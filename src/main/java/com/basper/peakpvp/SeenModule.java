package com.basper.peakpvp;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

final class SeenModule implements TabExecutor {
    private final PeakPvPPlugin plugin;

    SeenModule(PeakPvPPlugin plugin) {
        this.plugin = plugin;
    }

    void enable() {
        plugin.command("seen", this);
    }

    @Override public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                                       @NotNull String label, String @NotNull [] args) {
        if (args.length != 1 || args[0].isBlank()) {
            plugin.message(sender, "Usage: &c/seen <player>&f.");
            return true;
        }

        Player online = Bukkit.getPlayerExact(args[0]);
        if (online != null) {
            plugin.message(sender, "&c" + online.getName() + "&f is currently online.");
            return true;
        }

        OfflinePlayer target = Bukkit.getOfflinePlayerIfCached(args[0]);
        if (target == null || target.getLastPlayed() <= 0) {
            plugin.message(sender, "That player has never joined the server.");
            return true;
        }

        plugin.message(sender, "&c" + target.getName() + "&f was last seen " + elapsed(target.getLastPlayed()) + " ago.");
        return true;
    }

    private String elapsed(long lastPlayed) {
        Duration duration = Duration.between(Instant.ofEpochMilli(lastPlayed), Instant.now());
        long days = duration.toDays();
        long hours = duration.toHoursPart();
        long minutes = duration.toMinutesPart();
        if (days > 0) return days + (days == 1 ? " day" : " days");
        if (hours > 0) return hours + (hours == 1 ? " hour" : " hours");
        return Math.max(1, minutes) + (minutes == 1 ? " minute" : " minutes");
    }

    @Override public @NotNull List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                          @NotNull String alias, String @NotNull [] args) {
        if (args.length != 1) return List.of();
        String input = args[0].toLowerCase();
        return Bukkit.getOnlinePlayers().stream().map(Player::getName)
                .filter(name -> name.toLowerCase().startsWith(input)).toList();
    }
}
