package com.basper.peakpvp;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.Registry;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.UUID;

final class KitModule implements Listener, TabExecutor {
    private static final Component MENU_TITLE = Messages.legacy("&0Unranked Kits");
    private static final int[] KIT_SLOTS = {0, 1, 2, 3, 4, 5, 6, 9, 10, 11, 12, 13, 14, 15};
    private static final int FAST_JOIN_SLOT = 17;
    private static final String FAST_JOIN_ID = "__fast_join__";
    private final PeakPvPPlugin plugin;
    private final ArenaManager arenas;
    private final BlockRollbackModule rollback;
    private final File file;
    private final Map<String, List<ItemStack>> kits = new java.util.LinkedHashMap<>();
    private final Map<String, ItemStack[]> armor = new java.util.LinkedHashMap<>();
    private final Map<String, String> displayNames = new java.util.LinkedHashMap<>();
    private final Map<String, Material> icons = new java.util.LinkedHashMap<>();
    private final Map<Integer, String> menuSlots = new java.util.HashMap<>();
    private final Map<String, LinkedHashSet<UUID>> queues = new java.util.LinkedHashMap<>();
    private final Map<UUID, String> queuedKits = new java.util.HashMap<>();
    private final Map<UUID, UUID> forcedOpponents = new java.util.HashMap<>();
    private final Map<UUID, Duel> duels = new java.util.HashMap<>();
    private final Set<String> usedArenas = new HashSet<>();
    private final Set<UUID> pendingLobbyReturn = new HashSet<>();
    private final Set<UUID> skipKitVotes = new HashSet<>();
    private static final long PVP_ZONE_ROTATION_MILLIS = 5 * 60 * 1000L;
    private String pvpZoneKit;
    private long nextPvpZoneKitAt;
    private PartyModule parties;

    KitModule(PeakPvPPlugin plugin, ArenaManager arenas, BlockRollbackModule rollback) {
        this.plugin = plugin;
        this.arenas = arenas;
        this.rollback = rollback;
        this.file = new File(plugin.getDataFolder(), "kits.yml");
    }

    void enable() {
        if (!file.isFile()) plugin.saveResource("kits.yml", false);
        ConfigMigrator.resetFileIfOlder(plugin, "kits.yml");
        load();
        pvpZoneKit = randomKit();
        nextPvpZoneKitAt = System.currentTimeMillis() + PVP_ZONE_ROTATION_MILLIS;
        Bukkit.getScheduler().runTaskTimer(plugin, this::rotatePvpZoneKit, 6000L, 6000L);
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        plugin.command("kit", this);
        plugin.command("nextkit", this);
        plugin.command("skipkit", this);
    }

    void setPartyModule(PartyModule parties) { this.parties = parties; }

    void load() {
        kits.clear();
        armor.clear();
        displayNames.clear();
        icons.clear();
        YamlConfiguration data = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection groups = data.getConfigurationSection("kits.unranked");
        if (groups == null) return;
        for (String key : groups.getKeys(false)) {
            String path = "kits.unranked." + key;
            ConfigurationSection itemSection = data.getConfigurationSection(path + ".items");
            List<ItemStack> contents = new ArrayList<>();
            for (int slot = 0; slot < 36; slot++) contents.add(null);
            ConfigurationSection fills = data.getConfigurationSection(path + ".fills");
            if (fills != null) {
                for (String fillKey : fills.getKeys(false)) {
                    String fillPath = path + ".fills." + fillKey;
                    ItemStack template = readItem(data, fillPath);
                    if (template == null) continue;
                    for (int slot : parseSlots(data.getString(fillPath + ".slots", ""))) {
                        if (slot >= 0 && slot < 36) contents.set(slot, template.clone());
                    }
                }
            }
            if (itemSection != null) {
                for (String slotKey : itemSection.getKeys(false)) {
                    try {
                        int slot = Integer.parseInt(slotKey);
                        if (slot >= 0 && slot < 36) contents.set(slot, readItem(data, path + ".items." + slotKey));
                    } catch (NumberFormatException ignored) { }
                }
            }
            String id = key.toLowerCase(Locale.ROOT);
            kits.put(id, contents);
            displayNames.put(id, data.getString(path + ".display-name", "&a" + key));
            Material icon = Material.matchMaterial(data.getString(path + ".icon", "DIAMOND_SWORD"));
            icons.put(id, icon == null || icon.isAir() ? Material.DIAMOND_SWORD : icon);
            ItemStack[] kitArmor = new ItemStack[4];
            kitArmor[0] = readItem(data, path + ".armor.boots");
            kitArmor[1] = readItem(data, path + ".armor.leggings");
            kitArmor[2] = readItem(data, path + ".armor.chestplate");
            kitArmor[3] = readItem(data, path + ".armor.helmet");
            armor.put(id, kitArmor);
        }
    }

