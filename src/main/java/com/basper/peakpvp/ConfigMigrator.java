package com.basper.peakpvp;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/** Adds bundled defaults without overwriting server-owned configuration or data. */
final class ConfigMigrator {
    private static final DateTimeFormatter BACKUP_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private ConfigMigrator() { }

    static void upgrade(PeakPvPPlugin plugin) {
        File config = new File(plugin.getDataFolder(), "config.yml");
        int oldVersion = config.isFile() ? YamlConfiguration.loadConfiguration(config).getInt("config-version", 0) : 0;
        boolean legacySpawn = config.isFile() && oldVersion < 2;
        upgradeFile(plugin, "config.yml");
        if (legacySpawn) raiseSpawnHeight(plugin, config);
        if (config.isFile() && oldVersion < 3) updateScoreboardStats(plugin, config);
    }

    private static void updateScoreboardStats(PeakPvPPlugin plugin, File config) {
        try (InputStream bundled = plugin.getResource("config.yml")) {
            if (bundled == null) return;
            YamlConfiguration defaults = YamlConfiguration.loadConfiguration(new InputStreamReader(bundled, StandardCharsets.UTF_8));
            YamlConfiguration data = YamlConfiguration.loadConfiguration(config);
            data.set("scoreboard.lines", defaults.getStringList("scoreboard.lines"));
            Path original = config.toPath();
            Path backup = nextBackupPath(original);
            Files.copy(original, backup, StandardCopyOption.COPY_ATTRIBUTES);
            Path temporary = original.resolveSibling("config.yml.scoreboard.tmp");
            try {
                data.save(temporary.toFile());
                try {
                    Files.move(temporary, original, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                } catch (AtomicMoveNotSupportedException ignored) {
                    Files.move(temporary, original, StandardCopyOption.REPLACE_EXISTING);
                }
            } finally {
                Files.deleteIfExists(temporary);
            }
            plugin.getLogger().info("Scoreboard upgraded with separate kills, deaths and ELO lines. Backup: " + backup.getFileName());
        } catch (IOException exception) {
            plugin.getLogger().severe("Could not upgrade scoreboard lines: " + exception.getMessage());
        }
    }

    private static void raiseSpawnHeight(PeakPvPPlugin plugin, File config) {
        try {
            YamlConfiguration data = YamlConfiguration.loadConfiguration(config);
            data.set("world.spawn-y", data.getInt("world.spawn-y", 64) + 2);
            Path original = config.toPath();
            Path backup = nextBackupPath(original);
            Files.copy(original, backup, StandardCopyOption.COPY_ATTRIBUTES);
            Path temporary = original.resolveSibling("config.yml.spawn.tmp");
            try {
                data.save(temporary.toFile());
                try {
                    Files.move(temporary, original, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                } catch (AtomicMoveNotSupportedException ignored) {
                    Files.move(temporary, original, StandardCopyOption.REPLACE_EXISTING);
                }
            } finally {
                Files.deleteIfExists(temporary);
            }
            plugin.getLogger().info("config.yml spawn raised by 2 blocks. Backup: " + backup.getFileName());
        } catch (IOException exception) {
            plugin.getLogger().severe("Could not raise the configured spawn; the existing file was left in place: " + exception.getMessage());
        }
    }

    static void upgradeFile(PeakPvPPlugin plugin, String fileName) {
        File target = new File(plugin.getDataFolder(), fileName);
        if (!target.isFile()) return;
        try (InputStream bundled = plugin.getResource(fileName)) {
            if (bundled == null) {
                plugin.getLogger().warning("Bundled " + fileName + " is missing; upgrade skipped.");
                return;
            }
            YamlConfiguration defaults = YamlConfiguration.loadConfiguration(new InputStreamReader(bundled, StandardCharsets.UTF_8));
            YamlConfiguration installed = YamlConfiguration.loadConfiguration(target);
            int installedVersion = installed.getInt("config-version", 0);
            int added = addMissingDefaults(installed, defaults);
            int bundledVersion = defaults.getInt("config-version", 1);
            boolean versionChanged = installedVersion < bundledVersion;
            if (versionChanged) installed.set("config-version", bundledVersion);
            if (added == 0 && !versionChanged) return;

            Path original = target.toPath();
            Path backup = nextBackupPath(original);
            Files.copy(original, backup, StandardCopyOption.COPY_ATTRIBUTES);
            Path temporary = original.resolveSibling("config.yml.upgrade.tmp");
            try {
                installed.save(temporary.toFile());
                try {
                    Files.move(temporary, original, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                } catch (AtomicMoveNotSupportedException ignored) {
                    Files.move(temporary, original, StandardCopyOption.REPLACE_EXISTING);
                }
            } finally {
                Files.deleteIfExists(temporary);
            }
            plugin.getLogger().info(fileName + " upgraded safely: added " + added + " missing setting(s). Backup: " + backup.getFileName());
        } catch (IOException exception) {
            plugin.getLogger().severe("Could not safely upgrade " + fileName + "; the existing file was left in place: " + exception.getMessage());
        }
    }

    static void resetFileIfOlder(PeakPvPPlugin plugin, String fileName) {
        File target = new File(plugin.getDataFolder(), fileName);
        if (!target.isFile()) {
            plugin.saveResource(fileName, false);
            return;
        }
        try (InputStream bundled = plugin.getResource(fileName)) {
            if (bundled == null) {
                plugin.getLogger().warning("Bundled " + fileName + " is missing; reset skipped.");
                return;
            }
            YamlConfiguration defaults = YamlConfiguration.loadConfiguration(new InputStreamReader(bundled, StandardCharsets.UTF_8));
            YamlConfiguration installed = YamlConfiguration.loadConfiguration(target);
            if (installed.getInt("config-version", 0) >= defaults.getInt("config-version", 1)) return;

            Path original = target.toPath();
            Path backup = nextBackupPath(original);
            Files.copy(original, backup, StandardCopyOption.COPY_ATTRIBUTES);
            Path temporary = original.resolveSibling(fileName + ".reset.tmp");
            try (InputStream replacement = plugin.getResource(fileName)) {
                Files.copy(replacement, temporary, StandardCopyOption.REPLACE_EXISTING);
                try {
                    Files.move(temporary, original, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                } catch (AtomicMoveNotSupportedException ignored) {
                    Files.move(temporary, original, StandardCopyOption.REPLACE_EXISTING);
                }
            } finally {
                Files.deleteIfExists(temporary);
            }
            plugin.getLogger().info(fileName + " reset to the bundled kit list. Backup: " + backup.getFileName());
        } catch (IOException exception) {
            plugin.getLogger().severe("Could not reset " + fileName + "; the existing file was left in place: " + exception.getMessage());
        }
    }

    private static int addMissingDefaults(YamlConfiguration installed, YamlConfiguration defaults) {
        int added = 0;
        for (String path : defaults.getKeys(true)) {
            if (defaults.isConfigurationSection(path) || installed.contains(path)) continue;
            installed.set(path, defaults.get(path));
            added++;
        }
        return added;
    }

    private static Path nextBackupPath(Path original) {
        String base = original.getFileName() + ".backup-" + BACKUP_TIME.format(LocalDateTime.now());
        Path candidate = original.resolveSibling(base);
        int suffix = 1;
        while (Files.exists(candidate)) candidate = original.resolveSibling(base + "-" + suffix++);
        return candidate;
    }
}
