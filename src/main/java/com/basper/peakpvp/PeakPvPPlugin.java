package com.basper.peakpvp;

import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.command.PluginCommand;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class PeakPvPPlugin extends JavaPlugin {
    private World pvpWorld;
    private LobbyItemsModule lobbyItems;
    private KitModule kits;
    private final Set<UUID> spawnTeleports = new HashSet<>();

    @Override public void onEnable() {
        saveDefaultConfig();
        ConfigMigrator.upgrade(this);
        reloadConfig();
        registerSpawnCommand();
        getServer().getScheduler().runTask(this, this::enableAfterWorldLoad);
    }

    private void enableAfterWorldLoad() {
        if (!isEnabled()) return;
        try {
            ensureWorld();
            ArenaManager arenas = new ArenaManager(this);
            arenas.load();
            BlockRollbackModule rollback = new BlockRollbackModule(this, arenas);
            rollback.enable();
            WorldProtection protection = new WorldProtection(this, rollback);
            protection.enable();
            ColorModule colors = new ColorModule(this);
            colors.enable();
            SocialModule social = new SocialModule(this, colors);
            colors.setSocialModule(social);
            social.enable();
            StatsModule stats = new StatsModule(this);
            stats.enable();
            new ScoreboardModule(this, social, stats).enable();
            kits = new KitModule(this, arenas, rollback, stats);
            kits.enable();
            AdminCommand admin = new AdminCommand(this, arenas, kits);
            PluginCommand adminCommand = requireCommand("peakpvp");
            adminCommand.setExecutor(admin);
            adminCommand.setTabCompleter(admin);
            getServer().getPluginManager().registerEvents(admin, this);
            new CombatModule(this, kits).enable();
            PartyModule parties = new PartyModule(this, kits);
            kits.setPartyModule(parties);
            new ArenaGuardModule(this, arenas, kits).enable();
            parties.enable();
            new DuelRequestModule(this, kits, parties).enable();
            new SeenModule(this).enable();
            lobbyItems = new LobbyItemsModule(this, kits, parties);
            lobbyItems.enable();
            getLogger().info("PeakPvP enabled in world '" + pvpWorld.getName() + "'.");
        } catch (RuntimeException exception) {
            getLogger().severe("PeakPvP could not finish initialising: " + exception.getMessage());
            exception.printStackTrace();
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    private void registerSpawnCommand() {
        requireCommand("spawn").setExecutor((sender, command, label, args) -> {
            if (!(sender instanceof org.bukkit.entity.Player player)) {
                message(sender, "Players only.");
                return true;
            }
            if (kits != null && kits.isInDuel(player.getUniqueId())) {
                message(player, "You cannot use &c/spawn&f while you are in an arena duel.");
                return true;
            }
            sendToSpawn(player, true);
            return true;
        });
    }

    void sendToSpawn(org.bukkit.entity.Player player, boolean showMessage) {
        if (!spawnTeleports.add(player.getUniqueId())) return;
        if (kits != null) kits.cancelQueue(player.getUniqueId());
        player.getInventory().clear();
        player.teleportAsync(spawn()).whenComplete((success, error) -> getServer().getScheduler().runTask(this, () -> {
            spawnTeleports.remove(player.getUniqueId());
            if (error != null || !Boolean.TRUE.equals(success) || !player.isOnline()) return;
            player.getInventory().clear();
            resetPlayerVitals(player);
            refreshLobbyItems(player);
            if (showMessage) message(player, "Teleported to spawn.");
        }));
    }

    private PluginCommand requireCommand(String name) {
        PluginCommand command = getCommand(name);
        if (command == null) throw new IllegalStateException("Missing command /" + name);
        return command;
    }

    void command(String name, org.bukkit.command.CommandExecutor executor) {
        PluginCommand command = requireCommand(name);
        command.setExecutor(executor);
        if (executor instanceof org.bukkit.command.TabCompleter completer) command.setTabCompleter(completer);
    }

    private void ensureWorld() {
        String name = getConfig().getString("world.name", "pvp");
        pvpWorld = Bukkit.getWorld(name);
        if (pvpWorld == null) pvpWorld = new WorldCreator(name).generator(new VoidWorldGenerator()).generateStructures(false).createWorld();
        if (pvpWorld == null) throw new IllegalStateException("Could not create the PvP world");
        Location spawn = spawn();
        if (pvpWorld.getBlockAt(spawn.clone().subtract(0, 1, 0)).getType() == org.bukkit.Material.BEDROCK) {
            pvpWorld.getBlockAt(spawn.clone().subtract(0, 1, 0)).setType(org.bukkit.Material.AIR, false);
        }
        pvpWorld.setSpawnLocation(spawn);
        pvpWorld.setDifficulty(Difficulty.HARD);
        pvpWorld.setGameRule(GameRule.DO_MOB_SPAWNING, false);
        pvpWorld.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
        pvpWorld.setStorm(false);
        pvpWorld.setThundering(false);
    }

    Location spawn() {
        int y = getConfig().getInt("world.spawn-y", 66);
        return new Location(pvpWorld, 0.5, y + 1, 0.5, 0, 0);
    }

    World pvpWorld() { return pvpWorld; }
    double protectionRadius() { return Math.min(50, Math.max(0, getConfig().getDouble("world.spawn-protection-radius", 50))); }
    double protectionY() { return getConfig().getDouble("world.spawn-protection-y", 65); }

    boolean insideSpawn(Location location) {
        if (location == null || location.getWorld() == null || !location.getWorld().equals(pvpWorld)
                || location.getY() < protectionY()) return false;
        double radius = protectionRadius();
        return location.getX() * location.getX() + location.getZ() * location.getZ() <= radius * radius;
    }

    void refreshLobbyItems(org.bukkit.entity.Player player) {
        if (lobbyItems != null) lobbyItems.refresh(player);
    }

    void resetPlayerVitals(org.bukkit.entity.Player player) {
        player.setHealth(player.getMaxHealth());
        player.setAbsorptionAmount(0);
        player.setFoodLevel(20);
        player.setSaturation(20);
        player.setExhaustion(0);
        player.setFireTicks(0);
    }

    void message(org.bukkit.command.CommandSender sender, String value) {
        sender.sendMessage(Messages.legacy(getConfig().getString("messages.prefix", "&b&lPEAK &8» &f") + value));
    }

    void broadcast(String value) {
        Bukkit.broadcast(Messages.legacy(getConfig().getString("messages.prefix", "&b&lPEAK &8Â» &f") + value));
    }

    @Override public @Nullable ChunkGenerator getDefaultWorldGenerator(@NotNull String worldName, @Nullable String id) {
        return worldName.equalsIgnoreCase(getConfig().getString("world.name", "pvp")) ? new VoidWorldGenerator() : null;
    }
}