    void openUnrankedMenu(Player player) {
        if (!player.hasPermission("peakpvp.kit.use")) { plugin.message(player, "You do not have permission to choose a kit."); return; }
        Inventory inventory = Bukkit.createInventory(null, 18, MENU_TITLE);
        menuSlots.clear();
        int index = 0;
        for (String id : kits.keySet()) {
            if (index >= KIT_SLOTS.length) break;
            int slot = KIT_SLOTS[index++];
            ItemStack item = kitMenuItem(id);
            inventory.setItem(slot, item);
            menuSlots.put(slot, id);
        }
        inventory.setItem(FAST_JOIN_SLOT, fastJoinItem());
        menuSlots.put(FAST_JOIN_SLOT, FAST_JOIN_ID);
        player.openInventory(inventory);
    }

    boolean isInDuel(UUID playerId) { return duels.containsKey(playerId); }

    boolean isCombo(Player player) {
        Duel duel = duels.get(player.getUniqueId());
        if (duel != null) return duel.kit().equals("combo");
        return player.getWorld().equals(plugin.pvpWorld()) && player.getLocation().getY() < plugin.protectionY()
                && "combo".equals(pvpZoneKit);
    }

    void cancelQueue(UUID playerId) {
        leaveQueue(playerId);
        refreshOpenMenus();
    }

    void enterPvpZone(Player player) {
        if (duels.containsKey(player.getUniqueId()) || kits.isEmpty()) return;
        leaveQueue(player.getUniqueId());
        if (pvpZoneKit == null) pvpZoneKit = randomKit();
        equip(player, pvpZoneKit);
        plugin.message(player, "You entered the PvP Zone. Random kit: " + displayNames.getOrDefault(pvpZoneKit, "&a" + pvpZoneKit) + "&f.");
        refreshOpenMenus();
    }

