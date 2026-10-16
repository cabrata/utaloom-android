# Changelog

## [v1.3.0] - 2026-10-16

### Added
- **player:** Spotify Canvas backgrounds with automatic login (`3c464b0`)
- **canvas:** Unit tests for cookie extraction and Canvas URL matching (`e12a965`)

### Changed
- **playback:** Reduced streaming lag with next-track prefetching and progressive caching (`9fc42ef`)

### Fixed
- **canvas:** Repaired Spotify lookup and first-frame playback (`9a7c019`)
- **auth:** Restored Scaffold layout and settings for Spotify login (`6356ff7`)
- **playback:** Prevented wrong-thread access during look-ahead caching and fixed WebView login (`1f7441b`)
- **player:** Fixed blank WebView layouts and added manual cookie entry (`61a158d`)
- **player:** Fixed blank Spotify login and added a fallback when Canvas is unavailable (`ec7802f`)
- **lyrics:** Synchronized lyric animations and highlighting during cast playback (`37f542f`)

## [v1.2.0] - 2026-10-12

### Added
- **offline:** add download-only mode and download preferences (`3baa05c`)
- **queue:** save displayed queue as a local playlist (`6cdc52c`)
- **lyrics:** add free Google Translate provider on Android (`d4f8c67`)
- **changelog:** AI-generated changelog on release (`057b524`)

## [v1.1.1] - 2026-10-10

### Fixed
- **Import:** Background notifications that are disabled now show an explanation instead of being silently skipped. (c0a42f5)

## [v1.1.0] - 2026-10-10

### Added
- **Playlist import from links** — Add playlists straight from Spotify and YouTube Music URLs, no manual track entry required.
- **Import progress bar** — Link imports now display a progress bar that tracks the import as it runs.
- **Cancellable imports** — Link imports can be cancelled mid-flight if you change your mind or picked the wrong playlist.
- **Background importing** — Playlist imports now run in the background, so you can keep using the app while they finish, with progress reported through a notification.

## [v1.0.2] - 2026-10-10

### Added
- **Lyrics provider settings** — Added a desktop-style lyrics provider settings screen, giving you control over which sources are used when fetching lyrics. (`237263e`)
- **Smart shuffle** — The player now offers smart shuffle, which reorders upcoming tracks to avoid repetition and keep playback varied. (`88019f3`)
- **Launcher shortcuts** — Added launcher shortcuts for quick access to common player actions right from your home screen. (`88019f3`)

### Changed
- **Listen Together URL migration** — Migrated the old `metrolist.caliph.dev` Listen Together endpoint to `utaloom.caliph.dev`. Existing sessions will continue to work against the new host. (`0015cca`)

## [v1.0.1] - 2026-10-09

### Changed
- New app icon: the **Utaloom Woven U** logo replaces the previous Metrolist-derived icon. The artwork is now original to Utaloom, so it no longer carries the licensing and attribution baggage of the borrowed design.

## [v1.0.0] - 2026-10-09

### Added
- **listentogether:** Default to the `utaloom.caliph.dev` server, and ensure v1 + v2 release signing ([a810c37])
