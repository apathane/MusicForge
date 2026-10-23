# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

MusicForge is a YouTube Music player for Android (Kotlin, Jetpack Compose, Media3, Room). It was built from scratch on ideas from Metrolist (download engine), ConvX (screens, features, backup format) and BitChord (bounded-range transfers, monochrome style). GPL-3.0. The target device is an Honor X7b (Snapdragon 680, Adreno 610, Android 14, MagicOS, locale `en-ZA`).

## Commands

JDK 21 and the Android SDK (compileSdk 37, minSdk 26, targetSdk 36).

```sh
./gradlew :app:assembleDebug                      # package dev.musicforge.app.debug, installs beside release
./gradlew :app:assembleRelease                    # R8; signed with the local debug key; ~2 min, nearly all R8
./gradlew :app:testDebugUnitTest                  # JVM unit tests (Robolectric where Android is needed)
./gradlew :app:testDebugUnitTest --tests '*BackupImporterTest*'
./gradlew :app:testDebugUnitTest --tests 'dev.musicforge.download.OggTagsTest.crcMatchesKnownOggValue'
./gradlew :app:lintDebug                          # kept at 0 errors
./gradlew :app:generateReleaseBaselineProfile     # needs a device; runs on every connected device unless ANDROID_SERIAL is set
```

- Build outputs go to `/dev/shm/musicforge-build/<module>` (see the end of `build.gradle.kts`) and the project cache to `/dev/shm/musicforge-gradle` (`gradle.properties`), because the dev machine's disk is slow. APKs are under `/dev/shm/musicforge-build/app/outputs/apk/`. The `build` symlinks in module folders are local conveniences and are gitignored.
- The baseline profile generator and the macrobenchmarks in `:baselineprofile` reinstall `dev.musicforge.app` and reset its data. Never point them at a phone whose release install holds real user data. Test on real hardware with the debug package instead.
- Quick on-device checks without instrumentation: `adb shell am start -W` for start time, `adb shell dumpsys gfxinfo <pkg>` for frame stats, `adb shell setprop debug.hwui.overdraw show` for overdraw, `cmd package compile -m speed-profile -f <pkg>` vs `-m verify` to compare with and without the profile.

## Modules

- `:app`: everything app-side, package `dev.musicforge`.
- `:innertube`: InnerTube API client vendored from Metrolist (`com.metrolist.innertube`). Stream extraction comes from InnerTubeX (JitPack, `com.github.MetrolistGroup`; `settings.gradle.kts` restricts JitPack to that group). `app/.../extraction` adapts Metrolist's `InnerTubeXPlayer` and po-token code (asset `po_token.html`).
- `:baselineprofile`: `com.android.test` module with the UI Automator journey (`Journeys.kt`) and startup benchmarks. Use coordinate swipes there: Compose lists recompose mid-fling and held `UiObject2`s go stale.

## Architecture

**Wiring.** No DI framework. `AppGraph` (reachable as `context.graph`) holds lazy singletons: database, settings DataStore, `YouTubeSession`, `StreamResolver`, `MediaEngine`, `Downloads`, `MusicExport`, `LibraryActions`, `PlayerConnection`, lyrics, ambience, backup import/export.

**The byte path** is the core of the app; read `MediaEngine`, `StreamResolver` and `ChunkedDataSource` together.
- Two `SimpleCache`s: `downloadCache` (permanent, NoOp evictor) and `playerCache` (LRU). The player reads downloadCache read-only, then playerCache, then the network. Downloads write only to downloadCache. Separate caches mean read-ahead and downloads never fight over a cache key's single writer.
- `StreamResolver` turns a video id into a URL via InnerTubeX, caches it with an expiry and a generation counter (an invalidation during a resolve discards the late result), and records a `format` row (mime, length, loudness).
- `ChunkedDataSource` fetches in 4 MiB ranges (`StreamResolver.DEFAULT_CHUNK`). Google's video servers throttle any request above about 10 MiB to ~30 kB/s; `docs/BENCHMARKS.md` has the measurements. A mid-file re-resolve must return the same content length (`expectLength`), or the transfer fails instead of splicing two renditions. 403/410/416 retire the client and invalidate the URL.
- Cache keys are video ids. Exception: a download that duplicates another recording (`Duplicates`: same normalized title and first artist, duration within 2 s) stores under the owner's key. `MediaEngine.storageKeys` maps song id to storage key and `SharedKeyDownloaderFactory` keeps shared bytes until the last download using them is removed.

**Downloads** (`download/Downloads.kt`) use Media3 `DownloadManager` with a persistent index, so they survive process death. A song's `isDownloaded` flag flips only on `STATE_COMPLETED`. `reconcile()` treats the download index and cache as the source of truth and repairs DB rows at startup. Request data carries `DownloadMeta` JSON so rows can be rebuilt. `MusicExport` copies finished downloads to `Music/MusicForge` via MediaStore, remuxing WebM/Opus to Ogg/Opus (MediaStore refuses `audio/webm` in Music) and writing Vorbis comments, a FLAC picture block and LRC lyrics with `OggTags`, which rewrites the single tag page (about 64 KB limit).

