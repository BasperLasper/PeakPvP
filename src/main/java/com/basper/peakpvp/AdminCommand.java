package com.basper.peakpvp;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

final class AdminCommand implements TabExecutor, Listener {
    private static final String PERMISSION = "peakpvp.admin.arena";
    private final PeakPvPPlugin plugin;
    private final ArenaManager arenas;
    private final NamespacedKey wandKey;
    private final Map<UUID, Selection> selections = new HashMap<>();

    AdminCommand(PeakPvPPlugin plugin, ArenaManager arenas) {
        this.plugin = plugin;
        this.arenas = arenas;
        this.wandKey = new NamespacedKey(plugin, "arena_wand");
    }

    @Override public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String @NotNull [] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("help") || args[0].equalsIgnoreCase("setup")) {
            help(sender);
            return true;
        }
        if (args[0].equalsIgnoreCase("reload") || (label.equalsIgnoreCase("ppreload") && args.length == 0)) return reload(sender);
        if (!args[0].equalsIgnoreCase("arena") && !args[0].equalsIgnoreCase("area")) {
            plugin.message(sender, "Unknown option. Use &c/peakpvp help&f.");
            return true;
        }
        if (!sender.hasPermission(PERMISSION)) {
            plugin.message(sender, "You do not have permission to manage arenas.");
            return true;
        }
        Player player = sender instanceof Player p ? p : null;
        if (player == null && args.length > 1 && !List.of("list", "info", "delete").contains(args[1].toLowerCase(Locale.ROOT))) {
            plugin.message(sender, "That arena setup step must be used in game.");
            return true;
        }
        if (args.length == 1) { arenaHelp(sender); return true; }
        return switch (args[1].toLowerCase(Locale.ROOT)) {
            case "wand" -> wand(player);
            case "pos1" -> position(player, true);
            case "pos2" -> position(player, false);
            case "clear" -> clearSelection(player);
            case "create" -> create(player, args);
            case "setspawn" -> setSpawn(player, args);
            case "list" -> list(sender);
            case "info" -> info(sender, args);
            case "delete" -> delete(sender, args);
            default -> { arenaHelp(sender); yield true; }
        };
    }

    private boolean reload(CommandSender sender) {
        if (!sender.hasPermission("peakpvp.admin.reload")) { plugin.message(sender, "You do not have permission to reload PeakPvP."); return true; }
        ConfigMigrator.upgrade(plugin);
        plugin.reloadConfig();
        arenas.load();
        plugin.message(sender, "Configuration and arenas reloaded successfully.");
        return true;
    }

    private boolean wand(Player player) {
        ItemStack wand = new ItemStack(Material.BLAZE_ROD);
        ItemMeta meta = wand.getItemMeta();
        meta.displayName(Messages.legacy("&c&lArena Selection Wand"));
        meta.lore(List.of(Messages.legacy("&7Left-click: select corner 1"), Messages.legacy("&7Right-click: select corner 2")));
        meta.getPersistentDataContainer().set(wandKey, PersistentDataType.BYTE, (byte) 1);
        wand.setItemMeta(meta);
        player.getInventory().addItem(wand);
        plugin.message(player, "Arena wand given. &cLeft-click &fcorner 1 and &cright-click &fcorner 2.");
        return true;
    }

    private boolean position(Player player, boolean first) {
        Selection selection = selections.computeIfAbsent(player.getUniqueId(), ignored -> new Selection());
        if (first) selection.first = player.getLocation().getBlock().getLocation();
        else selection.second = player.getLocation().getBlock().getLocation();
        showPosition(player, first, first ? selection.first : selection.second);
        return true;
    }

    private boolean create(Player player, String[] args) {
        if (args.length != 3) { plugin.message(player, "Usage: &c/peakpvp arena create <name>&f after selecting both corners."); return true; }
        if (!args[2].matches("[A-Za-z0-9_-]{1,32}")) { plugin.message(player, "Arena names may only use letters, numbers, underscores and hyphens."); return true; }
        Selection selection = selections.get(player.getUniqueId());
        if (selection == null || selection.first == null || selection.second == null) { plugin.message(player, "Select both corners first with &c/peakpvp arena wand&f."); return true; }
        if (!selection.first.getWorld().equals(selection.second.getWorld())) { plugin.message(player, "Both corners must be in the same world."); return true; }
        if (!arenas.create(args[2], selection.first, selection.second)) { plugin.message(player, "An arena named &c" + args[2] + " &falready exists."); return true; }
        plugin.message(player, "Created arena &c" + args[2] + "&f. Now stand at each player start and use:");
        player.sendMessage(Messages.legacy("&8 • &c/peakpvp arena setspawn " + args[2] + " 1"));
        player.sendMessage(Messages.legacy("&8 • &c/peakpvp arena setspawn " + args[2] + " 2"));
        return true;
    }

    private boolean setSpawn(Player player, String[] args) {
        if (args.length != 4 || (!args[3].equals("1") && !args[3].equals("2"))) { plugin.message(player, "Usage: &c/peakpvp arena setspawn <name> <1|2>&f."); return true; }
        int number = Integer.parseInt(args[3]);
        if (!arenas.setSpawn(args[2], number, player.getLocation())) { plugin.message(player, "Arena &c" + args[2] + " &fdoes not exist."); return true; }
        ArenaManager.Arena arena = arenas.get(args[2]);
        plugin.message(player, "Set spawn &c" + number + " &ffor &c" + arena.name() + "&f. Arena status: " + (arena.ready() ? "&aREADY" : "&eNEEDS OTHER SPAWN"));
        return true;
    }

    private boolean list(CommandSender sender) {
        if (arenas.all().isEmpty()) { plugin.message(sender, "No arenas exist yet. Use &c/peakpvp setup&f."); return true; }
        plugin.message(sender, "Arenas (&c" + arenas.all().size() + "&f):");
        for (ArenaManager.Arena arena : arenas.all()) sender.sendMessage(Messages.legacy("&8 • &c" + arena.name() + " &8- " + (arena.ready() ? "&aREADY" : "&eSETUP INCOMPLETE")));
        return true;
    }

    private boolean info(CommandSender sender, String[] args) {
        if (args.length != 3) { plugin.message(sender, "Usage: &c/peakpvp arena info <name>&f."); return true; }
        ArenaManager.Arena arena = arenas.get(args[2]);
        if (arena == null) { plugin.message(sender, "That arena does not exist."); return true; }
        plugin.message(sender, "Arena &c" + arena.name() + " &8- " + (arena.ready() ? "&aREADY" : "&eINCOMPLETE"));
        sender.sendMessage(Messages.legacy("&8 • &fWorld: &c" + arena.corner1().getWorld().getName()));
        sender.sendMessage(Messages.legacy("&8 • &fCorner 1: &c" + coords(arena.corner1())));
        sender.sendMessage(Messages.legacy("&8 • &fCorner 2: &c" + coords(arena.corner2())));
        sender.sendMessage(Messages.legacy("&8 • &fSpawn 1: " + (arena.spawn1() == null ? "&cNOT SET" : "&a" + coords(arena.spawn1()))));
        sender.sendMessage(Messages.legacy("&8 • &fSpawn 2: " + (arena.spawn2() == null ? "&cNOT SET" : "&a" + coords(arena.spawn2()))));
        return true;
    }

    private boolean delete(CommandSender sender, String[] args) {
        if (args.length != 3) { plugin.message(sender, "Usage: &c/peakpvp arena delete <name>&f."); return true; }
        plugin.message(sender, arenas.delete(args[2]) ? "Deleted arena &c" + args[2] + "&f." : "That arena does not exist.");
        return true;
    }

    private boolean clearSelection(Player player) {
        selections.remove(player.getUniqueId());
        plugin.message(player, "Your temporary arena selection was cleared.");
        return true;
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.HIGHEST) public void useWand(PlayerInteractEvent event) {
        if (!event.getPlayer().hasPermission(PERMISSION) || !isWand(event.getItem())) return;
        boolean first;
        if (event.getAction() == Action.LEFT_CLICK_BLOCK) first = true;
        else if (event.getAction() == Action.RIGHT_CLICK_BLOCK) first = false;
        else return;
        event.setCancelled(true);
        Selection selection = selections.computeIfAbsent(event.getPlayer().getUniqueId(), ignored -> new Selection());
        Location location = event.getClickedBlock().getLocation();
        if (first) selection.first = location; else selection.second = location;
        showPosition(event.getPlayer(), first, location);
    }

    private boolean isWand(ItemStack item) {
        return item != null && item.hasItemMeta() && item.getItemMeta().getPersistentDataContainer().has(wandKey, PersistentDataType.BYTE);
    }

    private void showPosition(Player player, boolean first, Location location) {
        plugin.message(player, "Corner &c" + (first ? "1" : "2") + " &fset to &c" + coords(location) + "&f.");
    }

    private String coords(Location location) { return location.getBlockX() + ", " + location.getBlockY() + ", " + location.getBlockZ(); }

    private void help(CommandSender sender) {
        sender.sendMessage(Messages.legacy("&8&m--------------------------------"));
        sender.sendMessage(Messages.legacy("&c&lPeakPvP Admin Setup"));
        sender.sendMessage(Messages.legacy("&fCreate an arena in four simple steps:"));
        sender.sendMessage(Messages.legacy("&81. &c/peakpvp arena wand &7- get the selection tool"));
        sender.sendMessage(Messages.legacy("&82. &fLeft/right-click opposite arena corners"));
        sender.sendMessage(Messages.legacy("&83. &c/peakpvp arena create <name>"));
        sender.sendMessage(Messages.legacy("&84. &fStand at each start and use &csetspawn <name> 1/2"));
        sender.sendMessage(Messages.legacy("&fMore: &c/peakpvp arena &8(or &carea&8) | &c/peakpvp reload"));
        sender.sendMessage(Messages.legacy("&8&m--------------------------------"));
    }

    private void arenaHelp(CommandSender sender) {
        sender.sendMessage(Messages.legacy("&c&lArena Commands"));
        sender.sendMessage(Messages.legacy("&c/peakpvp arena wand &7- selection wand"));
        sender.sendMessage(Messages.legacy("&c/peakpvp arena pos1|pos2 &7- select without the wand"));
        sender.sendMessage(Messages.legacy("&c/peakpvp arena clear &7- clear your current selection"));
        sender.sendMessage(Messages.legacy("&c/peakpvp arena create <name> &7- save selected area"));
        sender.sendMessage(Messages.legacy("&c/peakpvp arena setspawn <name> <1|2> &7- save a start"));
        sender.sendMessage(Messages.legacy("&c/peakpvp arena list &7- show readiness"));
        sender.sendMessage(Messages.legacy("&c/peakpvp arena info <name> &7- show all setup details"));
        sender.sendMessage(Messages.legacy("&c/peakpvp arena delete <name> &7- permanently remove it"));
    }

    @Override public @NotNull List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, String @NotNull [] args) {
        if (args.length == 1) return filter(List.of("help", "setup", "arena", "area", "reload"), args[0]);
        if (!args[0].equalsIgnoreCase("arena") && !args[0].equalsIgnoreCase("area")) return List.of();
        if (args.length == 2) return filter(List.of("wand", "pos1", "pos2", "clear", "create", "setspawn", "list", "info", "delete"), args[1]);
        if (args.length == 3 && List.of("setspawn", "info", "delete").contains(args[1].toLowerCase(Locale.ROOT))) return filter(arenas.all().stream().map(ArenaManager.Arena::name).toList(), args[2]);
        if (args.length == 4 && args[1].equalsIgnoreCase("setspawn")) return filter(List.of("1", "2"), args[3]);
        return List.of();
    }

    private List<String> filter(List<String> values, String input) {
        String lower = input.toLowerCase(Locale.ROOT);
        return values.stream().filter(value -> value.toLowerCase(Locale.ROOT).startsWith(lower)).toList();
    }

    private static final class Selection { private Location first; private Location second; }
}
