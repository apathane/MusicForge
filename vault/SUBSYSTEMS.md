# Subsystem Guide

[← Vault index](README.md) · [Architecture](ARCHITECTURE.md) · [Source map](FILE-MAP.md)

This page answers “where should I look?” It is not a complete line-by-line code commentary. Start at the named entry point, follow the call path, and read tests before changing behavior.

## App lifecycle and dependency wiring

**Entry points:** `app/src/main/kotlin/dev/musicforge/ForgeApp.kt`, `MainActivity.kt`, `AppGraph.kt`.

- `ForgeApp` initializes Timber in debug, sets up the image loader, creates the graph, starts the YouTube session and schedules account sync / safety backup work.
- `MainActivity` hosts the Compose UI and triggers lifecycle-linked work such as history freshness checks.
- `AppGraph` is the manual singleton/dependency graph. Prefer adding dependencies here instead of introducing a DI framework. It owns shared DB, DataStore, networking, playback, lyrics, exports, account sync and ambience objects.

## Playback service and player façade

**Entry point:** `playback/PlaybackService.kt`. **UI façade:** `playback/PlayerConnection.kt`.

- `PlaybackService` owns ExoPlayer and the MediaLibrarySession, making playback independent of the screen's composition lifecycle. It handles audio focus/noisy-device behavior, notification/system controls, queue resumption, periodic play-time checkpoints, autoplay/radio continuation, audio processing and scrobbling/history push paths.
- `PlayerConnection` constructs a MediaController and exposes flows for now-playing, queue, current index and sleep timer; UI actions go through this class.
- `MediaItems.kt` converts provider and Room models into Media3 items, preserving artist credits and duration in extras. Its current “from” provenance marker is in-memory only; `QueueStore` persists IDs rather than all metadata.
- `QueueStore.kt` saves up to 200 song IDs, active index and position to an atomic JSON file, then restores from Room paused.
- `PlayRecorder.kt` stores song/artist rows and play events. Play eligibility is 30 seconds or half of a known-duration track, whichever comes first.
- `ReadAhead.kt` reads following tracks into the player LRU cache under a network/battery budget.
- `AudioQuality.kt`, `Equalizer.kt`, and `EqualizerProcessor.kt` define audio selection and a five-band DSP equalizer embedded in Media3's audio pipeline.

**When debugging playback:** follow `PlayerConnection → PlaybackService → MediaEngine → StreamResolver → InnerTubeXPlayer → ChunkedDataSource`, and inspect queue/caching tests before changing stream IDs, cache keys or resolver behavior.

## Stream extraction and network session

**Main files:** `playback/YouTubeSession.kt`, `extraction/InnerTubeXPlayer.kt`, `extraction/potoken/`, `innertube/`.

- `YouTubeSession` applies country/language, cookie, visitor data, account identifiers and proxy settings to the provider clients. YouTube's supported `hl` list matters: locale tags such as `en-ZA` have caused HTTP 400s, so language is normalized.
- `InnerTubeXPlayer` is the app's stream-extraction entry point and wraps the InnerTubeX library. It handles extraction bundle lifecycle, client failures and stream-client retry exclusions.
- Po-token generation/WebView support is under `extraction/potoken/` and uses a bundled HTML asset; treat the asset and token flow as security-sensitive.
- `:innertube` contains API calls/models/pages for YouTube Music data (search, browse, playlists, accounts, charts, history, queue and playback registration). It is separate from stream extraction.
- Do not substitute a random extractor or add a second route around these modules without a measured, deliberate reason.

## Byte path, cache and downloads

**Main files:** `playback/MediaEngine.kt`, `StreamResolver.kt`, `ChunkedDataSource.kt`, `download/Downloads.kt`, `ForgeDownloadService.kt`.

