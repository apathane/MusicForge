# Project Context

[← Vault index](README.md) · [Current state](STATE.md)

## Identity

**MusicForge** is a personal, local-first Android YouTube Music client with fast downloads and offline playback. Its guiding idea is a **private listening instrument**, not a generic feed wrapper. It uses real YouTube Music catalogue/account data plus the listener's own local history; it should not invent artists, albums, covers or recommendations to fill empty space.

- **Repository:** [apathane/MusicForge](https://github.com/apathane/MusicForge)
- **License:** GPL-3.0.
- **Package:** `dev.musicforge`; application id `dev.musicforge.app`; debug suffix `.debug`.
- **Stack:** Kotlin, Jetpack Compose, Android Media3/ExoPlayer, Room, Preferences DataStore, Coil, OkHttp, coroutines.
- **Minimum SDK:** Android 8.0 / API 26. **Compile SDK:** 37. **Target SDK:** 36.
- **JDK:** 21.
- **Target reference device:** Honor X7b, Snapdragon 680, Adreno 610, Android 14 / MagicOS, locale `en-ZA`.
- **License/dependency boundaries:** the app is GPL-3.0; vendored backdrop code has its own Apache-2.0 attribution. Keep upstream notices and `licenses/` intact.

## Version and phase: distinguish source from release

**Verified in source:** `app/build.gradle.kts` sets phase 14, codename Sonata, `versionCode = 1400`, and `versionName = 0.14.0-sonata`. `CHANGELOG.md` calls Sonata phase 14 / 0.14.0 in progress.

**Verified release metadata at initial vault creation:** latest non-prerelease GitHub release returned by the releases API was `v0.13.0-rondo`, published 2026-10-24. Therefore, main-branch code at phase 14 and the latest published stable release are not the same thing. Verify the release list before making a current release claim.

The repository's `main` HEAD reviewed on 2026-10-27 was `d4af468a44fffc7e5bb586ee45241bb0f4626fa8` (CI setup refactor, 2026-10-25). This vault and Quest Board are being authored on `docs/musicforge-quest-board`, branched from main. Re-check current HEAD in future sessions.

## Product promises and non-goals

### The product should

- Start quickly and remain useful offline.
- Download through a resumable Media3 pipeline.
- Treat local library data as valuable and preserve it during sync/restore failures.
- Keep personal Home shelves factual and explainable.
- Make source/quality/queue/player controls legible and predictable.
- Preserve backup compatibility with Metrolist and ConvX where intended.
- Stay performant on mid/low-range Android hardware.
- Have no ads, analytics, telemetry or trackers.
- Avoid requiring paid API keys or rooted devices.

### The product should not

- Fill Home with fake catalog items when history/library data is sparse.
- Treat a failed or partial remote sync as proof that remote items were deleted.
- write network or image work on the main thread.
- layer full-screen blur/live glass behind scrolling lists.
- hide data loss behind a successful-looking restore.
- break upstream table and DataStore key names casually.
- add a large new architecture, dependency injection framework, or a separate multi-platform UI stack without a deliberate project decision.

## Product features verified by source/docs

- YouTube Music search and browsing through the vendored `:innertube` module.
- Streaming and resumable download using bounded HTTP range requests where possible.
- Two cache domains: player LRU cache and persistent download cache.
- Download metadata stored in the Media3 request so missing DB rows can be repaired.
- Local library, likes, playlists, artists/albums, listening events and stats in Room.
- Signed-in library synchronization and remote listening-history merge.
- Cached timed/plain lyrics from LRCLIB, then YouTube Music fallback; lyric export.
- In-app five-band equalizer and loudness normalization; skip-silence setting.
- Persistent queue, read-ahead, radio/autoplay continuation, sleep timer, playback resumption.
- Android Auto / MediaLibraryService browsing, notification/system controls and home-screen widget.
- Optional ListenBrainz scrobbling with an offline queue.
- Local-first Home shelves, correction feedback, account feed, Taste screen, stats.
- Import/export backup, automatic daily safety backup, pre-restore snapshot.
- Artifact export to `Music/MusicForge`, including tags/cover/lyrics for supported formats.
- Liquid-glass implementation for constrained surfaces, plus solid/artwork fallback tiers.

A visible feature existing in source is not proof every path behaves correctly on all devices. Follow [State and priority](STATE.md), [Architecture](ARCHITECTURE.md) and [Tests](FILE-MAP.md).

## Design language

- Monochrome foundation; restrained accent sampled from the playing artwork.
- Typography and spacing create hierarchy. Use cards only for meaningful groupings.
- Artwork may tint the player, but title/artist/control contrast wins.
- Home sections should carry a reason/provenance and be based on actual data.
- Motion should feel physical but short; avoid perpetual decorative animation.
- Empty/loading/error states are part of the design, not placeholders to forget.
- UI copy is plain and warm. Buttons/settings stay literal; a small wink is allowed for error/empty text.
- Reference the design system in `ui/designsystem/Signal.kt`, `ui/theme/Theme.kt`, `ui/components/`, and [the personalized UI spec](../docs/PERSONALIZED-UI-SPEC.md).

## External inspirations and lineage

- **Metrolist:** InnerTube API models/client, account sync, download patterns, preference/table compatibility and history conventions.
- **ConvX:** screens/features, backup format compatibility, and design patterns that were adapted selectively.
- **BitChord:** bounded range transfer ideas, monochrome direction and artwork-led player ideas.
- **InnerTubeX:** stream extraction implementation, pinned to a specific version/commit via Gradle settings.
- **Kyant0/backdrop:** vendored glass renderer under `ui/backdrop/`, Apache-2.0 with attribution in `licenses/backdrop-Apache-2.0.txt`.

These are influences, not permission to transplant code without checking license, attribution, API fit, performance and MusicForge-specific invariants.

## Next phase proposal

The proposed Phase 15 direction is **Overture** (name/codename not yet approved). Read the [step-by-step roadmap](../docs/PHASE-15-OVERTURE-ROADMAP.md) and [reference audit](REFERENCE-AUDIT.md) before planning a large change. Complete the Sonata hand-off first; the proposal prioritizes player cohesion, lyric reliability/word timing and real Explore content. Local files and Smart Transitions are separate later-phase proposals.

## Primary documents

- [README](../README.md): user-facing features and Sonata roadmap.
- [CHANGELOG](../CHANGELOG.md): phase-level history.
- [CLAUDE.md](../CLAUDE.md): extensive architecture map and coding conventions.
- [AGENTS.md](../AGENTS.md): mandatory agent rules.
- [CONTRIBUTING](../CONTRIBUTING.md): build, branch and contribution policy.
- [Personalized UI spec](../docs/PERSONALIZED-UI-SPEC.md): product/design/implementation intent, originally drafted for an earlier phase.
- [Quest Board](../docs/QUEST-BOARD.md): ordered proposed work.
- [Reference audit](REFERENCE-AUDIT.md): what to learn from Convx, BitChord, Metrolist and ArchiveTune, with the no-contact boundary.
- [Phase 15 roadmap](../docs/PHASE-15-OVERTURE-ROADMAP.md): proposed scope and future-phase gates.
- [Benchmarks](../docs/BENCHMARKS.md): measured transfer and emulator rendering results.
