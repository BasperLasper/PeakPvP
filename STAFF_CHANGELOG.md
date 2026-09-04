## PeakPvP 0.1.0

- Added `arena-rules.allow-block-breaking` and `arena-rules.allow-fire-spread`, both disabled by default.
- Protected active arenas from entity/block explosions, block burning, and entity-driven block changes while retaining combat damage and knockback.
- Added sword-triggered TNT minecart detonation for Cart kit combat.
- Added bucket-source tracking and a final arena sweep that removes water, lava, and waterlogged states after matches.
- Added `/peakpvp setup` with per-map kit toggles, one-map kit restriction, unrestricted reset, persistent access rules, and kit-aware random arena allocation.
- Setup selections clear after arena creation; arena info reports kit access; reload refreshes arenas and kits; `/ppreload` routing was corrected.
- Added Spear Mace, Cart, and OP Cart inventories and raised `kits.yml` to config version 5.
- Expanded kit and layout menus to 27 slots and converted existing bottled potions to splash potions.
- Added a lobby Quick Join compass and cross-mode solo queue for whichever 1.8 or Latest kit another player selects first.
- Added a five-block, same-Y spawn return zone using the shared asynchronous `/spawn` flow.
- Updated the Paper and plugin API target to 1.21.11 and documented the separately installed Attribute Swap Fixer dependency.

- Added the `/seen <player>` command using Paper's persisted offline-player last-seen data.
- Created the Paper 1.21.8 and Java 21 Gradle project foundation.
- Added startup-safe void-world creation and the `PeakPvP` chunk generator.
- Spawn places one bedrock block at 0, 64, 0 and positions players at 0.5, 65, 0.5.
- Added build protection with the `peakpvp.admin.build` bypass.
- Added radial PvP protection for melee and projectile damage with the `peakpvp.admin.spawnpvp` bypass.
- Added BungeeCord-compatible network population, TPS, ping, LuckPerms metadata and mention support.
- Added CI artifact builds and compact public changelog delivery to Discord.
- Added a configurable per-player sidebar refreshed every second with network and player-specific values.
- Added configurable, persistent-data-tagged lobby items with inventory, dragging, dropping and death-drop protection.
- Added persistent `arenas.yml` storage and a guided selection-wand workflow with readiness reporting and tab completion.
- Added versioned, backup-first config migration that only adds missing bundled settings and never overwrites arena data.
- Added `kits.yml` storage, `/kit` selection, and the Unranked lobby sword kit menu.
- Extended the non-destructive migrator to preserve custom kit files while adding future bundled kit defaults.