- `MediaEngine` owns two different Media3 caches: the long-lived download cache, and the LRU player cache. Playback's data-source chain can read downloaded bytes without writing into that cache.
- `StreamResolver` obtains a usable URL, headers, format, length and chunk policy. It expires cached URLs, rejects stale concurrent resolutions with a generation counter and persists format metadata.
- `ChunkedDataSource` provides a continuous Media3 source over bounded HTTP byte ranges. Unknown lengths pass through; short streams must fail rather than be marked complete.
- `Downloads` orchestrates Media3's persistent DownloadManager/index, bounds preparation work, reconciles Room flags with durable bytes, and recreates missing rows using request metadata.
- `ForgeDownloadService` hosts the platform DownloadService.
- `Duplicates.kt` identifies same-recording downloads by normalized title/first artist and a two-second duration tolerance. `SharedKeys.kt` keeps shared bytes until the last dependent download goes away.
- `Upgrades.kt` models a higher-quality copy under `<id>#hq`; do not treat that key as a separate song.
- `Covers.kt` and download artwork prefetching support image cache behavior. `Redownloads.kt` offers to restore missing bytes after a library backup restore.
- `DownloadMeta.kt` serializes enough metadata into a download request to repair a missing Room row after restart/restore.

This path has important measured invariants. See [Architecture](ARCHITECTURE.md#playback-and-download-byte-path), [Benchmarks](../docs/BENCHMARKS.md) and [engineering decisions](DECISIONS.md).

## Database, preferences, and user data

**Main files:** `data/db/Entities.kt`, `MusicDao.kt`, `MusicDatabase.kt`, `data/prefs/Prefs.kt`.

- Room's main tables retain upstream-compatible names/layouts. The current Room version is 3, including the app-owned recommendation feedback and remote history tables.
- `MusicDao` is the main query/write surface. Read the query and its consumers together; Room flows feed the library, Home and multiple screens.
- `Prefs.kt` declares the preferences DataStore and all settings keys. The `Keys.importable` map is important for settings imported from Metrolist/ConvX.
- Any schema change needs a Room migration / generated schema and a migration test. Any cross-compatible setting needs a deliberate default and, when applicable, inclusion in `Keys.importable`.
- Never put actual DBs, backups or preferences files into Git. They can contain identifying library data or account state.

## Account sync and library actions

**Main files:** `data/AccountSync.kt`, `data/LibraryActions.kt`, `playback/YouTubeSession.kt`.

- `AccountSync` is signed-in only. It mirrors liked/saved songs, albums, artists and playlists into Room and has a separate remote listening-history path.
- Sync uses snapshots of the last complete remote list to decide which remote removals can remove mirrored local flags. A failed or partial result must not wipe a library.
- `LibraryActions` is local-first and sends eligible likes/playlist edits remotely when signed in; writes are serialized to avoid reordering fast edits.
- Sign-out removes account credentials and sync snapshots without deleting the local library. Account switching is a destructive-risk boundary; keep those semantics and tests explicit.

## Lyrics

**Main files:** `data/lyrics/Lyrics.kt`, `ui/player/PlayerPanes.kt`, `download/MusicExport.kt`.

- The domain model is `Lyrics.Synced(lines)`, `Lyrics.Plain(text)` or `Lyrics.None`.
- Existing order: Room cache → LRCLIB exact lookup → LRCLIB search fallback selecting closest duration → YouTube Music plain lyric fallback.
- `Lrc.parse` reads timestamps and multiple timestamps on one line, then orders lines by time. Formatting emits ordinary LRC.
- The player shows a loading state, a missing state, plain scrollable text or active-line-following synced text. Synced line taps seek the player; touching the list pauses autoscroll briefly.
- Provider metadata is saved in the upstream-compatible `lyrics.provider` field.
- Export may fetch lyrics with a bounded timeout, embeds them when supported/within size constraints and may create a sidecar LRC.
- There is currently no dedicated app-side lyrics parser test file in the discovered test tree. Q1 on the Quest Board is deliberately reliability/test first; word-level karaoke and TTML are later work.

## Backups and exports

**Main files:** `data/backup/AutoBackup.kt`, `BackupExporter.kt`, `BackupImporter.kt`, `PreferencesProto.kt`, `download/MusicExport.kt`, `OggTags.kt`.

- App backup is a zip that contains a database snapshot, optional preferences proto and a MusicForge manifest. It is not an exact image of download-cache bytes.
- Import is staged into temporary storage, verifies the DB, copies table/column intersections and imports supported preference keys. It protects against obvious zip path tricks by accepting only a strict set of flat entry names.
- A safety snapshot is taken before a nonempty library is replaced. Seven daily copies and three pre-restore snapshots are retained.
- Download bytes survive separately, and the restore flow can offer to redownload tracks that were marked downloaded in the old backup but whose bytes are not held now.
- The custom PreferencesProto implementation reads/writes the DataStore protobuf without adding a protobuf dependency; malformed/truncated wire data deserves tests.
- Export to the user Music folder is explicitly initiated / setting-controlled. It reads complete cached bytes rather than starting a surprise network download.

## Home, recommendations and remote feed

**Main files:** `recommendation/Home.kt`, `HomeRepository.kt`, `ui/home/HomeViewModel.kt`, `HomeScreen.kt`, `ui/screens/TasteScreen.kt`.

- `HomeRepository` collects Room flows for recent plays, repeated songs, downloads, playlists, listening time, time-of-day patterns, forgotten likes and correction feedback.
- `mergeRecent` merges local events and remote synced history, deduplicating by song ID and preserving a local play when timestamps tie.
- `HomeComposer` is deterministic/pure. It produces sections only when candidates exist, caps how much of one artist appears per shelf and applies hide/less-of feedback.
- `HomeViewModel` holds screen state and separately fetches/paginates the official YouTube Music feed. Account content must remain labeled and local shelves should remain useful offline.
- `TasteScreen` exposes patterns/reasons and allows individual corrections to be undone/reset.
- Recommendation behavior is deliberately explainable and user-correctable, not a remote black box.

## UI and navigation

**Root:** `ui/ForgeRoot.kt`, `ui/Routes.kt`, `ui/theme/Theme.kt`, `ui/designsystem/Signal.kt`.

Four top-level tabs are defined now: **Home, Search, Library, Downloads**. Route destinations also include collections, artist, album/artist library lists, login, settings, stats, equalizer, Taste and About. Explore appears in the roadmap but is not yet a top-level `Tab` in the reviewed source.

- `ForgeRoot` owns the single NavHost, mini-player/navigation pill and the modal player overlay.
- `PlayerSheet` displays artwork, synced/plain lyrics or queue panes; the queue UI supports jumping/reordering/removal.
- `Signal.kt` provides design tokens/components for spacing, typography and surfaces. `Theme.kt` defines custom Inter typography and monochrome light/dark/pure-black palettes.
- `Glass.kt` supports solid, artwork-derived and liquid tiers. The project intentionally restricts live glass to the nav pill and mini player on performance grounds.
- `ArtworkAmbience.kt` derives the current artwork's palette and one 54×120 blurred backing image per current artwork URL for reuse.
- Shared small UI parts live in `ui/components/` (artwork/song rows, shelves, skeletons, buttons, actions).
- `YouTubeLink.kt` parses pasted YouTube or YouTube Music URLs to route/play songs, playlists, albums or artists.
- Individual screen code is under `ui/screens/`; several screens are composable-led while more stateful screens have ViewModels.

## Optional integrations

- `ListenBrainz.kt` is opt-in and token-authenticated. It can submit “playing now” and scrobbles and queue unsent listens offline. This is an explicit external service beyond YouTube and LRCLIB; keep settings/consent and documentation aligned.
- Android Auto browsing goes through `PlaybackService` plus `BrowseTree.kt`.
- The home-screen widget is under `widget/PlayerWidget.kt`.
- Performance journeys live in `:baselineprofile`, with generated baseline profile text under `app/src/release/generated/baselineProfiles/`.

## Where to start for common work

- Lyrics bug: read `Lyrics.kt`, `PlayerPanes.kt`, `MusicExport.kt`, then add unit tests.
- Stream or resume bug: read `MediaEngine.kt`, `StreamResolver.kt`, `ChunkedDataSource.kt`, `Downloads.kt`, plus the tests for the changed path.
- Sync or missing-library bug: read `AccountSync.kt`, `LibraryActions.kt`, `MusicDao.kt` and backup restore tests.
- UI polish: read `ForgeRoot.kt`, the destination, relevant shared component/theme and [UI spec](../docs/PERSONALIZED-UI-SPEC.md).
- Performance issue: inspect [benchmarks](../docs/BENCHMARKS.md), use the profile/test paths documented in [workflow](WORKFLOW.md), and separate emulator measures from real-device results.
