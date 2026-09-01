package com.basper.peakpvp;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockMultiPlaceEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

final class BlockRollbackModule implements Listener {
    private final PeakPvPPlugin plugin;
    private final ArenaManager arenas;
    private final Set<String> activeArenas = new HashSet<>();
    private final Map<String, Map<Location, TrackedChange>> changes = new HashMap<>();
    private final Map<Location, TemporaryChange> temporaryChanges = new HashMap<>();
    private final Map<UUID, Set<Location>> temporaryByPlayer = new HashMap<>();

    BlockRollbackModule(PeakPvPPlugin plugin, ArenaManager arenas) {
        this.plugin = plugin;
        this.arenas = arenas;
    }

    void enable() {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::expireBlocks, 20L, 20L);
    }

    void begin(ArenaManager.Arena arena) {
        if (arena != null) activeArenas.add(key(arena));
    }

    boolean canEdit(Location location) {
        ArenaManager.Arena arena = arenas.containing(location);
        return arena != null && activeArenas.contains(key(arena));
    }

    boolean canTemporaryPlace(Location location) {
        if (!plugin.getConfig().getBoolean("spawn-build.enabled", true)) return false;
        if (location == null || location.getWorld() == null || !location.getWorld().equals(plugin.pvpWorld())) return false;
        double radius = plugin.protectionRadius();
        return location.getX() * location.getX() + location.getZ() * location.getZ() <= radius * radius;
    }

    boolean canTemporaryEdit(Location location) { return temporaryChanges.containsKey(location); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void place(BlockPlaceEvent event) {
        if (event instanceof BlockMultiPlaceEvent multiPlace) {
            for (BlockState replaced : multiPlace.getReplacedBlockStates()) rememberPlaced(replaced, event.getPlayer().getUniqueId());
            return;
        }
        rememberPlaced(event.getBlockReplacedState(), event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void breakBlock(BlockBreakEvent event) {
        if (canEdit(event.getBlock().getLocation())) remember(event.getBlock().getState(), event.getBlock());
        else if (temporaryChanges.containsKey(event.getBlock().getLocation())) removeTemporary(event.getBlock().getLocation(), false);
    }

    @EventHandler public void death(PlayerDeathEvent event) { removeTemporary(event.getEntity().getUniqueId(), true); }

    @EventHandler public void quit(PlayerQuitEvent event) { removeTemporary(event.getPlayer().getUniqueId(), true); }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void liquidFlow(BlockFromToEvent event) {
        ArenaManager.Arena source = arenas.containing(event.getBlock().getLocation());
        ArenaManager.Arena target = arenas.containing(event.getToBlock().getLocation());
        if (source != null && activeArenas.contains(key(source)) && (target == null || !key(source).equals(key(target)))) {
            event.setCancelled(true);
            return;
        }
        if (target != null && activeArenas.contains(key(target))) remember(event.getToBlock().getState(), event.getToBlock());
    }

    void end(ArenaManager.Arena arena) {
        if (arena == null) return;
        String arenaKey = key(arena);
        activeArenas.remove(arenaKey);
        Map<Location, TrackedChange> arenaChanges = changes.remove(arenaKey);
        if (arenaChanges == null) return;
        for (TrackedChange change : arenaChanges.values()) change.original().update(true, false);
    }

    private void remember(BlockState original, Block block) {
        ArenaManager.Arena arena = arenas.containing(block.getLocation());
        if (arena == null || !activeArenas.contains(key(arena))) return;
        changes.computeIfAbsent(key(arena), ignored -> new HashMap<>())
                .putIfAbsent(block.getLocation().clone(), new TrackedChange(original,
                        System.currentTimeMillis() + temporaryDurationMillis()));
    }

    private void rememberPlaced(BlockState original, UUID owner) {
        Block block = original.getBlock();
        if (canEdit(block.getLocation())) remember(original, block);
        else if (canTemporaryPlace(block.getLocation())) rememberTemporary(original, block, owner);
    }

    private void rememberTemporary(BlockState original, Block block, UUID owner) {
        Location location = block.getLocation().clone();
        TemporaryChange previous = temporaryChanges.remove(location);
        if (previous != null) {
            Set<Location> previousBlocks = temporaryByPlayer.get(previous.owner());
            if (previousBlocks != null) {
                previousBlocks.remove(location);
                if (previousBlocks.isEmpty()) temporaryByPlayer.remove(previous.owner());
            }
        }
        temporaryChanges.put(location, new TemporaryChange(owner, original,
                System.currentTimeMillis() + temporaryDurationMillis()));
        temporaryByPlayer.computeIfAbsent(owner, ignored -> new HashSet<>()).add(location);
    }

    private void expireBlocks() {
        expireArenaBlocks();
        expireTemporaryBlocks();
    }

    private void expireArenaBlocks() {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<String, Map<Location, TrackedChange>>> arenaIterator = changes.entrySet().iterator();
        while (arenaIterator.hasNext()) {
            Map<Location, TrackedChange> arenaChanges = arenaIterator.next().getValue();
            Iterator<Map.Entry<Location, TrackedChange>> blockIterator = arenaChanges.entrySet().iterator();
            while (blockIterator.hasNext()) {
                TrackedChange change = blockIterator.next().getValue();
                if (change.expiresAt() > now) continue;
                blockIterator.remove();
                change.original().update(true, false);
            }
            if (arenaChanges.isEmpty()) arenaIterator.remove();
        }
    }

    private void expireTemporaryBlocks() {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<Location, TemporaryChange>> iterator = temporaryChanges.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Location, TemporaryChange> entry = iterator.next();
            if (entry.getValue().expiresAt() > now) continue;
            iterator.remove();
            removeFromOwner(entry.getValue().owner(), entry.getKey());
            entry.getValue().original().update(true, false);
        }
    }

    private void removeTemporary(UUID owner, boolean restore) {
        Set<Location> locations = temporaryByPlayer.remove(owner);
        if (locations == null) return;
        for (Location location : new HashSet<>(locations)) removeTemporary(location, restore);
    }

    private void removeTemporary(Location location, boolean restore) {
        TemporaryChange change = temporaryChanges.remove(location);
        if (change == null) return;
        removeFromOwner(change.owner(), location);
        if (restore) change.original().update(true, false);
    }

    private void removeFromOwner(UUID owner, Location location) {
        Set<Location> locations = temporaryByPlayer.get(owner);
        if (locations == null) return;
        locations.remove(location);
        if (locations.isEmpty()) temporaryByPlayer.remove(owner);
    }

    private long temporaryDurationMillis() {
        return Math.max(1, plugin.getConfig().getLong("spawn-build.duration-seconds", 60)) * 1000L;
    }

    private String key(ArenaManager.Arena arena) { return arena.name().toLowerCase(Locale.ROOT); }

    private record TrackedChange(BlockState original, long expiresAt) { }
    private record TemporaryChange(UUID owner, BlockState original, long expiresAt) { }
}