**Playback** (`playback/PlaybackService.kt`) is a `MediaLibraryService`: notification and lock screen, Android Auto (`BrowseTree`), voice search (`SearchPlay`), radio continuation, read-ahead of upcoming items (`ReadAhead`), in-app 5-band EQ (`EqualizerProcessor` in the audio sink), loudness attenuation to −14 LUFS, play-time checkpoints every 30 s, the persistent queue (`QueueStore`) and `onPlaybackResumption`. The UI talks to it only through `PlayerConnection` (a `MediaController`). The home-screen widget (`widget/PlayerWidget`) sends commands through a short-lived controller so it works when the app is dead.

**Data.** Room schema version 2 (`app/schemas/`). Its upstream tables are a superset of Metrolist v38 and ConvX v40 tables and columns, with the same table and column names, so backups copy across by column intersection. MusicForge's own tables (`recommendation_feedback`, from v2) are not in `BackupImporter.TABLE_ORDER`, so restores neither copy nor wipe them. Schema changes go through Room AutoMigrations; `MigrationTest` checks a v1 library survives (the schemas ride in debug assets for it). `BackupImporter`:
- never `ATTACH`es (Android turns WAL off for that connection); it streams rows from a separate connection;
- replays a bundled `-wal` and refuses empty backups (the restore screen takes an `AutoBackup` snapshot first);
- stashes songs whose bytes we hold, with their artists and `format` rows (ours win over the backup's), so downloads survive a restore;
- returns `wereDownloaded` (songs the old app had downloaded), which `Redownloads` offers back in the Downloads tab;
- imports settings by upstream key name: `Keys.importable` in `data/prefs/Prefs.kt` lists the DataStore keys that come across, so keep Metrolist/ConvX key names when adding settings they share.

**YouTube locale.** `YouTubeSession.youTubeLanguage()` only sends `hl` values from YouTube's own list. Any other tag (for example `en-ZA`) gets HTTP 400 on every request, which broke home, search, playback and downloads on the real phone while the `en-US` emulator looked fine.

**Home** (`recommendation/`, `ui/home/`) is composed from the library, never from invented content: `HomeRepository` reads Room flows into `LocalSignals`, `HomeComposer` (pure, deterministic, unit-tested) turns them into sections, each with a reason and per-item provenance; a clean install gets only the welcome. YouTube's feed follows, labelled. Shelves share one picker (two songs per artist at most, nothing a shelf above shows). Corrections (Not for me, Less of an artist) are `recommendation_feedback` rows. Home is MVI (`HomeState`/`HomeAction`/`HomeEvent`, `HomeRoot` + `HomeScreen`); the spec it follows is `docs/PERSONALIZED-UI-SPEC.md`.

**UI** is Compose with Material 3 used for primitives only; design tokens (radii, gutter) and shared pieces (`SignalChip`, `SectionHeader`, `signalSurface`) live in `ui/designsystem`, colours in `ui/theme/Theme.kt` (the spec's canvas/ink/muted/line); the look is custom (`ui/theme`, `ui/components`). Navigation is a single `NavHost` in `ForgeRoot` with the player as an overlay sheet (`ui/player/PlayerSheet.kt`). `ArtworkAmbience` derives colours and one pre-blurred 54×120 backdrop per track; glass surfaces draw that bitmap (there is no live blur). Rules that came from measuring overdraw on the X7b:
- The activity's window background is removed after `setContent`; the root composable paints the screen and skips its background while the player covers it.
- `Artwork` requests a right-sized image (`sizedArtwork`: 128/256/544 px), shows the grey tile only as Coil placeholder/error, and zooms past YouTube's letterboxing with a `ContentScale` (`CropPastLetterbox`), not an extra layer. Only `default`, `hqdefault` and `sddefault` thumbnails are letterboxed (`isLetterboxedThumbnail`).
- The player's scrim is baked into the small backdrop once per track.

## Conventions

- Each development phase gets its own codename, set as `val codename` in `app/build.gradle.kts` (it becomes the launcher label and versionName suffix) until the app has a real name. Phases are tagged `phase-N-<codename>`.
- Commit messages explain why and carry measurements when the change is about performance. Commits are authored `apathane <316078151+apathane@users.noreply.github.com>` (set in this repo's git config) with a `Co-Authored-By: Claude …` trailer. The remote is the private repo `apathane/MusicForge`.
- Comments say why, at the density of the surrounding code; many record a measured fact or a device quirk. Keep that.
- The Gradle version catalog is `gradle/libs.versions.toml`. Media3 is pinned to 1.10.x on purpose (see the comment there).
