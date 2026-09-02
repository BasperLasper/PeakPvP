package com.basper.peakpvp;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

final class StatsModule implements Listener {
    private final PeakPvPPlugin plugin;
    private final File file;
    private final YamlConfiguration data;
    private final Map<UUID, Integer> kills = new HashMap<>();
    private final Map<UUID, Integer> deaths = new HashMap<>();
    private final Map<UUID, Integer> legacyElo = new HashMap<>();
    private final Map<UUID, Integer> modernElo = new HashMap<>();

    StatsModule(PeakPvPPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "stats.yml");
        this.data = YamlConfiguration.loadConfiguration(file);
    }

    void enable() {
        load();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    int kills(Player player) { return kills.getOrDefault(player.getUniqueId(), 0); }

    int deaths(Player player) { return deaths.getOrDefault(player.getUniqueId(), 0); }

    int elo(Player player) { return elo(player, true); }

    int elo(Player player, boolean modern) { return elo(player.getUniqueId(), modern); }

    int elo(UUID playerId, boolean modern) { return (modern ? modernElo : legacyElo).getOrDefault(playerId, 1000); }

    int recordRankedResult(UUID winner, UUID loser, boolean modern) {
        Map<UUID, Integer> ladder = modern ? modernElo : legacyElo;
        int winnerBefore = elo(winner, modern);
        int loserBefore = elo(loser, modern);
        double expectedWinner = 1.0 / (1.0 + Math.pow(10.0, (loserBefore - winnerBefore) / 400.0));
        int change = Math.max(1, (int) Math.round(32.0 * (1.0 - expectedWinner)));
        ladder.put(winner, winnerBefore + change);
        ladder.put(loser, Math.max(0, loserBefore - change));
        save();
        return change;
    }

    String kdr(Player player) {
        int deaths = deaths(player);
        double ratio = deaths == 0 ? kills(player) : (double) kills(player) / deaths;
        return String.format(Locale.ROOT, "%.1f", ratio);
    }

    @EventHandler public void death(PlayerDeathEvent event) {
        Player loser = event.getEntity();
        UUID loserId = loser.getUniqueId();
        deaths.put(loserId, deaths(loser) + 1);
        Player killer = loser.getKiller();
        if (killer != null && !killer.equals(loser)) {
            UUID killerId = killer.getUniqueId();
            kills.put(killerId, kills(killer) + 1);
        }
        save();
    }

    private void load() {
        var players = data.getConfigurationSection("players");
        if (players == null) return;
        for (String key : players.getKeys(false)) {
            try {
                UUID id = UUID.fromString(key);
                int playerKills = Math.max(0, data.getInt("players." + key + ".kills", 0));
                int playerDeaths = Math.max(0, data.getInt("players." + key + ".deaths", 0));
                int latestElo = Math.max(0, data.getInt("players." + key + ".latest-elo",
                        data.getInt("players." + key + ".elo", 1000)));
                int legacyRating = Math.max(0, data.getInt("players." + key + ".legacy-elo", 1000));
                if (playerKills > 0) kills.put(id, playerKills);
                if (playerDeaths > 0) deaths.put(id, playerDeaths);
                modernElo.put(id, latestElo);
                legacyElo.put(id, legacyRating);
            } catch (IllegalArgumentException ignored) { }
        }
    }

    private void save() {
        for (UUID id : kills.keySet()) data.set("players." + id + ".kills", kills.get(id));
        for (UUID id : deaths.keySet()) data.set("players." + id + ".deaths", deaths.get(id));
        for (UUID id : modernElo.keySet()) data.set("players." + id + ".latest-elo", modernElo.get(id));
        for (UUID id : legacyElo.keySet()) data.set("players." + id + ".legacy-elo", legacyElo.get(id));
        try {
            file.getParentFile().mkdirs();
            data.save(file);
        } catch (IOException exception) {
            plugin.getLogger().warning("Could not save stats.yml: " + exception.getMessage());
        }
    }
}
