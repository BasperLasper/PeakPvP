package com.basper.peakpvp;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

final class PartyModule implements Listener, TabExecutor {
    private final PeakPvPPlugin plugin;
    private final KitModule kits;
    private final Map<UUID, Party> parties = new HashMap<>();
    private final Map<UUID, UUID> invites = new HashMap<>();

    PartyModule(PeakPvPPlugin plugin, KitModule kits) {
        this.plugin = plugin;
        this.kits = kits;
    }

    void enable() {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        plugin.command("party", this);
    }

    void handleLobby(Player player) {
        Party party = parties.get(player.getUniqueId());
        if (party == null) {
            create(player);
            return;
        }
        info(player, party);
    }

    List<UUID> membersForQueue(UUID playerId) {
        Party party = parties.get(playerId);
        if (party == null || party.member() == null) return List.of(playerId);
        return List.of(party.leader(), party.member());
    }

    boolean sameParty(UUID first, UUID second) {
        Party party = parties.get(first);
        return party != null && parties.get(second) == party;
    }

    boolean hasOpenParty(UUID playerId) {
        Party party = parties.get(playerId);
        return party != null && party.member() == null && party.leader().equals(playerId);
    }

    boolean isInParty(UUID playerId) { return parties.containsKey(playerId); }

    void inviteFromSpawn(Player player, Player target) { invite(player, target); }

    private boolean create(Player player) {
        if (parties.containsKey(player.getUniqueId())) {
            info(player, parties.get(player.getUniqueId()));
            return true;
        }
        Party party = new Party(player.getUniqueId(), null);
        parties.put(player.getUniqueId(), party);
        plugin.message(player, "Party created. Invite one player with &c/party invite <player>&f.");
        return true;
    }

    private boolean invite(Player player, String[] args) {
        Party party = parties.get(player.getUniqueId());
        if (party == null) { plugin.message(player, "Create a party first with &c/party create&f."); return true; }
        if (!party.leader().equals(player.getUniqueId())) { plugin.message(player, "Only the party leader can invite players."); return true; }
        if (party.member() != null) { plugin.message(player, "Your party is already full. Parties can only have 2 players."); return true; }
        if (args.length != 2) { plugin.message(player, "Usage: &c/party invite <player>&f."); return true; }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) { plugin.message(player, "That player is not online."); return true; }
        invite(player, target);
        return true;
    }

    private void invite(Player player, Player target) {
        Party party = parties.get(player.getUniqueId());
        if (party == null) { plugin.message(player, "Create a party first with &c/party create&f."); return; }
        if (!party.leader().equals(player.getUniqueId())) { plugin.message(player, "Only the party leader can invite players."); return; }
        if (party.member() != null) { plugin.message(player, "Your party is already full. Parties can only have 2 players."); return; }
        if (target.equals(player)) { plugin.message(player, "You cannot invite yourself."); return; }
        if (parties.containsKey(target.getUniqueId())) { plugin.message(player, "That player is already in a party."); return; }
        invites.put(target.getUniqueId(), player.getUniqueId());
        plugin.message(player, "Party invite sent to &c" + target.getName() + "&f.");
        plugin.message(target, "&c" + player.getName() + " &finvited you to a party. Use &c/party accept&f.");
    }

    private boolean accept(Player player) {
        UUID leaderId = invites.remove(player.getUniqueId());
        Party party = leaderId == null ? null : parties.get(leaderId);
        Player leader = leaderId == null ? null : Bukkit.getPlayer(leaderId);
        if (party == null || party.member() != null || leader == null) {
            plugin.message(player, "You do not have a valid party invite.");
            return true;
        }
        if (parties.containsKey(player.getUniqueId())) {
            plugin.message(player, "You are already in a party.");
            return true;
        }
        Party updated = new Party(party.leader(), player.getUniqueId());
        parties.put(updated.leader(), updated);
        parties.put(updated.member(), updated);
        plugin.message(leader, "&c" + player.getName() + " &fjoined your party.");
        plugin.message(player, "You joined &c" + leader.getName() + "&f's party.");
        return true;
    }

    private boolean leave(Player player) {
        Party party = parties.get(player.getUniqueId());
        if (party == null) { plugin.message(player, "You are not in a party."); return true; }
        disband(party, player.getUniqueId());
        return true;
    }

    private void disband(Party party, UUID leaving) {
        parties.remove(party.leader());
        if (party.member() != null) parties.remove(party.member());
        if (party.member() != null) {
            UUID other = party.leader().equals(leaving) ? party.member() : party.leader();
            Player player = Bukkit.getPlayer(other);
            if (player != null) plugin.message(player, "The party was disbanded.");
        }
        Player player = Bukkit.getPlayer(leaving);
        if (player != null) plugin.message(player, "You left the party.");
    }

    private void info(Player player, Party party) {
        String leader = name(party.leader());
        String member = party.member() == null ? "waiting for an invite" : name(party.member());
        plugin.message(player, "Party: leader &c" + leader + "&f, member &c" + member + "&f. Max size: 2.");
    }

    private String name(UUID playerId) {
        Player player = Bukkit.getPlayer(playerId);
        return player == null ? playerId.toString() : player.getName();
    }

    @EventHandler public void quit(PlayerQuitEvent event) {
        invites.remove(event.getPlayer().getUniqueId());
        Party party = parties.get(event.getPlayer().getUniqueId());
        if (party != null) disband(party, event.getPlayer().getUniqueId());
    }

    @Override public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String @NotNull [] args) {
        if (!(sender instanceof Player player)) { plugin.message(sender, "Players only."); return true; }
        if (args.length == 0 || args[0].equalsIgnoreCase("info")) {
            Party party = parties.get(player.getUniqueId());
            if (party == null) plugin.message(player, "You are not in a party. Use &c/party create&f.");
            else info(player, party);
            return true;
        }
        return switch (args[0].toLowerCase(Locale.ROOT)) {
            case "create" -> create(player);
            case "invite" -> invite(player, args);
            case "accept" -> accept(player);
            case "leave", "disband" -> leave(player);
            default -> help(player);
        };
    }

    private boolean help(Player player) {
        plugin.message(player, "Party commands: &c/party create&f, &c/party invite <player>&f, &c/party accept&f, &c/party leave&f.");
        return true;
    }

    @Override public @NotNull List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, String @NotNull [] args) {
        if (args.length == 1) return filter(List.of("create", "invite", "accept", "leave", "info"), args[0]);
        if (args.length == 2 && args[0].equalsIgnoreCase("invite")) {
            return filter(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList(), args[1]);
        }
        return List.of();
    }

    private List<String> filter(List<String> values, String input) {
        String lower = input.toLowerCase(Locale.ROOT);
        return values.stream().filter(value -> value.toLowerCase(Locale.ROOT).startsWith(lower)).toList();
    }

    private record Party(UUID leader, UUID member) { }
}
