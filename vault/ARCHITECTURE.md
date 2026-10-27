# Architecture and Critical Data Flows

[← Vault index](README.md) · [Subsystem map](SUBSYSTEMS.md) · [Engineering invariants](DECISIONS.md)

## At a glance

```text
ForgeApp / MainActivity
  └── AppGraph (manual lazy dependency graph; no DI framework)
      ├── Room: song.db (MusicDao)
      ├── Preferences DataStore
      ├── YouTubeSession ──> vendored :innertube + InnerTubeX
      ├── MediaEngine
      │    ├── playerCache (LRU)
      │    ├── downloadCache (persistent, no eviction)
      │    └── StreamResolver -> InnerTubeXPlayer -> ChunkedDataSource -> OkHttp
      ├── Downloads -> Media3 DownloadManager / persistent download index
      ├── PlayerConnection -> MediaController -> PlaybackService / ExoPlayer
      ├── AccountSync / LibraryActions / LyricsRepository / MusicExport
      ├── BackupImporter / BackupExporter / AutoBackup
      ├── ListenBrainz (optional)
      └── ArtworkAmbience
UI (Compose)
  └── ForgeRoot + NavHost + overlay PlayerSheet
      ├── four tab roots: Home, Search, Library, Downloads
      ├── child destinations: collection, artist, albums/artists, login, settings,
      │   stats, equalizer, Taste, About
      └── mini player + glass nav pill + PlayerSheet
```

The arrows are logical ownership/use paths, not necessarily synchronous calls. `AppGraph` is the one place to wire shared services. UI playback controls go through `PlayerConnection`; the `PlaybackService` owns the real player so playback is not owned by a composable lifetime.

## Modules

### `:app`

The Android app: service graph, database/data repositories, playback, downloads/export, UI, image ambience, settings and platform integrations. Namespace `dev.musicforge`.

### `:innertube`

Vendored InnerTube API client/models/pages derived from Metrolist (`com.metrolist.innertube`). Handles YouTube Music browsing/search/account operations and request/session conventions. It is intentionally separate from the stream extraction wrapper.

### InnerTubeX extraction

Dependency `com.github.MetrolistGroup.innertubex:innertubex`, version declared in `gradle/libs.versions.toml` and restricted to JitPack's MetrolistGroup via `settings.gradle.kts`. `app/.../extraction/InnerTubeXPlayer.kt` is the app's stream extraction entry point. Po-token work is under `app/.../extraction/potoken/`; `app/src/main/assets/po_token.html` is part of that path. Don't replace this casually with an unrelated URL extractor.

### `:baselineprofile`

Android instrumentation/baseline-profile and macrobenchmark module. Contains UIAutomator journeys and startup benchmarking. The documented generator may reinstall the app and reset data, so never run it against a device holding irreplaceable real user data.

## Dependency graph and ownership

- `ForgeApp` creates `AppGraph`, starts the YouTube session and schedules background startup jobs.
- `AppGraph` uses lazy properties for shared service objects. Access it via the app/context graph helpers; don't add Hilt/Koin or another DI framework.
- `MusicDatabase.open(app)` owns Room; services receive `MusicDao` or `MusicDatabase` rather than opening alternate DB copies.
- `PlaybackService` owns ExoPlayer, MediaLibrarySession, audio processors, radio continuation, play-time checkpoints, queue persistence and external media controls.
- `PlayerConnection` is the UI facade: MediaController-backed commands plus StateFlows for now-playing, queue/index and sleep timer.
- `MediaEngine` owns caches and the shared source factories.
- `StreamResolver` owns the short-lived per-video URL map, URL expiry and invalidation generation counter; it writes format metadata into Room.
- `Downloads` owns Media3 DownloadManager orchestration and reconcile logic. Its index is durable and is the main truth for completed downloads.
- `HomeRepository` reads Room flows into `LocalSignals`; `HomeComposer` is pure/deterministic section selection; `HomeViewModel` coordinates UI state and the separately labelled remote feed.
- `LyricsRepository` is fetch/cache/parse service; `LyricsPane` renders results and maps taps to seeks.
- `ArtworkAmbience` derives artwork colors and one tiny blurred bitmap per artwork URL, then shares it with player and glass surfaces.
- Backup code is deliberately independent of app navigation, so it can be tested on staged files.
- `LibraryActions` changes local state first and mirrors eligible edits remotely when signed in.

## Playback and download byte path

The cache layout is a critical correctness/performance boundary.

```text
PLAYER
  MediaItem with mediaId/videoId + placeholder YouTube URI
    -> ExoPlayer / Media3
    -> downloadCache (read-only; can use downloaded bytes)
    -> playerCache (LRU; streamed/read-ahead bytes)
    -> ResolvingDataSource
    -> StreamResolver
    -> InnerTubeXPlayer -> signed stream URL + headers + format hints
    -> ChunkedDataSource
    -> OkHttp upstream

DOWNLOAD
  DownloadManager / persistent index
    -> downloadCache (writer)
    -> downloadUpstream
    -> resolved stream + bounded ranges
```

Rules that matter:

