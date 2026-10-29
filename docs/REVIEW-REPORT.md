# MusicForge code review report — findings & implementation backlog

**Repo:** `apathane/MusicForge`  
**Branch reviewed:** `kilo/bouncy-fog-9i8` (working tree clean at `5b301e9`)  
**Current phase:** Sonata (phase 14), version 0.14.0, code 1409, codename `Sonata`  
**Target device:** Honor X7b (Snapdragon 680, Adreno 610, Android 14, MagicOS, locale `en-ZA`)  
**Status:** findings only — no fixes applied. Pick items off this list, each as its own small PR.

---

## 1. What MusicForge is (architecture recap)

A personal YouTube Music player for Android: Kotlin + Jetpack Compose + Media3 (ExoPlayer, DownloadManager, MediaSession) + Room + DataStore. GPL-3.0. Three Gradle modules:

- `:app` — everything app-side, package `dev.musicforge`
- `:innertube` — vendored InnerTube client from Metrolist (`com.metrolist.innertube`); stream extraction via InnerTubeX from JitPack (JitPack restricted to `com.github.MetrolistGroup`)
- `:baselineprofile` — `com.android.test` module with UI Automator journey + startup benchmarks

**Wiring:** no DI framework. `AppGraph` (reachable as `context.graph`) holds lazy singletons: database, DataStore settings, `YouTubeSession`, `StreamResolver`, `MediaEngine`, `Downloads`, `MusicExport`, `LibraryActions`, `PlayerConnection`, lyrics, ambience, backup import/export, updater.

**Byte path:** `MediaEngine` → `StreamResolver` → `ChunkedDataSource`. Two `SimpleCache`s: `downloadCache` (permanent, NoOp evictor) and `playerCache` (LRU). Player reads downloadCache read-only → playerCache → network; downloads write only to downloadCache. `StreamResolver` caches a URL with TTL + generation counter; 403/410/416 retire the client and invalidate the URL. `ChunkedDataSource` fetches in 4 MiB bounded ranges (googlevideo throttles > ~10 MiB to ~30 kB/s; measured 5.6× faster for a 73 MB stream).

**Downloads:** Media3 `DownloadManager` with persistent index; `isDownloaded` flips only on `STATE_COMPLETED`; `reconcile()` at startup repairs DB rows from `DownloadMeta` JSON. **Playback:** `MediaLibraryService` with 5-band EQ, −14 LUFS loudness, 30 s play-time checkpoints, persistent queue (`QueueStore`), radio continuation, read-ahead of 5 upcoming items. UI talks only through `PlayerConnection`. Widget sends commands via a short-lived controller.

**Data:** Room schema v2, superset of Metrolist v38 / ConvX v40 tables with identical names; backups copy by column intersection. MusicForge's own tables (`recommendation_feedback`, `remote_history`) are not in `BackupImporter.TABLE_ORDER`, so restores neither copy nor wipe them.

**Conventions (bind every change):** no DI framework (wire through `AppGraph`); no Ktor (YouTube via vendored innertube); tests JUnit4 + Robolectric + Truth; Compose motion `graphicsLayer` only; nothing heavy on main thread; artwork at slot size; keep upstream DataStore key names (backups depend on them); never commit real user data; comments explain *why*.

**Build gate:** `scripts/check.sh` (debug build + unit tests + lint, 0 lint errors). Agents build on CI via `scripts/ci-check.sh` (pushes to `agent/<branch>`, waits for CI). Do not push main; work in your own branch.

---

## 2. Open work already tracked (from `.agent/board.md`)

These are in the shared vault; do not duplicate them.

**P4 / 0.14.0 (published preview 2026-10-28):**
- [ ] Real-device updater test: verify update discovery, checksum rejection, permission flow, install over preview 3 on hardware.
- [ ] Identity (owner): long-term app name, colour, signature element before a larger identity redesign.

