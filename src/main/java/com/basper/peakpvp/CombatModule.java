package com.basper.peakpvp;

import org.bukkit.attribute.Attribute;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

final class CombatModule implements Listener {
    private static final double LEGACY_ATTACK_SPEED = 1024.0;
    private final PeakPvPPlugin plugin;
    private final KitModule kits;

    CombatModule(PeakPvPPlugin plugin, KitModule kits) {
        this.plugin = plugin;
        this.kits = kits;
    }

    void enable() {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        for (Player player : Bukkit.getOnlinePlayers()) apply(player);
    }

    @EventHandler public void join(PlayerJoinEvent event) { apply(event.getPlayer()); }

    @EventHandler public void respawn(PlayerRespawnEvent event) {
        Bukkit.getScheduler().runTask(plugin, () -> apply(event.getPlayer()));
    }

    @EventHandler public void move(PlayerMoveEvent event) {
        if (event.getTo() == null || event.getFrom().getBlockY() == event.getTo().getBlockY()) return;
        apply(event.getPlayer());
    }

    @EventHandler public void teleport(PlayerTeleportEvent event) {
        Bukkit.getScheduler().runTask(plugin, () -> apply(event.getPlayer()));
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void damage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;
        Player attacker = attacker(event);
        if (attacker == null) return;
        apply(attacker);
        if (kits.isCombo(attacker) && kits.isCombo(victim)) {
            victim.setMaximumNoDamageTicks(0);
            victim.setNoDamageTicks(0);
        } else if (victim.getMaximumNoDamageTicks() == 0) {
            victim.setMaximumNoDamageTicks(20);
        }
    }

    private Player attacker(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player) return player;
        if (event.getDamager() instanceof Projectile projectile && projectile.getShooter() instanceof Player player) return player;
        return null;
    }

    private void apply(Player player) {
        if (player.getAttribute(Attribute.ATTACK_SPEED) != null) {
            player.getAttribute(Attribute.ATTACK_SPEED).setBaseValue(kits.usesLegacyCombat(player) ? LEGACY_ATTACK_SPEED : 4.0);
        }
        if (player.getMaximumNoDamageTicks() == 0) player.setMaximumNoDamageTicks(20);
    }
}