    private void rotatePvpZoneKit() {
        if (kits.isEmpty()) return;
        skipKitVotes.clear();
        String previous = pvpZoneKit;
        do { pvpZoneKit = randomKit(); } while (kits.size() > 1 && pvpZoneKit.equals(previous));
        nextPvpZoneKitAt = System.currentTimeMillis() + PVP_ZONE_ROTATION_MILLIS;
        plugin.broadcast("&d&lPvP Zone Kit &8» &fThe new random kit is "
                + displayNames.getOrDefault(pvpZoneKit, "&a" + pvpZoneKit) + "&f. Next change in 5 minutes.");
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getWorld().equals(plugin.pvpWorld()) && player.getLocation().getY() < plugin.protectionY()
                    && !duels.containsKey(player.getUniqueId())) {
                leaveQueue(player.getUniqueId());
                equip(player, pvpZoneKit);
                plugin.message(player, "PvP Zone kit changed to " + displayNames.getOrDefault(pvpZoneKit, "&a" + pvpZoneKit) + "&f.");
            }
        }
    }

    private String randomKit() {
        List<String> values = new ArrayList<>(kits.keySet());
        return values.isEmpty() ? null : values.get(ThreadLocalRandom.current().nextInt(values.size()));
    }

    private void nextKit(Player player) {
        long remaining = Math.max(0, nextPvpZoneKitAt - System.currentTimeMillis()) / 1000;
        long minutes = remaining / 60;
        long seconds = remaining % 60;
        plugin.message(player, "Current PvP Zone kit: " + displayNames.getOrDefault(pvpZoneKit, "&a" + pvpZoneKit)
                + "&f. Next change in &e" + minutes + "m " + seconds + "s&f.");
    }

    private ItemStack kitMenuItem(String id) {
        int queued = queuedCount(id);
        ItemStack item = new ItemStack(icons.getOrDefault(id, Material.DIAMOND_SWORD), Math.max(1, Math.min(64, queued)));
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Messages.legacy(displayNames.getOrDefault(id, "&a" + id)));
        meta.lore(List.of(Messages.legacy("&7Unranked kit"), Messages.legacy("&fPlayers queuing: &e" + queued), Messages.legacy(""), Messages.legacy("&eClick to join queue")));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack fastJoinItem() {
        int queued = totalQueued();
        ItemStack item = new ItemStack(Material.COMPASS, Math.max(1, Math.min(64, queued)));
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Messages.legacy("&d&lFast Join"));
        meta.lore(List.of(Messages.legacy("&7Join a queue with another player"), Messages.legacy("&fPlayers waiting: &e" + queued), Messages.legacy(""), Messages.legacy("&eClick to fast join")));
        item.setItemMeta(meta);
        return item;
    }

    private void queue(Player player, String name) {
        String id = name.toLowerCase(Locale.ROOT);
        if (!kits.containsKey(id)) { plugin.message(player, "That kit does not exist."); return; }
        if (duels.containsKey(player.getUniqueId())) { plugin.message(player, "You are already in a duel."); return; }
        if (parties != null) {
            List<UUID> party = parties.membersForQueue(player.getUniqueId());
            if (party.size() == 2) {
                queueParty(id, party);
                return;
            }
        }
        leaveQueue(player.getUniqueId());
        queues.computeIfAbsent(id, ignored -> new LinkedHashSet<>()).add(player.getUniqueId());
        queuedKits.put(player.getUniqueId(), id);
        player.closeInventory();
        plugin.message(player, "Queued for " + displayNames.getOrDefault(id, "&a" + name) + " &f. Waiting for another player to choose the same kit...");
        tryStart(id);
        refreshOpenMenus();
    }

    private void queueParty(String kit, List<UUID> party) {
        List<Player> players = party.stream().map(Bukkit::getPlayer).toList();
        if (players.stream().anyMatch(player -> player == null)) {
            Player player = players.stream().filter(candidate -> candidate != null).findFirst().orElse(null);
            if (player != null) plugin.message(player, "Both party members must be online to queue together.");
            return;
        }
        if (party.stream().anyMatch(duels::containsKey)) {
            players.forEach(player -> plugin.message(player, "Your party cannot queue while a member is in a duel."));
            return;
        }
        party.forEach(this::leaveQueue);
        LinkedHashSet<UUID> queue = queues.computeIfAbsent(kit, ignored -> new LinkedHashSet<>());
        party.forEach(playerId -> {
            queue.add(playerId);
            queuedKits.put(playerId, kit);
        });
        players.forEach(player -> {
            player.closeInventory();
            plugin.message(player, "Your party is queued for " + displayNames.getOrDefault(kit, "&a" + kit) + " &f. You will fight each other when matched.");
        });
        tryStart(kit);
        refreshOpenMenus();
    }

    void queueDirectDuel(Player first, Player second) {
        if (kits.isEmpty()) {
            plugin.message(first, "No kits are available for a duel yet.");
            plugin.message(second, "No kits are available for a duel yet.");
            return;
        }
        if (duels.containsKey(first.getUniqueId()) || duels.containsKey(second.getUniqueId())) {
            plugin.message(first, "One of you is already in a duel.");
            plugin.message(second, "One of you is already in a duel.");
            return;
        }
        String kit = pvpZoneKit == null ? randomKit() : pvpZoneKit;
        UUID firstId = first.getUniqueId();
        UUID secondId = second.getUniqueId();
        leaveQueue(firstId);
        leaveQueue(secondId);
        LinkedHashSet<UUID> queue = queues.computeIfAbsent(kit, ignored -> new LinkedHashSet<>());
        queue.add(firstId);
        queue.add(secondId);
        queuedKits.put(firstId, kit);
        queuedKits.put(secondId, kit);
        forcedOpponents.put(firstId, secondId);
        forcedOpponents.put(secondId, firstId);
        first.closeInventory();
        second.closeInventory();
        plugin.message(first, "Duel accepted. Queued for " + displayNames.getOrDefault(kit, "&a" + kit) + "&f.");
        plugin.message(second, "Duel accepted. Queued for " + displayNames.getOrDefault(kit, "&a" + kit) + "&f.");
        tryStart(kit);
        refreshOpenMenus();
    }

    private void tryStart(String kit) {
        LinkedHashSet<UUID> queue = queues.get(kit);
        if (queue == null) return;
        for (UUID id : new HashSet<>(queue)) if (Bukkit.getPlayer(id) == null) leaveQueue(id);
        while (queue.size() >= 2) {
            UUID firstId = null;
            UUID secondId = null;
            for (UUID candidate : queue) {
                UUID partner = forcedOpponents.get(candidate);
                if (partner != null && queue.contains(partner) && candidate.equals(forcedOpponents.get(partner))) {
                    firstId = candidate;
                    secondId = partner;
                    break;
                }
            }
            if (firstId == null) firstId = queue.iterator().next();
            if (parties != null) {
                for (UUID candidate : queue) {
                    if (!candidate.equals(firstId) && parties.sameParty(firstId, candidate)) {
                        secondId = candidate;
                        break;
                    }
                }
            }
            if (secondId == null && parties != null) {
                for (UUID candidate : queue) {
                    for (UUID teammate : queue) {
                        if (!candidate.equals(teammate) && parties.sameParty(candidate, teammate)) {
                            firstId = candidate;
                            secondId = teammate;
                            break;
                        }
                    }
                    if (secondId != null) break;
                }
            }
            if (secondId == null) {
                for (UUID candidate : queue) {
                    if (!candidate.equals(firstId)) {
                        secondId = candidate;
                        break;
                    }
                }
            }
            queue.remove(firstId);
            queue.remove(secondId);
            queuedKits.remove(firstId);
            queuedKits.remove(secondId);
            Player first = Bukkit.getPlayer(firstId);
            Player second = Bukkit.getPlayer(secondId);
            if (first == null || second == null) {
                forcedOpponents.remove(firstId);
                forcedOpponents.remove(secondId);
                if (first != null) queue(first, kit);
                if (second != null) queue(second, kit);
                continue;
            }
            ArenaManager.Arena arena = findAvailableArena();
            if (arena == null) {
                queue.add(firstId);
                queue.add(secondId);
                queuedKits.put(firstId, kit);
                queuedKits.put(secondId, kit);
                plugin.message(first, "No ready arenas are available yet. You are still queued.");
                plugin.message(second, "No ready arenas are available yet. You are still queued.");
                return;
            }
            forcedOpponents.remove(firstId);
            forcedOpponents.remove(secondId);
            String arenaId = arena.name().toLowerCase(Locale.ROOT);
            usedArenas.add(arenaId);
            rollback.begin(arena);
            Duel duel = new Duel(firstId, secondId, kit, arenaId);
            duels.put(firstId, duel);
            duels.put(secondId, duel);
            equip(first, kit);
            equip(second, kit);
            first.teleport(arena.spawn1());
            second.teleport(arena.spawn2());
            plugin.message(first, "Matched! Your " + displayNames.getOrDefault(kit, "&a" + kit) + " &fduel is starting.");
            plugin.message(second, "Matched! Your " + displayNames.getOrDefault(kit, "&a" + kit) + " &fduel is starting.");
        }
        if (queue.isEmpty()) queues.remove(kit);
    }

    private ArenaManager.Arena findAvailableArena() {
        for (ArenaManager.Arena arena : arenas.all()) {
            if (arena.ready() && arena.spawn1().getWorld() != null && arena.spawn2().getWorld() != null
                    && !usedArenas.contains(arena.name().toLowerCase(Locale.ROOT))) return arena;
        }
        return null;
    }

    private void leaveQueue(UUID playerId) {
        String oldKit = queuedKits.remove(playerId);
        if (oldKit != null) {
            LinkedHashSet<UUID> queue = queues.get(oldKit);
            if (queue != null) {
                queue.remove(playerId);
                if (queue.isEmpty()) queues.remove(oldKit);
            }
        }
        UUID partner = forcedOpponents.remove(playerId);
        if (partner != null && playerId.equals(forcedOpponents.get(partner))) forcedOpponents.remove(partner);
    }

    private int queuedCount(String kit) {
        LinkedHashSet<UUID> queue = queues.get(kit);
        if (queue == null) return 0;
        queue.removeIf(uuid -> Bukkit.getPlayer(uuid) == null);
        return queue.size();
    }

    private int totalQueued() {
        int total = 0;
        for (String kit : new ArrayList<>(queues.keySet())) total += queuedCount(kit);
        return total;
    }

    private void fastJoin(Player player) {
        UUID playerId = player.getUniqueId();
        String kit = null;
        for (Map.Entry<String, LinkedHashSet<UUID>> entry : queues.entrySet()) {
            queuedCount(entry.getKey());
            boolean hasOther = entry.getValue().stream().anyMatch(uuid -> !uuid.equals(playerId) && Bukkit.getPlayer(uuid) != null);
            if (hasOther) { kit = entry.getKey(); break; }
        }
        if (kit == null) {
            plugin.message(player, "There are no unranked players waiting to fast join.");
            return;
        }
        queue(player, kit);
    }

    private void refreshOpenMenus() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!player.getOpenInventory().title().equals(MENU_TITLE)) continue;
            Inventory inventory = player.getOpenInventory().getTopInventory();
            int index = 0;
            for (String id : kits.keySet()) {
                if (index >= KIT_SLOTS.length) break;
                inventory.setItem(KIT_SLOTS[index++], kitMenuItem(id));
            }
            inventory.setItem(FAST_JOIN_SLOT, fastJoinItem());
        }
    }

    private void equip(Player player, String name) {
        List<ItemStack> contents = kits.get(name.toLowerCase(Locale.ROOT));
        if (contents == null) { plugin.message(player, "That kit does not exist."); return; }
        player.getInventory().clear();
        for (PotionEffect effect : player.getActivePotionEffects()) player.removePotionEffect(effect.getType());
        for (int slot = 0; slot < contents.size(); slot++) {
            ItemStack item = contents.get(slot);
            if (item != null) player.getInventory().setItem(slot, item.clone());
        }
        ItemStack[] kitArmor = armor.get(name.toLowerCase(Locale.ROOT));
        if (kitArmor != null) {
            ItemStack[] cloned = new ItemStack[4];
            for (int index = 0; index < cloned.length; index++) cloned[index] = kitArmor[index] == null ? null : kitArmor[index].clone();
            player.getInventory().setArmorContents(cloned);
        }
        player.setHealth(player.getMaxHealth());
        player.setAbsorptionAmount(0);
        player.setFoodLevel(20);
        player.setSaturation(20);
        player.updateInventory();
    }

    private ItemStack readItem(YamlConfiguration data, String path) {
        Material material = Material.matchMaterial(data.getString(path + ".material", "AIR"));
        if (material == null || material.isAir()) return null;
        ItemStack item = new ItemStack(material, Math.max(1, Math.min(material.getMaxStackSize(), data.getInt(path + ".amount", 1))));
        String customName = data.getString(path + ".name");
        if (customName != null) {
            ItemMeta meta = item.getItemMeta();
            meta.displayName(Messages.legacy(customName));
            item.setItemMeta(meta);
        }
        String effectName = data.getString(path + ".effect");
        if (effectName != null && item.getItemMeta() instanceof PotionMeta meta) {
            PotionEffectType effect = potionEffect(effectName);
            if (effect != null) {
                PotionType base = potionType(effectName, data.getInt(path + ".amplifier", 0));
                if (base != null) meta.setBasePotionType(base);
                else meta.addCustomEffect(new PotionEffect(effect, Math.max(1, data.getInt(path + ".duration", 1)), Math.max(0, data.getInt(path + ".amplifier", 0))), true);
                item.setItemMeta(meta);
            }
        }
        ConfigurationSection enchantments = data.getConfigurationSection(path + ".enchantments");
        if (enchantments != null) {
            for (String enchantmentName : enchantments.getKeys(false)) {
                Enchantment enchantment = enchantment(enchantmentName);
                if (enchantment != null) item.addUnsafeEnchantment(enchantment, Math.max(1, enchantments.getInt(enchantmentName, 1)));
            }
        }
        return item;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void soup(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getItem() == null || event.getItem().getType() != Material.MUSHROOM_STEW || !isSoupKit(event.getPlayer())) return;
        event.setCancelled(true);
        event.getItem().setAmount(event.getItem().getAmount() - 1);
        event.getPlayer().getInventory().addItem(new ItemStack(Material.BOWL));
        event.getPlayer().setHealth(Math.min(event.getPlayer().getMaxHealth(), event.getPlayer().getHealth() + 6.0));
    }

    private boolean isSoupKit(Player player) {
        Duel duel = duels.get(player.getUniqueId());
        if (duel != null) return duel.kit().equals("soup");
        return player.getWorld().equals(plugin.pvpWorld()) && player.getLocation().getY() < plugin.protectionY()
                && "soup".equals(pvpZoneKit);
    }

    private Enchantment enchantment(String name) {
        NamespacedKey key = NamespacedKey.fromString(name.toLowerCase(Locale.ROOT));
        if (key == null) key = NamespacedKey.minecraft(name.toLowerCase(Locale.ROOT));
        return Registry.ENCHANTMENT.get(key);
    }

    private PotionEffectType potionEffect(String name) {
        return switch (name.toUpperCase(Locale.ROOT)) {
            case "SPEED" -> PotionEffectType.SPEED;
            case "FIRE_RESISTANCE" -> PotionEffectType.FIRE_RESISTANCE;
            case "INSTANT_HEALTH", "HEALING" -> PotionEffectType.INSTANT_HEALTH;
            case "STRENGTH" -> PotionEffectType.STRENGTH;
            case "SLOWNESS" -> PotionEffectType.SLOWNESS;
            case "POISON" -> PotionEffectType.POISON;
            default -> null;
        };
    }

    private PotionType potionType(String name, int amplifier) {
        return switch (name.toUpperCase(Locale.ROOT)) {
            case "SPEED" -> amplifier > 0 ? PotionType.STRONG_SWIFTNESS : PotionType.SWIFTNESS;
            case "FIRE_RESISTANCE" -> PotionType.FIRE_RESISTANCE;
            case "INSTANT_HEALTH", "HEALING" -> amplifier > 0 ? PotionType.STRONG_HEALING : PotionType.HEALING;
            case "STRENGTH" -> amplifier > 0 ? PotionType.STRONG_STRENGTH : PotionType.STRENGTH;
            case "SLOWNESS" -> amplifier > 0 ? PotionType.STRONG_SLOWNESS : PotionType.SLOWNESS;
            case "POISON" -> amplifier > 0 ? PotionType.STRONG_POISON : PotionType.POISON;
            default -> null;
        };
    }

    @Override public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String @NotNull [] args) {
        if (!(sender instanceof Player player)) { plugin.message(sender, "Players only."); return true; }
        if (command.getName().equalsIgnoreCase("skipkit")) {
            if (!skipKitVotes.add(player.getUniqueId())) {
                plugin.message(player, "You have already voted to skip this kit.");
                return true;
            }
            int required = 2;
            int votes = skipKitVotes.size();
            if (votes >= required) {
                plugin.broadcast("&d&lPvP Zone Kit &8» &fThe skip vote passed (&e" + votes + "/" + required + "&f).");
                rotatePvpZoneKit();
            } else {
                plugin.broadcast("&d&lPvP Zone Kit &8» &f" + player.getName()
                        + " voted to skip the kit (&e" + votes + "/" + required + "&f).");
            }
            return true;
        }
        if (!player.hasPermission("peakpvp.kit.use")) { plugin.message(player, "You do not have permission to choose a kit."); return true; }
        if (command.getName().equalsIgnoreCase("nextkit")) { nextKit(player); return true; }
        if (args.length == 0) { openUnrankedMenu(player); return true; }
        if (args.length == 1 && args[0].equalsIgnoreCase("fastjoin")) { fastJoin(player); return true; }
        String name = args[0].equalsIgnoreCase("unranked") && args.length > 1 ? args[1] : args[0];
        queue(player, name);
        return true;
    }

    @EventHandler(priority = EventPriority.HIGHEST) public void click(InventoryClickEvent event) {
        if (!event.getView().title().equals(MENU_TITLE)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        String kit = menuSlots.get(event.getRawSlot());
        if (FAST_JOIN_ID.equals(kit)) fastJoin(player);
        else if (kit != null) queue(player, kit);
    }

    @EventHandler public void quit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (player.getOpenInventory().title().equals(MENU_TITLE)) player.closeInventory();
        leaveQueue(player.getUniqueId());
        skipKitVotes.remove(player.getUniqueId());
        refreshOpenMenus();
        Duel duel = duels.remove(player.getUniqueId());
        if (duel == null) return;
        UUID opponentId = duel.opponent(player.getUniqueId());
        duels.remove(opponentId);
        usedArenas.remove(duel.arena());
        rollback.end(arenas.get(duel.arena()));
        Player opponent = Bukkit.getPlayer(opponentId);
        if (opponent != null) {
            plugin.broadcast("&c" + opponent.getName() + " &awon the " + displayNames.getOrDefault(duel.kit(), "&a" + duel.kit())
                    + " &amatch against &c" + player.getName() + "&a.");
            plugin.message(opponent, "You won the duel because your opponent left.");
            returnToLobby(opponent);
        }
    }

    @EventHandler public void death(PlayerDeathEvent event) {
        Player loser = event.getEntity();
        Duel duel = duels.remove(loser.getUniqueId());
        if (duel == null) {
            rewardFfaKill(loser);
            return;
        }
        event.getDrops().clear();
        event.setDroppedExp(0);
        event.setKeepInventory(true);
        UUID winnerId = duel.opponent(loser.getUniqueId());
        duels.remove(winnerId);
        usedArenas.remove(duel.arena());
        rollback.end(arenas.get(duel.arena()));
        pendingLobbyReturn.add(loser.getUniqueId());
        Player winner = Bukkit.getPlayer(winnerId);
        if (winner != null) {
            plugin.broadcast("&c" + winner.getName() + " &awon the " + displayNames.getOrDefault(duel.kit(), "&a" + duel.kit())
                    + " &amatch against &c" + loser.getName() + "&a.");
            plugin.message(winner, "You won the " + displayNames.getOrDefault(duel.kit(), "&a" + duel.kit()) + " duel!");
            returnToLobby(winner);
        }
        plugin.message(loser, "You lost the " + displayNames.getOrDefault(duel.kit(), "&a" + duel.kit()) + " duel.");
        refreshOpenMenus();
    }

    private void rewardFfaKill(Player loser) {
        Player killer = loser.getKiller();
        if (killer == null || killer.equals(loser) || !isFfaPlayer(loser) || !isFfaPlayer(killer)) return;
        killer.setAbsorptionAmount(killer.getAbsorptionAmount() + 4.0);
        plugin.message(killer, "Kill reward: &e+2 hearts&f.");
    }

    private boolean isFfaPlayer(Player player) {
        return player.getWorld().equals(plugin.pvpWorld()) && player.getLocation().getY() < plugin.protectionY()
                && !duels.containsKey(player.getUniqueId());
    }

    @EventHandler public void respawn(PlayerRespawnEvent event) {
        if (!pendingLobbyReturn.remove(event.getPlayer().getUniqueId())) return;
        event.setRespawnLocation(plugin.spawn());
        Bukkit.getScheduler().runTask(plugin, () -> returnToLobby(event.getPlayer()));
    }

    private void returnToLobby(Player player) {
        player.getInventory().clear();
        player.teleport(plugin.spawn());
        plugin.refreshLobbyItems(player);
    }

    private record Duel(UUID first, UUID second, String kit, String arena) {
        UUID opponent(UUID player) { return first.equals(player) ? second : first; }
    }

    @Override public @NotNull List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, String @NotNull [] args) {
        if (args.length == 1) {
            List<String> values = new ArrayList<>(kits.keySet());
            values.add("unranked");
            values.add("fastjoin");
            return filter(values, args[0]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("unranked")) return filter(new ArrayList<>(kits.keySet()), args[1]);
        return List.of();
    }

    private List<String> filter(List<String> values, String input) {
        String lower = input.toLowerCase(Locale.ROOT);
        return values.stream().filter(value -> value.toLowerCase(Locale.ROOT).startsWith(lower)).toList();
    }

    private List<Integer> parseSlots(String value) {
        List<Integer> result = new ArrayList<>();
        for (String part : value.split(",")) {
            String trimmed = part.trim();
            if (trimmed.isEmpty()) continue;
            try {
                if (trimmed.contains("-")) {
                    String[] range = trimmed.split("-", 2);
                    int start = Integer.parseInt(range[0].trim());
                    int end = Integer.parseInt(range[1].trim());
                    for (int slot = Math.min(start, end); slot <= Math.max(start, end); slot++) result.add(slot);
                } else result.add(Integer.parseInt(trimmed));
            } catch (NumberFormatException ignored) { }
        }
        return result;
    }
}
