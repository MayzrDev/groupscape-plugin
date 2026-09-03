# Changelog

All notable changes to the GroupScape plugin are logged here, newest first.

## [1.8.10] - 2026-09-03

### Added
- New party overlay Layout option: Orb Grid, which shows each group member as a circular HP orb (with Prayer/Run/Spec pips and a target indicator) tiled in a grid instead of a list of bars. The panel now sizes itself to however many members you have instead of always taking up a fixed width.
- New party overlay Layout option: Scoreboard, which lays members out as side-by-side vertical HP meters (like a raid frame turned sideways) instead of a list - handy for a wide, short strip along the top of the screen during boss fights.

### Changed
- The party overlay's "Scale" setting is now called "Layout", since most of its options change the whole arrangement, not just the size. Your existing choice is preserved.
- Orb Grid: bolder, better-spaced Prayer/Run/Spec pips around each orb.
- Scoreboard: sized up about a third, with a larger, bolder name label.

### Fixed
- A newly-assigned slayer boss task (Leviathan, Whisperer, Vardorvis, Duke Sucellus, etc.) could get permanently stuck showing as "Unknown task" in the group panel/side panel instead of the boss's name.
- Orb Grid's Prayer/Run/Spec pips no longer spill outside the panel's bottom edge - padding and row spacing now account for how far the bigger pips hang off each orb.
- Scoreboard's member initials now actually render bold - the font-weight flag alone wasn't visibly bolding the game's pixel font.
- New collection log items unlocked in the middle of a session (especially untradeable ones) could sometimes fail to sync to the Activity Feed until you next opened your Collection Log - the plugin now identifies the unlocked item directly from your inventory instead of relying on a Grand Exchange price lookup that doesn't cover every item.
- Party overlay text (member names, bar labels/values) no longer overlaps itself in Compact and Super Compact scale - font size and text position now scale properly with the tighter row heights instead of overflowing them.
- Talking to Mortimer, Aya, Achtryn, or Kuradal no longer fails to update your tracked slayer master - the plugin wasn't recognizing them as slayer masters yet.

## [1.8.9] - 2026-09-03

### Added
- Notable drop pings sent to Discord now show the item's icon.

### Fixed
- Drops received as a "noted" stack (e.g. a bulk pile of gems) no longer show up in the Activity Feed and Discord as an unrecognized "item #____ (untradeable)" - they now show the correct item name, link, and gp value just like the unnoted version.

## [1.8.8] - 2026-09-03

### Fixed
- Deaths to bosses (e.g. Leviathan) that hit you with a ranged/special attack you weren't actively fighting back against now correctly show "died to <boss>" instead of just "died" - killer attribution now uses whichever NPC last landed a hit on you, not just whoever you were targeting.
- Checking your slayer task (helm, enchanted gem, black mask, or with your slayer master) could sometimes show "null" for the task name on the site while the kill count stayed correct - the task name and location no longer get wiped out by a momentary lookup hiccup.

## [1.8.7] - 2026-09-03

