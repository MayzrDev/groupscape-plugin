# Changelog

All notable changes to the GroupScape plugin are logged here, newest first.

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
