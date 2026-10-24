# Changelog

All notable changes to MusicForge. Newest phase first.

---

## Sonata (phase 14) — *in progress*

Version 0.14.0, codename `Sonata`.

- Player sheet rewritten: codec pill, Up Next, volume slider, output device picker
- History that syncs: pull remote plays, merge on Home, push local plays (Laguna S 2.1)
- One RAM build folder per git worktree
- Settings button on every tab; version follows the phase
- Voice rules codified: why-comments may be funny, UI gets a little warmth
- `AGENTS.md` and `scripts/check.sh`: one entry point for every coding agent

---

## Rondo (phase 13) — 2026-10-24

Version 0.13.0, tag `v0.13.0-rondo`.

- Settings button on every tab (was only on Library)
- Version number follows the phase: 0.13.0 (was 0.1.0)
- Swipe mini player sideways to skip; swipe queue song away with Undo
- Haptic tick on long-press menus and queue drags; press feedback on cards and grid tiles
- Pages slide in, tabs crossfade; re-tap a tab to jump to its top
- Skeleton rows instead of spinners; pull-to-refresh on Home and Library
- Signed-in search runs as you (Pause search history in Settings)
- Paste a YouTube/YouTube Music link: songs play, playlists/albums/artists open
- Clear search field, focus on open, manage recent searches, fill-in arrows on suggestions
- Tap artist in player to open them; long titles scroll; long-press title to copy
- Artist pages: Play, Shuffle, Follow in header; expandable About section
- Pictures that stopped loading (old avatars, retired playlist covers) fetch a fresh link automatically
- Home hero stays readable over cover art with lettering
- Installs over earlier builds and keeps your library

---

## Gigue (phase 12) — 2026-10-24

Tag `gigue-build-2026-10-24` (pre-release), phase tag `phase-12-gigue`.

- Liquid glass for nav pill and mini player (blur, saturation, edge lens on Android 13+, specular rim). Default on Android 12+, toggle in Settings → Glass
- Google sign-in drives Home feed (`useLoginForBrowse`) and syncs account library: liked songs, saved songs, albums, artists, playlists. Sync now / Switch account / Sign out in Settings
- Pure black is actually black now, and turns on the dark theme
- Home hero card, bigger section titles and cards
- Downloads: reuse player cache, retry failed, upgrade low-quality copies
- Downloads: re-download in high quality (ConvX's quality upgrade)
- Downloads: copy what's already cached, forget failed URLs, retry failures
- Accessibility: 48 dp touch targets, named queue symbols, states in words
- Playlists say how they're used
- Queue: radio pick with no artist reads "Radio pick", not "Radio pick · "
- Baseline profile regenerated for the new Home
- Uninstall older MusicForge build signed with a different key first

---

## Fugue (phase 11)

Tag `phase-11-fugue`.

- Home: suggestions can be paused
- What shapes your Home: the personalisation laid open and undoable
- Playback: Autoplay can be turned off
- Player says where a song came from when you didn't just pick it
- Library: albums and artists as adaptive cover grids
- Library: find in your library, as you type, offline
- Queue: radio's picks say so
- README: what the new Home does
- Phase 11 review fixes

---

## Aria (phase 10)

Tag `phase-10-aria`.

- Home built from your own listening (not invented content)
- Home follows the clock, sleeps off screen, pages reliably (review fixes)
- Artwork: a cover that fails to load shows its letter, not a blank tile
- Home suggestions explain themselves and take correction (Not for me, Less of an artist)
- Home: two shelves of its own — Around this hour, Liked then forgotten
- Aria review fixes
- `CLAUDE.md`: commands, byte path, data and UI rules for future sessions
- README, benchmarks, ignore build symlinks
- Stop tracking build symlinks (outputs live in RAM, see `build.gradle.kts`)

---

## Nocturne (phase 9)

Tag `phase-9-nocturne`.

- Cut overdraw: no window background, placeholder only while loading, letterbox zoom without a layer
- Player: one full-screen draw instead of four (was four)
- Fix HTTP 400 on non-US phones (`hl=en-ZA`); export lyrics+cover; duplicate downloads share bytes; baseline profile module
- Home-screen player widget; playback resumption from saved queue
- Service review fixes: scrobble timestamp at first playback; loudness re-applied on resume; listener/callback cleanup
- Encore: remember the queue across restarts (paused, no data used until play)
- Lint clean: voice play-from-search, locale, nav API, explicit Media3 opt-ins
- Encore: built-in 5-band equalizer that works on every phone
- Polish: no misleading play counts on Recently played / Your listening rows
- Polish: status-bar scrim app-wide; settings back row; remove unbuilt 'Live' glass option
- Encore: ListenBrainz scrobbling
- Encore: sleep timer fades out (~10 s, ease-out) then restores volume; end-of-song stops at the boundary
- Encore: Android Auto / car / watch browsing (MediaLibraryService + BrowseTree)
- Encore: 'From YouTube Music' in Library when signed in (Liked music + account playlists)
- Encore: export mutex and stale-pending cleanup
- Encore: search your own library (works offline; downloads first)
- Encore: serialize library writes (FIFO); export checks for existing file before copying

---

## Reprise (phase 8)

Tag `phase-8-reprise`.

- Export: read cached bytes to the end; cap embedded lyrics (review triage)
- Fade artwork only when large
- Right-sized artwork for rows; no fade on small thumbnails
- Keep downloaded songs' format rows through a restore; build outputs in RAM; stable baseline-profile journey
- Download songs you like (Metrolist/ConvX setting, restored from backups)
- Bring back your downloads after a restore
- Zoom only 4:3 letterboxed YouTube thumbnails (review triage)
- Player: square corners at rest (nothing is painted behind it now)
- Lint: declare Media3 unstable API use in SharedKeys

---

*See [Releases](../../releases) for APKs. Each phase is tagged `phase-N-<codename>`; releases are tagged `v<version>-<codename>`.*