# Changelog

All notable changes to the GroupScape plugin are logged here, newest first.

## [1.7.2] - 2026-08-28

### Removed
- Removed farming patch and bird house timers tracking. It relied on reflecting into RuneLite's internal Time Tracking classes, which isn't something we can keep doing reliably.

## [1.7.1] - 2026-08-28

### Fixed
- The party overlay no longer gets stuck disconnected after the website has a brief outage or maintenance — it now notices and reconnects on its own instead of requiring you to restart RuneLite.
- Fixed another case of a member card's vitals bar briefly flashing over the inventory grid when a bag item changed.
