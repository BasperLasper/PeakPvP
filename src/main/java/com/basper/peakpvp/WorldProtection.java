package com.basper.peakpvp;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockMultiPlaceEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.block.BlockState;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.entity.Item;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.entity.PotionSplashEvent;

final class WorldProtection implements Listener {
    private final PeakPvPPlugin plugin;
    private final BlockRollbackModule rollback;
    WorldProtection(PeakPvPPlugin plugin, BlockRollbackModule rollback) {
        this.plugin = plugin;
        this.rollback = rollback;
    }

    void enable() {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::removeDroppedItems, 1, 20);
    }

    @EventHandler(ignoreCancelled = true) public void breakBlock(BlockBreakEvent event) {
        if (isPvPWorld(event.getBlock().getWorld()) && !event.getPlayer().hasPermission("peakpvp.admin.build")
                && !rollback.canEdit(event.getBlock().getLocation())
                && !rollback.canTemporaryEdit(event.getBlock().getLocation())) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true) public void placeBlock(BlockPlaceEvent event) {
        if (!isPvPWorld(event.getBlock().getWorld()) || event.getPlayer().hasPermission("peakpvp.admin.build")) return;
        if (event instanceof BlockMultiPlaceEvent multiPlace) {
            for (BlockState replaced : multiPlace.getReplacedBlockStates()) {
                if (!canPlace(replaced.getLocation())) {
                    event.setCancelled(true);
                    return;
                }
            }
            return;
        }
        if (!canPlace(event.getBlock().getLocation())) event.setCancelled(true);
    }

    private boolean canPlace(Location location) {
        return rollback.canEdit(location) || rollback.canTemporaryPlace(location);
    }

    @EventHandler public void mobSpawn(CreatureSpawnEvent event) {
        if (isPvPWorld(event.getLocation().getWorld())) event.setCancelled(true);
    }

    @EventHandler public void itemSpawn(ItemSpawnEvent event) {
        if (isPvPWorld(event.getLocation().getWorld())) event.setCancelled(true);
    }

    @EventHandler public void potionSplash(PotionSplashEvent event) {
        if (!isPvPWorld(event.getEntity().getWorld())) return;
        event.getAffectedEntities().removeIf(entity -> entity instanceof Player player && insideSpawn(player.getLocation()));
        if (event.getAffectedEntities().isEmpty()) event.setCancelled(true);
    }

    private void removeDroppedItems() {
        if (plugin.pvpWorld() == null) return;
        for (Item item : plugin.pvpWorld().getEntitiesByClass(Item.class)) item.remove();
    }

    @EventHandler(ignoreCancelled = true) public void pvp(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;
        Player attacker = attacker(event);
        if (attacker == null || attacker.hasPermission("peakpvp.admin.spawnpvp")) return;
        if (insideSpawn(victim.getLocation()) || insideSpawn(attacker.getLocation())) event.setCancelled(true);
    }

    private Player attacker(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player) return player;
        if (event.getDamager() instanceof org.bukkit.entity.Projectile projectile && projectile.getShooter() instanceof Player player) return player;
        return null;
    }

    private boolean insideSpawn(Location location) {
        if (!isPvPWorld(location.getWorld())) return false;
        if (location.getY() < plugin.protectionY()) return false;
        double radius = plugin.protectionRadius();
        return location.getX() * location.getX() + location.getZ() * location.getZ() <= radius * radius;
    }

    private boolean isPvPWorld(org.bukkit.World world) { return world != null && world.equals(plugin.pvpWorld()); }

}
