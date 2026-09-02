package com.basper.peakpvp;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;
import java.util.ArrayList;
import java.util.Map;

final class LobbyItemsModule implements Listener {
    private static final List<String> ITEM_IDS = List.of("unranked", "ranked", "party", "kit-editor", "settings");
    private static final Map<String, String> DISPLAY_NAMES = Map.of(
            "unranked", "Ranked 1.8",
            "ranked", "Ranked Latest",
            "party", "Create Party",
            "kit-editor", "Kit Editor",
            "settings", "Settings"
    );

    private final PeakPvPPlugin plugin;
    private final NamespacedKey itemKey;
    private final KitModule kits;
    private final PartyModule parties;

    LobbyItemsModule(PeakPvPPlugin plugin, KitModule kits, PartyModule parties) {
        this.plugin = plugin;
        this.kits = kits;
        this.parties = parties;
        this.itemKey = new NamespacedKey(plugin, "lobby_item");
    }

    void enable() {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        for (Player player : Bukkit.getOnlinePlayers()) giveItems(player);
    }

    @EventHandler public void join(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        kits.cancelQueue(player.getUniqueId());
        player.getInventory().clear();
        Bukkit.getScheduler().runTask(plugin, () -> player.teleportAsync(plugin.spawn()).thenAccept(success -> {
            if (!success) return;
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (player.isOnline()) giveItems(player);
            });
        }));
    }

    @EventHandler public void respawn(PlayerRespawnEvent event) {
        event.setRespawnLocation(plugin.spawn());
        Bukkit.getScheduler().runTask(plugin, () -> {
            plugin.resetPlayerVitals(event.getPlayer());
            giveItems(event.getPlayer());
        });
    }

    @EventHandler public void move(PlayerMoveEvent event) {
        if (event.getTo() == null || event.getFrom().getWorld() == null || event.getTo().getWorld() == null) return;
        boolean wasInLobby = insideSpawn(event.getFrom());
        boolean isInLobby = insideSpawn(event.getTo());
        boolean wasInPvpZone = isPvpZone(event.getFrom());
        boolean isInPvpZone = isPvpZone(event.getTo());
        if ((wasInLobby == isInLobby && wasInPvpZone == isInPvpZone) || kits.isInDuel(event.getPlayer().getUniqueId())) return;
        if (isInLobby) giveItems(event.getPlayer());
        else if (isInPvpZone) kits.enterPvpZone(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void teleport(PlayerTeleportEvent event) {
        if (event.getTo() == null || !insideSpawn(event.getTo()) || insideSpawn(event.getFrom())
                || kits.isInDuel(event.getPlayer().getUniqueId())) return;
        Player player = event.getPlayer();
        kits.cancelQueue(player.getUniqueId());
        player.getInventory().clear();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline() && insideSpawn(player.getLocation()) && !kits.isInDuel(player.getUniqueId())) giveItems(player);
        });
    }

    @EventHandler public void death(PlayerDeathEvent event) {
        event.getDrops().removeIf(this::isLobbyItem);
    }

    @EventHandler(priority = EventPriority.HIGHEST) public void interact(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        String id = itemId(event.getItem());
        if (id == null) return;
        event.setCancelled(true);
        if (id.equals("unranked")) {
            kits.openUnrankedMenu(event.getPlayer());
            return;
        }
        if (id.equals("ranked")) {
            kits.openRankedMenu(event.getPlayer());
            return;
        }
        if (id.equals("kit-editor")) {
            kits.openKitEditor(event.getPlayer());
            return;
        }
        if (id.equals("party")) {
            parties.handleLobby(event.getPlayer());
            return;
        }
        plugin.message(event.getPlayer(), "Opening &c" + DISPLAY_NAMES.getOrDefault(id, id) + "&f will be added next.");
    }

    @EventHandler(priority = EventPriority.HIGHEST) public void click(InventoryClickEvent event) {
        if (isLobbyItem(event.getCurrentItem()) || isLobbyItem(event.getCursor())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST) public void drag(InventoryDragEvent event) {
        if (isLobbyItem(event.getOldCursor())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST) public void drop(PlayerDropItemEvent event) {
        if (isLobbyItem(event.getItemDrop().getItemStack())) event.setCancelled(true);
    }

    void refresh(Player player) { giveItems(player); }

    private void giveItems(Player player) {
        if (!player.getWorld().equals(plugin.pvpWorld())) return;
        if (!insideSpawn(player.getLocation()) && isPvpZone(player.getLocation()) && !kits.isInDuel(player.getUniqueId())) {
            kits.enterPvpZone(player);
            return;
        }
        if (!insideSpawn(player.getLocation())) return;
        kits.cancelQueue(player.getUniqueId());
        plugin.resetPlayerVitals(player);
        if (!plugin.getConfig().getBoolean("lobby-items.enabled", true)) return;
        player.getInventory().clear();
        for (String id : ITEM_IDS) {
            String path = "lobby-items." + id + ".";
            Material material = Material.matchMaterial(plugin.getConfig().getString(path + "material", defaultMaterial(id).name()));
            if (material == null || material.isAir()) material = defaultMaterial(id);
            ItemStack item = new ItemStack(material);
            ItemMeta meta = item.getItemMeta();
            meta.displayName(Messages.legacy(plugin.getConfig().getString(path + "name", DISPLAY_NAMES.get(id))));
            List<String> lore = new ArrayList<>(plugin.getConfig().getStringList(path + "lore"));
            if (id.equals("unranked")) {
                addPvPSystemLore(lore, "&e1.8 PvP");
                addRatingLore(lore, "&e1.8 ELO");
            }
            if (id.equals("ranked")) {
                addPvPSystemLore(lore, "&bLatest PvP");
                addRatingLore(lore, "&bLatest ELO");
            }
            meta.lore(lore.stream().map(Messages::legacy).toList());
            meta.getPersistentDataContainer().set(itemKey, PersistentDataType.STRING, id);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
            item.setItemMeta(meta);
            int slot = Math.max(0, Math.min(8, plugin.getConfig().getInt(path + "slot", 0)));
            player.getInventory().setItem(slot, item);
        }
        player.getInventory().setHeldItemSlot(0);
    }

    private boolean isPvpZone(org.bukkit.Location location) {
        return location.getWorld() != null && location.getWorld().equals(plugin.pvpWorld())
                && location.getY() < plugin.protectionY();
    }

    private boolean insideSpawn(org.bukkit.Location location) {
        if (location.getWorld() == null || !location.getWorld().equals(plugin.pvpWorld())
                || location.getY() < plugin.protectionY()) return false;
        double radius = plugin.protectionRadius();
        return location.getX() * location.getX() + location.getZ() * location.getZ() <= radius * radius;
    }

    private Material defaultMaterial(String id) {
        return switch (id) {
            case "unranked" -> Material.IRON_SWORD;
            case "ranked" -> Material.DIAMOND_SWORD;
            case "party" -> Material.ENDER_EYE;
            case "kit-editor" -> Material.BOOK;
            default -> Material.CLOCK;
        };
    }

    private void addPvPSystemLore(List<String> lore, String system) {
        if (lore.stream().anyMatch(line -> line.toLowerCase(java.util.Locale.ROOT).contains("combat:")
                || line.toLowerCase(java.util.Locale.ROOT).contains("pvp system:"))) return;
        int blank = lore.indexOf("");
        lore.add(blank < 0 ? lore.size() : blank, "&fPvP System: " + system);
    }

    private void addRatingLore(List<String> lore, String rating) {
        if (lore.stream().anyMatch(line -> line.toLowerCase(java.util.Locale.ROOT).contains("rating:"))) return;
        int blank = lore.indexOf("");
        lore.add(blank < 0 ? lore.size() : blank, "&fRating: " + rating);
    }

    private boolean isLobbyItem(ItemStack item) { return itemId(item) != null; }

    private String itemId(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer().get(itemKey, PersistentDataType.STRING);
    }
}
