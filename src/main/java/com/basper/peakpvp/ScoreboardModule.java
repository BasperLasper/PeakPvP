package com.basper.peakpvp;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

final class ScoreboardModule implements Listener {
    private final PeakPvPPlugin plugin;
    private final SocialModule social;
    private final StatsModule stats;
    private final Map<UUID, Scoreboard> boards = new HashMap<>();

    ScoreboardModule(PeakPvPPlugin plugin, SocialModule social, StatsModule stats) {
        this.plugin = plugin;
        this.social = social;
        this.stats = stats;
    }

    void enable() {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        long ticks = Math.max(10, plugin.getConfig().getLong("scoreboard.update-ticks", 20));
        Bukkit.getScheduler().runTaskTimer(plugin, this::refresh, 1, ticks);
    }

    private void refresh() {
        if (!plugin.getConfig().getBoolean("scoreboard.enabled", true)) {
            clearAll();
            return;
        }
        for (Player player : Bukkit.getOnlinePlayers()) update(player);
    }

    private void update(Player player) {
        Scoreboard board = boards.computeIfAbsent(player.getUniqueId(), ignored -> Bukkit.getScoreboardManager().getNewScoreboard());
        Objective old = board.getObjective("peakpvp");
        if (old != null) old.unregister();
        Component title = Messages.legacy(plugin.getConfig().getString("scoreboard.title", "&c&lPEAK PVP"));
        Objective objective = board.registerNewObjective("peakpvp", "dummy", title);
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);

        List<String> configured = plugin.getConfig().getStringList("scoreboard.lines");
        int score = configured.size();
        for (int index = 0; index < configured.size(); index++) {
            String line = replace(configured.get(index), player);
            // Every scoreboard entry must be unique, including intentionally blank lines.
            line += uniqueSuffix(index);
            objective.getScore(line).setScore(score--);
        }
        if (player.getScoreboard() != board) player.setScoreboard(board);
    }

    private String replace(String value, Player player) {
        double tpsValue = Math.min(20, Bukkit.getTPS()[0]);
        String tps = String.format(Locale.ROOT, "%.1f", tpsValue);
        boolean protectedArea = insideProtectedSpawn(player.getLocation());
        return colour(value
                .replace("<player>", player.getName())
                .replace("<online>", Integer.toString(Bukkit.getOnlinePlayers().size()))
                .replace("<max-online>", Integer.toString(Bukkit.getMaxPlayers()))
                .replace("<network-online>", Integer.toString(social.networkPlayers()))
                .replace("<kills>", Integer.toString(stats.kills(player)))
                .replace("<deaths>", Integer.toString(stats.deaths(player)))
                .replace("<kdr>", stats.kdr(player))
                .replace("<ping>", Integer.toString(player.getPing()))
                .replace("<tps>", tps)
                .replace("<tps-color>", tpsValue >= 18 ? "&a" : tpsValue >= 15 ? "&e" : "&c")
                .replace("<area>", protectedArea ? "Safe Zone" : "PvP Zone")
                .replace("<area-color>", protectedArea ? "&a" : "&c"));
    }

    private boolean insideProtectedSpawn(Location location) {
        if (location.getWorld() == null || !location.getWorld().equals(plugin.pvpWorld())) return false;
        if (location.getY() < plugin.protectionY()) return false;
        double radius = plugin.protectionRadius();
        return location.getX() * location.getX() + location.getZ() * location.getZ() <= radius * radius;
    }

    private String colour(String value) { return ChatColor.translateAlternateColorCodes('&', value); }

    private String uniqueSuffix(int index) {
        ChatColor[] colours = ChatColor.values();
        return colours[index % colours.length].toString() + ChatColor.RESET;
    }

    private void clearAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            Scoreboard board = boards.remove(player.getUniqueId());
            if (board != null && player.getScoreboard() == board) player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
        }
    }

    @EventHandler public void quit(PlayerQuitEvent event) { boards.remove(event.getPlayer().getUniqueId()); }
}