**Phase 15 "Toccata" (see `.agent/decisions/0001-phase-15-toccata.md`):**
- Wave 1: T1 updater reuse (don't re-download a cached, verified APK); T2 motion (predictive back, sheet/menu/dialog transitions, press/list motion); T3 radio & streaming basics (endless play, audio quality, skip silence, crossfade groundwork).
- Wave 2: T4 player canvas; T5 shared loading bar; T6 awareness (offline/metered/battery saver/low storage/failing stream in one line).
- Wave 3: T7 Home (customisable shelves, artist portraits, listening tracking); T8 settings; T9 perf (GPU-friendly drawing, fewer recompositions, measured).

**Night shift (`night/maintenance`):**
- [ ] Undo in the queue re-inserts at the captured index; harmless today (Media3 clamps), wrong if the queue is reordered within the 6 s window.

**Process:**
- [ ] fcc: working default model + fallbacks; fcc agents native in Orca; worker watchdog (`tools/worker-guard.sh`).

**Sonata roadmap remaining:** transfers sheet, max quality options, collection pages + multi-select, Library menu, Explore tab + Search moods, collapsing nav, Home top picks, history by day, name phase-out (rename vendored innertube package, keep credits/key names).

---

## 3. New findings from this review (no fixes yet)

Each item: **what / where it lives / why it matters / risk.** Treat these as candidate work items; confirm the diagnosis before fixing (per the debugging guidelines: reflect on 5–7 sources, distill to 1–2, add logging to validate, then fix minimally).

### 3.1 `runBlocking` DataStore read in `AppGraph.mediaEngine` (`AppGraph.kt:69`)

**What:** `MediaEngine` construction reads `settings.data.first()[Keys.MaxSongCacheSizeMb]` under `runBlocking`. `AppGraph.mediaEngine` is a `lazy` val, so the block runs on whatever thread first touches it.

**Why it matters:** Flagged in the pre-release redundancy review as "small, not fixed." Blocking the caller on a DataStore read can jank UI or stall background work depending on who touches `graph.mediaEngine` first.

**Risk:** Low-to-medium. DataStore reads are fast but not main-thread-safe when blocking; callers vary.

**Where:** `app/src/main/kotlin/dev/musicforge/AppGraph.kt:67-71`.

### 3.2 File I/O on Main in `AutoBackup.list()` and `AutoBackup.forgetSnapshots()` (`SettingsScreen.kt`, `AccountSync.kt`)

**What:** `SettingsScreen` calls `graph.autoBackup.list()` inside a Compose `ActionRow` (composition path), and `signOut()` calls `graph.accountSync.forgetSnapshots()` which deletes files in `context.filesDir/account-sync`.

**Why it matters:** Same pre-release review flag: small, not fixed. File I/O during composition is a jank source on the X7b.

**Risk:** Low; easy to move off Main with a coroutine.

**Where:** `app/src/main/kotlin/dev/musicforge/ui/screens/SettingsScreen.kt:231`; `app/src/main/kotlin/dev/musicforge/data/AccountSync.kt:294-296`.

### 3.3 `runBlocking` inside `StreamResolver.resolve` (`StreamResolver.kt:83-84`)

**What:** `resolve` is a suspend function but calls `runBlocking { dao.format(...) }` and `runBlocking { resolver.resolve(...) }` (indirectly via MediaEngine). This blocks whatever dispatcher the caller is on.

**Why it matters:** Resolution runs on download/playback paths; blocking there can serialize transfers and stall read-ahead.

**Risk:** Medium — affects download/playback throughput on a modest device.

**Where:** `app/src/main/kotlin/dev/musicforge/playback/MediaEngine.kt:80-90`, `StreamResolver.kt`.

### 3.4 Playback error recovery is hard-coded (`PlaybackService.onPlayerError`)

**What:** After a playback error, the service skips to the next item only if `consecutiveErrors <= 3` within 15 s and a next item exists; otherwise it stops trying. The threshold and window are magic numbers.

**Why it matters:** One bad track (region-locked, removed, offline) shouldn't end the music, but the current back-off is opaque and not configurable or observable.

**Risk:** Low; UX polish item.

**Where:** `app/src/main/kotlin/dev/musicforge/playback/PlaybackService.kt:369-381`.

### 3.5 `ChunkedDataSource.MAX_EMPTY_RANGES = 3` (`ChunkedDataSource.kt:125`)

**What:** An empty range is reopened up to 3 times before the source throws "stream ended N bytes early." A stalling server (not an error) costs 3 round trips per chunk.

**Why it matters:** On flaky networks this amplifies latency; a shorter retry with backoff, or treating stall as transient, would be kinder.

**Risk:** Low; network-edge behaviour.

**Where:** `app/src/main/kotlin/dev/musicforge/playback/ChunkedDataSource.kt:71-92`.

### 3.6 Export slowness on large libraries (`MusicExport.exportAll`)

**What:** `SettingsScreen.exportAll()` iterates every downloaded id and calls `musicExport.export(id)` sequentially. Each export fetches lyrics (10 s timeout) and artwork per song; a toast warns it "runs a while."

**Why it matters:** "Save all to Music" on hundreds of songs is slow by design, but there's no progress UI beyond toasts, and it runs on the Settings ViewModel scope.

**Risk:** Low; perceived-performance / UX item.

**Where:** `app/src/main/kotlin/dev/musicforge/ui/screens/SettingsScreen.kt:136-143`; `download/MusicExport.kt`.

### 3.7 `ArtRepair` tries each link/id once per process (`ArtRepair.kt`)

**What:** A failed artwork lookup is remembered in a `tried` set and only retried when the same URL is reported failed again. A transient 5xx means the link stays unrepaired until restart.

**Why it matters:** Restored libraries have ~3% rot; a flaky network can leave artwork broken longer than necessary.

**Risk:** Low; cosmetic.

**Where:** `app/src/main/kotlin/dev/musicforge/data/ArtRepair.kt`.

### 3.8 `Downloads.maxParallelDownloads` mutated from a NetworkCallback (`Downloads.kt:277-288`)

**What:** `watchNetwork()` sets `manager.maxParallelDownloads = 3` (unmetered) / `2` (metered) via `Handler.post` on `manager.applicationLooper`. The change is not atomic with in-flight requests.

**Why it matters:** A network flip mid-batch can leave 3 in-flight on metered data, saturating the radio — contrary to the comment's intent.

**Risk:** Low; data-usage polish.

**Where:** `app/src/main/kotlin/dev/musicforge/download/Downloads.kt`.

### 3.9 Version-code scheme edge cases (`VersionCode.kt`, `app/build.gradle.kts`)

**What:** Previews p1–8 map to slots 1–8 (`phase*100 + patch*10 + preview`); stable/patch occupies slot 9. `preview = 0` currently means "stable slot 9" (1409). Release notes can declare an explicit `code` that takes precedence.

**Why it matters:** Adding a new preview or patch requires care to keep codes monotonic so updates install over each other (one permanent release key). Easy to misnumber.

**Risk:** Medium — broken updates if codes collide or go backwards.

**Where:** `app/src/main/kotlin/dev/musicforge/update/VersionCode.kt`; `app/build.gradle.kts:13-27`.

### 3.10 Room schema version vs `MigrationTest` (`Entities.kt` claims v2; test references v3)

**What:** `MusicDatabase` / entities header say schema version 2. `MigrationTest.twoToThreeAddsRemoteHistory` creates a v2 DB and runs migrations to v3, validating `remote_history` exists after. `remote_history` is listed as a MusicForge-owned table in `Entities.kt`.

**Why it matters:** Either the schema version is actually 3 and the header is stale, or the test is testing a migration that doesn't exist in `MusicDatabase`. Worth confirming before anyone touches Room.

**Risk:** Medium — a broken migration corrupts real user libraries on upgrade.

**Where:** `app/src/main/kotlin/dev/musicforge/data/db/Entities.kt`; `app/src/test/kotlin/dev/musicforge/data/db/MigrationTest.kt`; `MusicDatabase.kt` (not fully read).

### 3.11 `UpdateChecker` anonymous GitHub API rate limits (`UpdateChecker.kt:31`)

**What:** Checks `https://api.github.com/repos/apathane/MusicForge/releases` without auth. Unauthenticated limit is 60 req/hr; the check runs at most once daily plus on manual taps.

**Why it matters:** Popular instances or frequent manual checks could hit the limit; checks fail closed (Failed state) which is safe, but silent.

**Risk:** Low; reliability polish.

**Where:** `app/src/main/kotlin/dev/musicforge/update/UpdateChecker.kt`.

### 3.12 `Settings.exportAll` sequential, no cancellation (`SettingsScreen.kt`)

**What:** "Save all to Music now" runs a synchronous `forEach` over all downloaded ids with per-song toast at the end. No progress, no cancel, runs while the Settings screen is alive.

**Why it matters:** Long-running work tied to a screen lifecycle; if the user navigates away it keeps running in the ViewModel scope.

**Risk:** Low; UX item.

**Where:** `app/src/main/kotlin/dev/musicforge/ui/screens/SettingsScreen.kt:136-143`.

---

## 4. Codebase health observations (not action items yet)

- **Tests exist and are focused:** `BackupImporterTest` builds upstream song.db fixtures from Metrolist v10/v38 and ConvX v40 schema exports; `HomeComposerTest` covers all shelves; `MigrationTest` covers v1→v2 and v2→v3; `OggTagsTest`, `VersionCodeTest`, `ChecksumsTest`, `YouTubeLanguageTest`, `EqualizerProcessorTest`, `ChunkedDataSourceTest`, `ArtworkUrlTest`, `BrowseTreeTest`, `SniffAudioMimeTest`, `CoversTest`, `DownloadMetaTest`, `DuplicatesTest`, `UpgradesTest`, `MergeRecentTest`, `PlaylistInsightTest`, `StackBlurTest`, `YouTubeLinkTest`, `AccountSyncTest`, `LyricsTest`.
- **Docs are current:** `BENCHMARKS.md` (2026-10-22 transfer + rendering measurements), `PERSONALIZED-UI-SPEC.md`, `CHANGELOG.md` (through 0.14.0), `README.md`.
- **CI enforces `.agent/` privacy** via `scripts/vault-guard.sh`; `.agent/private/` is gitignored.
- **Landing page** (`site/`) is validated by CI against `app/build.gradle.kts` phase/codename.
- **Comments explain *why* and carry measured facts** — preserve that density when editing.

---

## 5. Suggested implementation order

1. Confirm Room schema version / migration 2→3 (`MusicDatabase.kt` vs `MigrationTest`) — highest risk, do first.
2. Move `AutoBackup.list()` and `forgetSnapshots()` off Main (composition-safe).
3. Replace `runBlocking` in `AppGraph.mediaEngine` DataStore read with a non-blocking default or lazy async.
4. Unblock `StreamResolver.resolve` / MediaEngine `resolve` from `runBlocking`.
5. Fix `maxParallelDownloads` network-flip atomicity.
6. Add progress + cancellation to `exportAll` ("Save all to Music").
7. Review `PlaybackService.onPlayerError` back-off constants; make observable.
8. Reduce `ChunkedDataSource` empty-range retry storm.
9. Make `ArtRepair` retry transient failures sooner.
10. Audit version-code bumps before any release tag.

Each should be its own branch + PR + CI run via `scripts/ci-check.sh`. Do not run two Gradle commands at once (14 GB dev machine).

---

## 6. What I skipped (ponytail ultra)

- Did not run Gradle, tests, or lint — build on CI per AGENTS.md.
- Did not create a branch, commit, or push — this document is the deliverable; the coordinator decides when to branch/commit.
- Did not use adb, emulator, or phone (not authorized for this task).
- Did not read every composable/screen body in full, the full `MusicDao.kt`/`MusicDatabase.kt`, `site/` source beyond CI checks, `docs/site-redesign/`, `.github/release-notes.md`, or every test's full body.
- Did not write any fixes — this is a findings report only.

---

**End of report.** Use the items above as a backlog; each fix should be minimal, tested, and CI-verified before merge.