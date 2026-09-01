## PeakPvP 0.1.0

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
