package com.basper.peakpvp;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.List;

final class ColorModule implements Listener, TabExecutor {
    private static final Component CHAT_TITLE = Messages.legacy("&0PeakPvP - Chat Colour");
    private static final Component NAME_TITLE = Messages.legacy("&0PeakPvP - Name Colour");
    private static final Component COLOR_TITLE = Messages.legacy("&0PeakPvP - Colours");
    private static final int[] COLOR_SLOTS = {0, 1, 2, 3, 4, 5, 6, 7, 9, 10, 11, 12, 13, 14, 15, 16};
    private static final List<Colour> COLOURS = List.of(
            new Colour("&f", "White", Material.WHITE_CONCRETE),
            new Colour("&7", "Gray", Material.LIGHT_GRAY_CONCRETE),
            new Colour("&8", "Dark Gray", Material.GRAY_CONCRETE),
            new Colour("&0", "Black", Material.BLACK_CONCRETE),
            new Colour("&c", "Red", Material.RED_CONCRETE),
            new Colour("&4", "Dark Red", Material.RED_TERRACOTTA),
            new Colour("&6", "Gold", Material.ORANGE_CONCRETE),
            new Colour("&e", "Yellow", Material.YELLOW_CONCRETE),
            new Colour("&a", "Green", Material.LIME_CONCRETE),
            new Colour("&2", "Dark Green", Material.GREEN_CONCRETE),
            new Colour("&b", "Aqua", Material.LIGHT_BLUE_CONCRETE),
            new Colour("&3", "Dark Aqua", Material.CYAN_CONCRETE),
            new Colour("&9", "Blue", Material.BLUE_CONCRETE),
            new Colour("&1", "Dark Blue", Material.BLUE_TERRACOTTA),
            new Colour("&d", "Pink", Material.MAGENTA_CONCRETE),
            new Colour("&5", "Purple", Material.PURPLE_CONCRETE));

    private final PeakPvPPlugin plugin;
    private final File file;
    private final YamlConfiguration data;
    private final NamespacedKey colourKey;
    private SocialModule social;

    ColorModule(PeakPvPPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "preferences.yml");
        this.data = YamlConfiguration.loadConfiguration(file);
        this.colourKey = new NamespacedKey(plugin, "social_colour");
    }

    void enable() {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        plugin.command("chatcolor", this);
        plugin.command("namecolor", this);
        plugin.command("color", this);
    }

    void setSocialModule(SocialModule social) { this.social = social; }

    String chatCode(Player player) {
        if (!player.hasPermission("peakpvp.vip.chatcolor")) return "&f";
        return data.getString("players." + player.getUniqueId() + ".chat-color", "&f");
    }

    String nameCode(Player player) {
        if (!player.hasPermission("peakpvp.vip.namecolor")) return "&f";
        return data.getString("players." + player.getUniqueId() + ".name-color", "&f");
    }

    @Override public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String @NotNull [] args) {
        if (!(sender instanceof Player player)) { plugin.message(sender, "Players only."); return true; }
        switch (command.getName().toLowerCase()) {
            case "chatcolor" -> openColour(player, true);
            case "namecolor" -> openColour(player, false);
            default -> openColorHub(player);
        }
        return true;
    }

    private void openColorHub(Player player) {
        Inventory inventory = Bukkit.createInventory(null, 27, COLOR_TITLE);
        inventory.setItem(11, menuItem(Material.WRITABLE_BOOK, "&b&lChat Colour", "&7Change your default message colour."));
        inventory.setItem(15, menuItem(Material.NAME_TAG, "&d&lName Colour", "&7Change your name colour in chat and tab."));
        player.openInventory(inventory);
    }

    private ItemStack menuItem(Material material, String name, String lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Messages.legacy(name));
        meta.lore(List.of(Messages.legacy(lore), Messages.legacy("&eClick to open.")));
        item.setItemMeta(meta);
        return item;
    }

    private void openColour(Player player, boolean chat) {
        String permission = chat ? "peakpvp.vip.chatcolor" : "peakpvp.vip.namecolor";
        if (!player.hasPermission(permission)) {
            plugin.message(player, "This colour selector is a VIP feature.");
            return;
        }
        Inventory inventory = Bukkit.createInventory(null, 27, chat ? CHAT_TITLE : NAME_TITLE);
        for (int index = 0; index < COLOURS.size(); index++) {
            Colour colour = COLOURS.get(index);
            ItemStack item = new ItemStack(colour.icon());
            ItemMeta meta = item.getItemMeta();
            meta.displayName(Messages.legacy(colour.code() + "&l" + colour.name()));
            meta.lore(List.of(Messages.legacy("&7Click to use this " + (chat ? "chat" : "name") + " colour.")));
            meta.getPersistentDataContainer().set(colourKey, PersistentDataType.STRING, colour.code());
            item.setItemMeta(meta);
            inventory.setItem(COLOR_SLOTS[index], item);
        }
        player.openInventory(inventory);
    }

    @EventHandler public void click(InventoryClickEvent event) {
        Component title = event.getView().title();
        if (!title.equals(CHAT_TITLE) && !title.equals(NAME_TITLE) && !title.equals(COLOR_TITLE)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || event.getCurrentItem() == null) return;
        if (title.equals(COLOR_TITLE)) {
            if (event.getRawSlot() == 11) openColour(player, true);
            else if (event.getRawSlot() == 15) openColour(player, false);
            return;
        }
        ItemMeta meta = event.getCurrentItem().getItemMeta();
        if (meta == null) return;
        String colour = meta.getPersistentDataContainer().get(colourKey, PersistentDataType.STRING);
        if (colour == null) return;
        boolean chat = title.equals(CHAT_TITLE);
        String permission = chat ? "peakpvp.vip.chatcolor" : "peakpvp.vip.namecolor";
        if (!player.hasPermission(permission)) { player.closeInventory(); return; }
        data.set("players." + player.getUniqueId() + "." + (chat ? "chat-color" : "name-color"), colour);
        save();
        player.closeInventory();
        plugin.message(player, (chat ? "Chat" : "Name") + " colour changed to " + colour + "&f.");
        if (social != null) social.refreshTab();
    }

    private void save() {
        try {
            file.getParentFile().mkdirs();
            data.save(file);
        } catch (IOException exception) {
            plugin.getLogger().warning("Could not save preferences.yml: " + exception.getMessage());
        }
    }

    @Override public @NotNull List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                          @NotNull String alias, String @NotNull [] args) {
        return List.of();
    }

    private record Colour(String code, String name, Material icon) { }
}
