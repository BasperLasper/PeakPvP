## PeakPvP 0.1.0

- Added random compatible-map selection and an admin `/peakpvp setup` GUI for controlling which kits can use each arena.
- Arena maps are protected from block breaking, fire, TNT, TNT minecarts, and every other explosion while combat damage and knockback remain active.
- Arena cleanup now restores changes and removes all water, lava, and waterlogged states after every match.
- Added Spear Mace, Cart, and OP Cart kits, expanded the kit menus, and changed existing drinkable kit potions to splash potions.
- TNT minecarts now detonate when struck with a sword without damaging the arena.
- Added a lobby Quick Join compass that waits for the first available 1.8 or Latest kit selected by another player.
- Added a five-block spawn return zone that removes kits, leaves matchmaking, and returns players safely to spawn.
- Updated to Paper 1.21.11 for Netherite Spear support and added Attribute Swap Fixer support for spear, mace, and axe mechanics.

- PeakPvP update: two ranked ladders are now live—Ranked 1.8 with 1.8 ELO and Ranked Latest with Latest ELO—plus editable kit layouts, Ranked Quick Match, party queueing for both modes, improved scoreboard stats, and unlimited-hit Combo combat.
- PeakPvP now has separate Ranked 1.8 PvP and Ranked Latest PvP queues.
- Added independent 1.8 ELO and Latest ELO ratings with clear match-result messages.
- Added personal kit layout editing and copying layouts from Ranked 1.8 to Ranked Latest.
- Added Quick Match to the Ranked Latest menu and party queue support for both ranked ladders.
- Updated lobby items, kit menus and the scoreboard to clearly show PvP systems, kills, deaths and both ELO ratings.
- Preserved unlimited-hit combat for the Combo kit.
- Added `/seen <player>` to check whether a player is online and when they were last seen.
- Added a void PvP world with a single bedrock spawn block at 0, 64, 0.
- Added world block protection and a configurable 100-block no-PvP spawn radius.
- Added Peak-style LuckPerms chat formatting, mentions and a live tablist.
- Added configurable rotating PvP announcements.
- Added a Discord changelog webhook workflow using the `PEAKPVP_UPDATE_WEBHOOK` repository secret.
- Added a live sidebar scoreboard showing population, ping, TPS and the player's current PvP status.
- Added protected lobby hotbar items for Unranked, Ranked, parties, the Kit Editor and Settings.
- Added guided admin commands for selecting, creating, inspecting and configuring duel arenas.
- Added safe automatic config updates that back up older configs and preserve all existing settings and data.
- Added the first Unranked NoDebuff kit with the supplied inventory, potions and diamond armour.
- Added backup-first updates for the kit data file as new kit settings are released.
