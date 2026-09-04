package com.basper.peakpvp;

import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.LinkedHashSet;

final class ArenaManager {
    record Arena(String name, Location corner1, Location corner2, Location spawn1, Location spawn2,
                 Set<String> allowedKits) {
        boolean ready() { return spawn1 != null && spawn2 != null; }
        boolean allowsKit(String kit) { return allowedKits == null || allowedKits.contains(kit.toLowerCase(Locale.ROOT)); }
    }

    private final PeakPvPPlugin plugin;
    private final File file;
    private final Map<String, Arena> arenas = new LinkedHashMap<>();

    ArenaManager(PeakPvPPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "arenas.yml");
    }

    void load() {
        arenas.clear();
        YamlConfiguration data = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection section = data.getConfigurationSection("arenas");
        if (section == null) return;
        for (String key : section.getKeys(false)) {
            String path = "arenas." + key + ".";
            Location corner1 = data.getLocation(path + "corner-1");
            Location corner2 = data.getLocation(path + "corner-2");
            if (corner1 == null || corner2 == null) continue;
            arenas.put(key.toLowerCase(Locale.ROOT), new Arena(
                    data.getString(path + "name", key), corner1, corner2,
                    data.getLocation(path + "spawn-1"), data.getLocation(path + "spawn-2"),
                    data.contains(path + "allowed-kits")
                            ? new LinkedHashSet<>(data.getStringList(path + "allowed-kits").stream()
                            .map(value -> value.toLowerCase(Locale.ROOT)).toList()) : null));
        }
    }

    Arena get(String name) { return arenas.get(name.toLowerCase(Locale.ROOT)); }
    Collection<Arena> all() { return new ArrayList<>(arenas.values()); }

    Arena containing(Location location) {
        if (location == null || location.getWorld() == null) return null;
        for (Arena arena : arenas.values()) {
            if (arena.corner1().getWorld() == null || !arena.corner1().getWorld().equals(location.getWorld())) continue;
            double minX = Math.min(arena.corner1().getX(), arena.corner2().getX());
            double maxX = Math.max(arena.corner1().getX(), arena.corner2().getX()) + 1;
            double minY = Math.min(arena.corner1().getY(), arena.corner2().getY());
            double maxY = Math.max(arena.corner1().getY(), arena.corner2().getY()) + 1;
            double minZ = Math.min(arena.corner1().getZ(), arena.corner2().getZ());
            double maxZ = Math.max(arena.corner1().getZ(), arena.corner2().getZ()) + 1;
            if (location.getX() >= minX && location.getX() < maxX
                    && location.getY() >= minY && location.getY() < maxY
                    && location.getZ() >= minZ && location.getZ() < maxZ) return arena;
        }
        return null;
    }

    boolean create(String name, Location corner1, Location corner2) {
        String key = name.toLowerCase(Locale.ROOT);
        if (arenas.containsKey(key)) return false;
        arenas.put(key, new Arena(name, corner1.clone(), corner2.clone(), null, null, null));
        save();
        return true;
    }

    boolean setSpawn(String name, int number, Location location) {
        String key = name.toLowerCase(Locale.ROOT);
        Arena old = arenas.get(key);
        if (old == null) return false;
        Arena updated = number == 1
                ? new Arena(old.name, old.corner1, old.corner2, location.clone(), old.spawn2, old.allowedKits)
                : new Arena(old.name, old.corner1, old.corner2, old.spawn1, location.clone(), old.allowedKits);
        arenas.put(key, updated);
        save();
        return true;
    }

    void setAllowedKits(String name, Set<String> allowedKits) {
        String key = name.toLowerCase(Locale.ROOT);
        Arena old = arenas.get(key);
        if (old == null) return;
        Set<String> normalized = allowedKits == null ? null : new LinkedHashSet<>(allowedKits.stream()
                .map(value -> value.toLowerCase(Locale.ROOT)).toList());
        arenas.put(key, new Arena(old.name, old.corner1, old.corner2, old.spawn1, old.spawn2, normalized));
        save();
    }

    boolean delete(String name) {
        if (arenas.remove(name.toLowerCase(Locale.ROOT)) == null) return false;
        save();
        return true;
    }

    private void save() {
        YamlConfiguration data = new YamlConfiguration();
        for (Map.Entry<String, Arena> entry : arenas.entrySet()) {
            String path = "arenas." + entry.getKey() + ".";
            Arena arena = entry.getValue();
            data.set(path + "name", arena.name);
            data.set(path + "corner-1", arena.corner1);
            data.set(path + "corner-2", arena.corner2);
            data.set(path + "spawn-1", arena.spawn1);
            data.set(path + "spawn-2", arena.spawn2);
            if (arena.allowedKits != null) data.set(path + "allowed-kits", new ArrayList<>(arena.allowedKits));
        }
        try { data.save(file); }
        catch (IOException exception) { throw new IllegalStateException("Could not save arenas.yml", exception); }
    }
}
