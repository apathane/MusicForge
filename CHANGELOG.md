# Changelog

All notable changes to MusicForge. Newest phase first.

---

## Toccata (phase 15)

Version 0.15.0, codename `Toccata`. Plan: `.agent/decisions/0001-phase-15-toccata.md`.

### P2 · 0.15.0

Tag: [`p2-0.15.0`](../../releases/tag/p2-0.15.0) · Version code `1502` · 2026-10-29

Pixel fixes from an emulator walk-through (1080×2412, 400 dpi, Android 14): full-bleed cover under
the status bar, one continuous cover-to-canvas gradient, visible volume slider, "This phone" for the
built-in speaker, Material lock glyphs, faded title edge; stable LazyColumn keys (no ghost rows),
distinct Library icons, idle pull-to-refresh hidden, per-collection empty copy, artist portraits
looked up through `ArtRepair`; queue drag index fix and end-of-song sleep unarmed on `STATE_ENDED`
(from the pre-release review).

### P1 · 0.15.0

Tag: [`p1-0.15.0`](../../releases/tag/p1-0.15.0) · Version code `1501` · 2026-10-29

- Motion: NavHost slide/fade with predictive back, press feedback, list item animation, icon crossfades
- Player: safe-drawing insets, cover fades into the ambience canvas, "Hide top bar"
- Radio: "Start radio" plays the song alone and lets endless play build the station
- Loading: `LoadingBar` (draw-phase, 150 ms gate) on every real loading state
- Awareness: one `StateFlow` of network / battery saver / storage / failing streams; one line on Home
- Home: hidden and reordered shelves (`forge.homeOrder`, `forge.homeHidden`), artist portraits, "Your week" with streak
- Settings: start screen (`defaultOpenTab`, Metrolist values accepted), keep screen on, haptics, double-tap seek, swipe-to-skip, cache clears, lyrics defaults
- Updater: reuses a cached APK whose SHA-256 still matches
- Performance: DataStore and file I/O off the main thread at startup and in Settings; software bitmaps only where pixels are read

## Sonata (phase 14)

Version 0.14.0, codename `Sonata`.

### 0.14.0 — *S0nata*

Tag: [`v0.14.0`](../../releases/tag/v0.14.0) · Version code `1409` · 2026-10-29

The first regular Sonata release: everything in previews 1–4, plus:

- Accessibility: the seek bar exposes slider semantics, so TalkBack can read and move it
- Queue: rows keep their keys across queue changes (a swipe in progress is no longer reset)
- Sign-in: account info is read off the main thread
- Artwork: resizing a Google cover URL keeps any query string after the size token
- Updater: asset names stripped of paths, previous update APKs cleared, progress emitted per whole percent
- Releases: one workflow publishes whatever version `app/build.gradle.kts` declares, with notes from `.github/release-notes.md`
- Repo: committed APKs and an unused backup-rules file removed

### P4 · 0.14.0 — *The Updater Has Entered the Chat*

Tag: [`p4-0.14.0`](../../releases/tag/p4-0.14.0) · Version code `1404` · Published 2026-10-28

[Download the APK](../../releases/download/p4-0.14.0/MusicForge-p4-0.14.0.apk) · [SHA-256 manifest](../../releases/download/p4-0.14.0/SHA256SUMS.txt) · [Release workflow](../../actions/runs/40769284957)

- In-app updater: daily background check, manual check, preview opt-in, download progress, and Android installer handoff
- Update integrity: require a valid SHA-256 manifest entry before installing; fail closed when the manifest or entry is missing
- Release selection: use the explicit Android versionCode in release notes so a future stable release can supersede preview builds
- Reliability: failed automatic checks do not suppress retries for the next 24 hours
- Installer wiring: declare FileProvider paths metadata and increment the app to version code 1404
- Preview 3 fixes: sign-in confirmation, MagicOS output-device fallback, safer volume control, and artwork URL repairs

### v0.14.0-preview.2 — 2026-10-28

Tag `v0.14.0-preview.2`.

- Player cover art is full-bleed: edge to edge across the top, fading into black
- Like and more buttons have room to breathe
- Accessibility & touch targets: 48 dp targets in queue (lock, row menu, drag handle, Undo, Clear) and mini player; TalkBack cover art description and queue end notice
- Active song marked with a glyph in the queue, not just a tint

### v0.14.0-preview.1 — 2026-10-28

Tag `v0.14.0-preview.1`.

- Permanent release key: signed with one key so future updates install directly (one-time reinstall and backup restore if upgrading from older builds)
- Account history sync: two-way sync with YouTube Music account
- Player sheet rewritten: codec pill, Up Next, volume slider, output device selector
- Icon fixes: volume and headphone icons redrawn properly
- Downloads: notification with progress display and Cancel all action
- Lyrics: fixed LRC parsing and LRCLIB matching
- Settings button on every tab; version follows the phase
- Network security: HTTPS only, system certificates
- Engineering: one RAM build folder per git worktree; `AGENTS.md` and `scripts/check.sh` unified entry points

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

*See [Releases](../../releases) for APKs. Previews use `pN-0.x.y` tags (for example, `p4-0.14.0`). Small fixes increment the patch version (`0.14.1`, `0.14.2`) and use a short, code-appropriate subtitle. Android version codes keep previews ordered before the completed patch release.*