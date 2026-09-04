# PeakPvP

PeakPvP is a Paper 1.21.11 plugin for PeakMC's duels and kit PvP server.
PeakPvP now targets Paper 1.21.11 so the Spear Mace kit can use the native Netherite Spear. Install [Attribute Swap Fixer](https://modrinth.com/plugin/attribute-swap-fixer) beside PeakPvP to enable Paper attribute swapping for spears, maces, and axe shield stuns; its first installation requires two server restarts.

The first release creates an empty `pvp` world, places one bedrock spawn block at `0, 64, 0`, blocks player building, and disables PvP within a configurable 100-block radius of `0, 0`. It also includes Peak-style LuckPerms chat/tab formatting, mentions, a live sidebar, protected matchmaking/party/settings hotbar items, rotating announcements, `/spawn`, and `/peakpvp reload`.

## Admin arena setup

Operators with `peakpvp.admin.arena` (included in `peakpvp.admin`) can run `/peakpvp help` in game. The guided setup is:

`/peakpvp setup` opens the map setup GUI. Select a map to toggle individual kits, right-click a kit to restrict it to that map only, or reset the map to allow every kit. Restricting a kit to one map does not stop other kits from using that map.

1. `/peakpvp arena wand` gives a selection rod. Use `/peakpvp arena clear` to reset your temporary selection.
2. Left-click one corner and right-click the opposite corner, or use `/peakpvp arena pos1` and `/peakpvp arena pos2`.
3. Run `/peakpvp arena create <name>`.
4. Stand at each player starting point and run `/peakpvp arena setspawn <name> 1`, then `2`.

Use `/peakpvp arena list` to see which arenas are ready, `/peakpvp arena info <name>` to inspect one, and `/peakpvp arena delete <name>` to remove one. `/peakpvp area` is an alias for `/peakpvp arena`. Data is saved in `plugins/PeakPvP/arenas.yml`.

When the plugin is updated, missing settings are added automatically. Existing values are never replaced, and each upgrade creates a timestamped `config.yml.backup-*` before writing. Arena data in `arenas.yml` is untouched.

The only initial kit is Unranked NoDebuff. Right-click the Unranked lobby sword or use `/kit` to open the kit menu, then select NoDebuff. `/kit nodebuff` equips it directly. Its saved inventory is in `plugins/PeakPvP/kits.yml`, which also receives safe missing-setting updates and timestamped backups.

## Changelog webhook

Add a GitHub Actions repository secret named `PEAKPVP_UPDATE_WEBHOOK` containing a Discord webhook URL. On each push, the workflow builds the plugin and sends newly added `PUBLIC_CHANGELOG.md` bullet points to Discord.

## Build

Use Java 21 and Gradle 9.2.1:

```text
gradle clean build
```

The plugin jar is written to `build/libs/PeakPvP-0.1.0.jar`.
