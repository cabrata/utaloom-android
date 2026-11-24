# Changelog

## [v1.7.0] - 2026-11-24

### Added
- **encore:** glass stories with best-part clips and story strings (`8ed16a4`)
- **settings:** BitChord audio quality and player controls (`9673309`)
- **player:** output button icon follows active output (`6392269`)
- **player:** audio output drawer, M3 output sheet, fuller Audio Pipeline (`af34c6a`)
- **i18n:** translate all remaining strings to every locale (`eb256b5`)
- **ui:** Account & integrations page and BitChord-style library (`ad86f86`)
- **lyrics:** RTL lyric line support (`5b4182c`)
- **ui:** Utaloom Glass app style with liquid glass chrome (`d262a82`)
- **player:** audio pipeline info, addon quality upgrade, lyric clock (`a0fb7ca`)

### Changed
- **ui:** account switcher card calmed to BitChord frost (`f7f437f`)
- **encore:** rename Wrapped feature to Encore (`3594de0`)

### Fixed
- **i18n:** move hardcoded strings to resources, complete translations (`42a8a43`)
- **playback:** init player when service created by background bind (`892adc6`)
- **ui:** Utaloom Glass fixes for charts artwork, lens pill, status bar, artist header, search (`86d9d1e`)

## [v1.6.0] - 2026-11-15

### Added
- **i18n:** Translate Wrapped recap strings to all locales (`6196f65`)
- **ui:** Add floating pill navbar, brand top bar and navigation drawer (`bd2d250`)

### Fixed
- **wrapped:** Use Utaloom palette, condensed display type and new home banner (`a79ada5`)

## [v1.5.0] - 2026-11-15

### Added
- **wrapped:** Rebuild Wrapped as Spotify-style stories for any year (`b16606a`)
- **lyrics:** Add lrc.red provider as default first source (`1d0fc2b`)

### Changed
- **playback:** Fall back to YouTube when add-on stream fails (`6cb3349`)
- **scrobble:** Queue offline Last.fm scrobbles for later retry (`440c201`)

## [v1.4.0] - 2026-11-15

### Added
- **player:** Label add-on streams with reported quality and measured format (`8701c54`)
- **player:** Add audio quality badge with source details (`361f0c7`)
- **ui:** Show add-on quality picker, status and info in music sources (`d37f7e5`)
- **playback:** Add BitChord-compatible music source add-ons (`b32bd7a`)
- **ui:** Add Utaloom brand accents across player and theme (`cf88558`)
- **lyrics:** Add Utaloom woven thread share cards (`61e0142`)

### Fixed
- **playback:** Restore add-on stream functions misplaced in MusicService (`2ce7f5a`)

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
