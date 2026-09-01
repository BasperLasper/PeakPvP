package com.basper.peakpvp;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

final class ArenaGuardModule implements Listener {
    private final PeakPvPPlugin plugin;
    private final ArenaManager arenas;
    private final KitModule kits;

    ArenaGuardModule(PeakPvPPlugin plugin, ArenaManager arenas, KitModule kits) {
        this.plugin = plugin;
        this.arenas = arenas;
        this.kits = kits;
    }

    void enable() { plugin.getServer().getPluginManager().registerEvents(this, plugin); }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void move(PlayerMoveEvent event) {
        if (event.getTo() == null || sameBlock(event.getFrom(), event.getTo())) return;
        guard(event.getPlayer(), event.getTo());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void teleport(PlayerTeleportEvent event) { guard(event.getPlayer(), event.getTo()); }

    private void guard(Player player, Location destination) {
        if (destination == null || arenas.containing(destination) == null) return;
        if (kits.isInDuel(player.getUniqueId()) || player.hasPermission("peakpvp.admin.arena")) return;
        player.kick(Messages.legacy("&cYou were kicked for entering an arena without a match."));
    }

    private boolean sameBlock(Location first, Location second) {
        return first.getWorld() == second.getWorld()
                && first.getBlockX() == second.getBlockX()
                && first.getBlockY() == second.getBlockY()
                && first.getBlockZ() == second.getBlockZ();
    }
}