### Fixed
- Combat achievement progress no longer waits up to a minute (or a relog) to sync to the site - it now updates the moment you complete a task or tier, and also refreshes whenever you open your Combat Achievements log.
- Your slayer task's assigning master no longer gets stuck showing as unknown after a client restart mid-task - talking to (or rechecking with) a slayer master now refreshes it right away.
- Boss slayer tasks (Leviathan, Whisperer, Vardorvis, Duke Sucellus) now show the actual boss name to your group instead of just "Boss".
- Collection log items whose name contains an apostrophe (e.g. Beekeeper's gloves) could fail to sync the moment you got them and only show up after you next opened your collection log - fixed a mismatched apostrophe character breaking the item name lookup.

## [1.8.6] - 2026-09-02

### Added
- Your current slayer task, master, points, and streak are now reported to your group, so the website can show them in each member's side panel.

### Fixed
- Fixed the Corrupted/Crystalline Hunllef never showing up as a kill in the Activity Feed, and the Gauntlet reward chest loot never showing in the Loot Log - the Hunllef's health bar never reliably signals its death, so the kill is now detected from the "Gauntlet completion count" chat message instead, and correctly paired with the reward chest's loot whenever you get around to opening it.
- Fixed your own row in the party overlay always showing a gold accent bar instead of your assigned helmet colour, even though every other group member's row already showed theirs correctly.
- Fixed Tombs of Amascut (and occasionally Chambers of Xeric) completions sometimes not appearing in the Activity Feed at all - the reward chest loot is now enough on its own to log the completion, instead of requiring an in-game chat message to line up with it first.
- Internal: confirmed raid completion chat detection wording against real completion messages and removed a stale internal note about it being unverified.
- Fixed collection log unlocks not syncing to your group until you next opened your collection log - new items now sync as soon as you get them.
- Fixed deaths (and any kill/loot alongside them) sometimes not appearing in the Activity Feed, toasts, or Discord at all - a too-strict check meant to filter out spurious death animations could also misfire on real deaths.

## [1.8.5] - 2026-09-01

### Added
- Finishing a raid (Chambers of Xeric, Theatre of Blood, or Tombs of Amascut) now reports it to your group's Activity Feed, along with the difficulty and how much the reward chest was worth.

### Fixed
- Discord boss kill notifications now show your account's real kill count (read from the "kill count is" chat message) instead of a count of kills GroupScape happened to see logged on its own server.
- Fixed loot going missing from the Activity Feed (and its Discord notification) for bosses that split their drop across more than one loot roll per kill, such as Vet'ion - only the first roll was being kept before.
- Fixed kills, loot, and deaths sometimes appearing twice in the Activity Feed and Discord (with a duplicated kill count / loot value) right around a GroupScape server restart.

## [1.8.4] - 2026-08-31

### Added
- Raid markers now include four numbered (1-4) and four lettered (A-D) generic callouts, alongside Danger/Defend/Loot/Focus.
- You can hide raid marker types you don't use from your own "Raid Markers" menu, in three new settings sections - this only tidies your menu, it doesn't hide other members' markers.

### Changed
- Renamed the "Safe Spot" raid marker to "Defend".
- Chat messages from GroupScape (group notifications, pings, kill/loot lines) are now prefixed with "[gs]" and shown in purple, so they're easier to spot in a busy chat window.
- "Ping" and "Raid Markers" now only show up in the right-click menu when you shift-right-click, so a plain right-click stays uncluttered.
- Raid markers in the game world are now semi-transparent, so they don't fully block your view of what's underneath them.

### Removed
- The hold-to-ping hotkey (Z by default) is gone - use shift-right-click instead.

### Fixed
- Boss kills are detected more reliably - some deaths were missed when the health bar hid itself before the kill could be confirmed.
- Shift-right-click now actually shows the "Ping" and "Raid Markers" menu entries - they were silently never appearing due to a wrong key check.
- The "Raid Markers" submenu now lists top to bottom in the order Danger/Defend/Loot/Focus, then A-D, then 1-4 - it was showing bottom to top before.
- Fixed a false "died" report showing up on the group's activity feed when you hadn't actually died.

## [1.8.3] - 2026-08-31

### Changed
- Ping and raid marker beams (the optional vertical line under a marker) are no longer a separate setting - a tile ping/marker now always shows its beam, and an NPC ping/marker never does.

## [1.8.2] - 2026-08-31

### Fixed
- Fixed the hold-to-ping hotkey still not working - it now cancels the walk/attack the same way RuneLite's own Party plugin does, instead of a method that never actually suppressed it.

## [1.8.1] - 2026-08-31

### Fixed
- Loot log now tracks kills from every monster, not just a short list of bosses - previously it silently ignored loot from anything not on that list, which was most normal play.
- Fixed a timing issue where loot could sometimes go untracked even for a monster it was watching, if the loot dropped a moment before the kill was detected.

## [1.8.0] - 2026-08-31

### Added
- New "Raid Markers" feature (off by default - enable it in the settings). Right-click a tile or NPC to drop a Danger, Safe Spot, Loot, or Focus/Kill Target marker for your group to see - each shows its own icon and stays up until you clear/redrop it or the target dies, no timer. You can have one of each type active at a time, on a tile and on an NPC.
- Pings and raid markers can now optionally show a vertical beam under the marker (off by default), similar to the Ground Items plugin's loot beam - toggle "Show ping beams" and "Show raid marker beams" separately in the settings.

## [1.7.11] - 2026-08-31

### Fixed
- Fixed the hold-to-ping hotkey not working at all - clicking while holding the key could cause the game to briefly steal keyboard focus, making the plugin think the key had already been released.
- Fixed "Clear ping" (and pings ending on their own) silently failing every time.

### Changed
- You can now have one pinged monster and one pinged tile active at the same time - pinging a new tile no longer clears an in-progress monster ping, and vice versa. Right-click now shows separate "Clear NPC ping" and "Clear tile ping" options when applicable.

## [1.7.10] - 2026-08-31

### Fixed
- Fixed a pinged monster's marker not showing at all - a wrong parameter to a game math function meant the floating arrow and beam kept silently failing to draw the whole time a ping was active.

## [1.7.9] - 2026-08-31

### Fixed
- A pinged monster now shows an outline around its actual tile instead of its 3D model, and its floating arrow marker shows reliably above it.
- The hold-Z ping shortcut and the right-click "Ping"/"Clear ping" options are now more reliable, and won't silently fail without at least logging what went wrong.

## [1.7.8] - 2026-08-31

### Added
- You can now ping a spot or a monster for your group to see - right-click an NPC or a tile (in the game view or the world map) and choose "Ping", or hold Z and left-click in the game view for a quicker shortcut. Pings show as a colored arrow marker on the minimap, world map, and the group's website map, and a pinged monster's marker follows it around live. Pings clear themselves after about a minute, or sooner if the monster dies or you choose "Clear ping" from any right-click menu.

## [1.7.7] - 2026-08-31

### Fixed
- Fixed group members intermittently disappearing from the party overlay and side panel (showing as offline) even though they were online — the "offline" cutoff was set equal to the idle heartbeat interval, leaving no room for normal network delay.

## [1.7.6] - 2026-08-31

### Added
- Group members now show up on the world map and minimap while they're in your world and on your plane, marked with the same colored helm icon as the party overlay. On the world map, a member who's off-screen shows as an arrow at the map edge you can click to jump straight to them - just like clue and quest markers.
- Added a "Map markers" toggle to turn this off.

## [1.7.5] - 2026-08-31

### Changed
- Internal: fixed how your location data gets saved so it stays consistent between sessions.

### Removed
- Removed the "Pause sync" setting; use the plugin toggle if you want to stop sending data.

## [1.7.4] - 2026-08-30

### Changed
- Tightened up spacing in the side panel's inventory, equipment, and skills tabs so more fits without feeling cramped, and centered the equipment doll within the panel.
- Fixed the roster list sometimes getting clipped on the right edge instead of matching the padding on the left.

## [1.7.3] - 2026-08-29

### Fixed
- Fixed you sometimes staying stuck showing "offline" to your group after standing idle for a while, even though the plugin was still running - it now sends a periodic check-in even when nothing's changed.

## [1.7.2] - 2026-08-28

### Removed
- Removed farming patch and bird house timers tracking. It relied on reflecting into RuneLite's internal Time Tracking classes, which isn't something we can keep doing reliably.

## [1.7.1] - 2026-08-28

### Fixed
- The party overlay no longer gets stuck disconnected after the website has a brief outage or maintenance — it now notices and reconnects on its own instead of requiring you to restart RuneLite.
- Fixed another case of a member card's vitals bar briefly flashing over the inventory grid when a bag item changed.
