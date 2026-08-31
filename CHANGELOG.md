# Changelog

All notable changes to the GroupScape plugin are logged here, newest first.

## [1.7.7] - 2026-08-31

### Fixed
- Fixed group members intermittently disappearing from the party overlay and side panel (showing as offline) even though they were online — the "offline" cutoff was set equal to the idle heartbeat interval, leaving no room for normal network delay.

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