1. Player and download caches are separate `SimpleCache`s. Do not let player/read-ahead and downloads write to the same cache key/cache writer.
2. The player reads the permanent download cache read-only, then the LRU player cache, then network.
3. Downloads can copy missing bytes from the player cache before hitting network through `downloadUpstream()`.
4. The resolver URL is short-lived. HTTP 403/410/416 marks the stream client failed for that video and invalidates the cached URL.
5. If reopening in the middle of a transfer, a new rendition must report the exact same total length as the committed format row. If not, fail; never splice two different encodes into a corrupt file.
6. Range chunking is performance-critical. Measured long-stream results showed open-ended and >~10 MiB range requests throttled to ~30 kB/s; bounded 4 MiB chunks were chosen as a compromise.
7. Unknown total length is allowed to pass through unchunked. Do not lie about a transfer being complete if the upstream closed early.
8. A quality-upgrade download uses a separate key, `<videoId>#hq`; only swap it into the main ID if its bitrate is better. It is not a normal independent song.
9. Two cache keys may share one owner's bytes when title/first artist normalize equally and durations are within 2 seconds. Keep the shared bytes until the last dependent download is removed.
10. Read-ahead budgets work according to power/network state: zero during battery saver; one upcoming song on metered network; up to three on unmetered. Avoid the currently playing cache key and downloaded/downloading items.

Relevant source: `playback/MediaEngine.kt`, `StreamResolver.kt`, `ChunkedDataSource.kt`, `extraction/InnerTubeXPlayer.kt`, `download/Downloads.kt`, `SharedKeys.kt`, `Duplicates.kt`, `Upgrades.kt`, `ReadAhead.kt`.

## Room, settings and persistence

- Room DB file: `song.db`, intentionally the same name as Metrolist/ConvX.
- Current schema version in `MusicDatabase.kt`: **3**.
- Migration 1 → 2 added `recommendation_feedback`; 2 → 3 added `remote_history`.
- The main Room entities preserve upstream table/column names for backup interop. MusicForge-only `recommendation_feedback` and `remote_history` are treated differently by restore.
- DataStore name is `settings`; setting names intentionally match upstream apps for supported imported settings.
- Keep new compatible preference keys in `Keys.importable`. Account sync timestamps and app-specific state may deliberately be excluded.
- Listening events are written incrementally. A play counts after 30 seconds or half the track, whichever comes first; time checkpoints occur around every 30 seconds.
- Queue state persists in `queue.json`, with up to 200 IDs, current index and position. It is atomically written, rebuilt from Room and restored paused.
- Download metadata travels in the request's data field so a missing DB row can be recreated when reconciling the download index.
- Automatic backups are daily at most, keep seven daily copies and three pre-restore snapshots; don't let an empty library rotate valuable backups out.

## Account and Home data flow

1. `YouTubeSession.start()` initializes InnerTubeX, applies locale/proxy/session settings and observes persisted visitor data, account cookie and auth values.
2. `AccountSync` syncs only if `YouTube.cookie` is present. It mirrors likes, saved songs, albums, artists and playlists to Room; history sync is separately time-gated (six hours).
3. Sync removes an item only if the previous complete remote snapshot contained it and a current complete snapshot no longer does. Local-only rows are retained. A partial/empty fetch must not be interpreted as an intentional delete.
4. `HomeRepository` combines local play events and remote history. It dedupes by song ID, uses the more recent timestamp and lets local win ties.
5. `HomeComposer` produces local-first factual/personal shelves. Suggestion shelves are corrected by hide/less-of-artist feedback before composition. A clean install gets a welcome/intent state, not a fake catalogue.
6. The official YouTube Music feed is loaded separately in `HomeViewModel` and shown as remote content, with continuation paging and a separate provenance.
7. Sign-out clears account cookies and sync snapshots, but does not erase the local library.

## Backup restore data flow

- `BackupExporter` snapshots the Room DB (uses SQLite snapshot behavior appropriate for Android API), then writes `song.db`, optional `settings.preferences_pb`, and a small `musicforge.json` manifest to a ZIP.
- `BackupImporter` stages only known flat ZIP entries in the cache directory, replays a bundled WAL when inspecting, checks the SQLite database and refuses backups with no songs/playlists/history.
- It uses column intersection between tables rather than a per-version migration matrix. Tables are copied in parent-before-child order; rows the current DB owns but backup lacks get defaults when allowed.
- It keeps bytes the app actually has and identifies backup-marked downloads whose bytes are absent, so the UI can offer redownload.
- Before a restore, `AutoBackup.snapshotBeforeRestore()` creates a safety copy when the local library is nonempty.
- The backup is an ordinary ZIP written by `ZipOutputStream`, not an encrypted container. It includes the DataStore preferences file as-is, which can contain the YouTube cookie, ListenBrainz token, proxy credentials and other saved settings. Treat exported and auto-backup files as secrets; never use real backup files as test fixtures in Git.
- The settings cookie/account inclusion choice is a security-sensitive behavior. Never put a real export fixture or account backup in a commit.

## UI composition and lifecycle

- Root shell: `ForgeRoot` + single Compose Navigation `NavHost`.
- Four top-level tabs: **Home, Search, Library, Downloads**. Explore is part of the Sonata roadmap but is not currently one of `Tab` enum's four roots.
- The full player is an overlay sheet. `PlayerSheet` tells the root when it covers the screen so the UI can skip painting behind it.
- Mini player and bottom nav pill are the only intended live-glass surfaces. `GlassTier` supports SOLID, ARTWORK and LIQUID; `ArtworkAmbience` downsizes the cover to 54×120 before palette/blur processing.
- Home uses a ViewModel + state/actions/events, with deterministic domain composition separate from rendering. Other screens are mixed: several ViewModels manage complex screens, while some small setting screens are direct Composables.
- Motion follows project rules: transforms/alpha in `graphicsLayer`; avoid expensive per-frame drawing and nested unbounded lazy lists.
