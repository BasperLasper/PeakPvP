package com.basper.peakpvp;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

final class DuelRequestModule implements Listener, TabExecutor {
    private static final long REQUEST_TICKS = 20L * 30L;
    private final PeakPvPPlugin plugin;
    private final KitModule kits;
    private final PartyModule parties;
    private final Map<UUID, DuelRequest> requests = new HashMap<>();

    DuelRequestModule(PeakPvPPlugin plugin, KitModule kits, PartyModule parties) {
        this.plugin = plugin;
        this.kits = kits;
        this.parties = parties;
    }

    void enable() {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        plugin.command("duel", this);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void rightClickPlayer(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof Player target)) return;
        Player player = event.getPlayer();
        if (!plugin.insideSpawn(player.getLocation()) || !plugin.insideSpawn(target.getLocation())) return;
        event.setCancelled(true);

        if (accept(player, target)) return;
        if (parties.hasOpenParty(player.getUniqueId())) {
            parties.inviteFromSpawn(player, target);
            return;
        }
        if (parties.isInParty(player.getUniqueId())) {
            plugin.message(player, "Leave your party before sending a duel request.");
            return;
        }
        request(player, target);
    }

    @EventHandler public void quit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        requests.remove(playerId);
        requests.entrySet().removeIf(entry -> entry.getValue().requester().equals(playerId));
    }

    @Override public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String @NotNull [] args) {
        if (!(sender instanceof Player player)) { plugin.message(sender, "Players only."); return true; }
        if (args.length == 1 && args[0].equalsIgnoreCase("accept")) {
            accept(player);
            return true;
        }
        if (args.length != 1) {
            plugin.message(player, "Usage: &c/duel <player>&f or &c/duel accept&f.");
            return true;
        }
        if (!plugin.insideSpawn(player.getLocation())) {
            plugin.message(player, "Duel requests can only be used in spawn.");
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) { plugin.message(player, "That player is not online."); return true; }
        if (target.equals(player)) { plugin.message(player, "You cannot duel yourself."); return true; }
        if (!plugin.insideSpawn(target.getLocation())) {
            plugin.message(player, "That player must be in spawn.");
            return true;
        }
        request(player, target);
        return true;
    }

    private void request(Player requester, Player target) {
        if (kits.isInDuel(requester.getUniqueId()) || kits.isInDuel(target.getUniqueId())) {
            plugin.message(requester, "One of you is already in a duel.");
            return;
        }
        if (requester.equals(target)) {
            plugin.message(requester, "You cannot duel yourself.");
            return;
        }
        DuelRequest request = new DuelRequest(requester.getUniqueId(), System.currentTimeMillis() + 30_000L);
        requests.put(target.getUniqueId(), request);
        plugin.message(requester, "Duel request sent to &c" + target.getName() + "&f.");
        plugin.message(target, "&c" + requester.getName() + " &fchallenged you to a duel.");
        Component accept = Component.text("[ACCEPT]", NamedTextColor.GREEN)
                .clickEvent(ClickEvent.runCommand("/duel accept"));
        target.sendMessage(Component.text("Click ").color(NamedTextColor.GRAY).append(accept)
                .append(Component.text(" or right-click " + requester.getName() + " to accept.").color(NamedTextColor.GRAY)));
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            DuelRequest current = requests.get(target.getUniqueId());
            if (request.equals(current)) {
                requests.remove(target.getUniqueId());
                if (target.isOnline()) plugin.message(target, "The duel request from " + requester.getName() + " expired.");
            }
        }, REQUEST_TICKS);
    }

    private boolean accept(Player player, Player requester) {
        DuelRequest request = requests.get(player.getUniqueId());
        if (request == null || !request.requester().equals(requester.getUniqueId())) return false;
        accept(player);
        return true;
    }

    private void accept(Player player) {
        if (!plugin.insideSpawn(player.getLocation())) {
            plugin.message(player, "Duel requests can only be accepted in spawn.");
            return;
        }
        DuelRequest request = requests.remove(player.getUniqueId());
        if (request == null || request.expiresAt() < System.currentTimeMillis()) {
            plugin.message(player, "You do not have a valid duel request.");
            return;
        }
        Player requester = Bukkit.getPlayer(request.requester());
        if (requester == null || !plugin.insideSpawn(requester.getLocation())) {
            plugin.message(player, "The challenger is no longer available in spawn.");
            return;
        }
        if (kits.isInDuel(requester.getUniqueId()) || kits.isInDuel(player.getUniqueId())) {
            plugin.message(player, "One of you is already in a duel.");
            return;
        }
        kits.queueDirectDuel(requester, player);
    }

    @Override public @NotNull List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, String @NotNull [] args) {
        if (args.length != 1) return List.of();
        List<String> values = new ArrayList<>(List.of("accept"));
        values.addAll(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList());
        String input = args[0].toLowerCase(Locale.ROOT);
        return values.stream().filter(value -> value.toLowerCase(Locale.ROOT).startsWith(input)).toList();
    }

    private record DuelRequest(UUID requester, long expiresAt) { }
}
