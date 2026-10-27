# Source and Test File Map

[← Vault index](README.md) · [Subsystem guide](SUBSYSTEMS.md) · [Workflow/test map](WORKFLOW.md)

## Top-level files

| Path | Purpose |
|---|---|
| `README.md` | User-facing project overview, features, phase roadmap and credits |
| `CHANGELOG.md` | Phase-by-phase feature and fix history |
| `AGENTS.md` | Mandatory instructions for any coding agent |
| `CLAUDE.md` | Detailed architecture, commands, invariants and coding conventions |
| `CONTRIBUTING.md` | Toolchain, branch/commit, contribution and non-goal policy |
| `SECURITY.md` | Secret-handling and vulnerability policy |
| `docs/PERSONALIZED-UI-SPEC.md` | Detailed product/design/recommendation spec |
| `docs/BENCHMARKS.md` | Transfer and UI performance measurements |
| `docs/QUEST-BOARD.md` | Prioritized next-step quests, including Hermes/FCC test |
| `scripts/check.sh` | Prescribed local build + app tests + app lint gate |
| `gradle/libs.versions.toml` | Central version catalog |
| `settings.gradle.kts` | Modules and allowed dependency repositories |
| `app/build.gradle.kts` | App phase/codename/SDK/dependencies/Room schema config |

## Module roots

| Path | What lives there |
|---|---|
| `app/src/main/kotlin/dev/musicforge/` | App setup, data services, playback, downloading, UI, widget |
| `app/src/test/kotlin/dev/musicforge/` | App JVM unit/Robolectric tests |
| `app/schemas/` | Exported Room schemas and migration fixtures |
| `app/src/main/assets/` | Extraction asset(s), including PoToken HTML |
| `app/src/main/res/` | Android resources, fonts, widget layouts, themes |
| `innertube/src/main/kotlin/com/metrolist/innertube/` | YouTube Music API client, models, response parsers and browse/search pages |
| `innertube/src/test/kotlin/com/metrolist/innertube/` | InnerTube module unit tests |
| `baselineprofile/src/main/kotlin/dev/musicforge/baselineprofile/` | UIAutomator journeys and startup/macrobenchmark setup |
| `licenses/` | Preserved licenses/attribution for vendored third-party code |
| `site/` | Plain HTML/CSS/JS landing page deployed via GitHub Pages |
| `docs/screenshots/` | Current UI screenshots used by README/design review |
| `docs/brand/` | Repository social preview image/brand assets |

## App source by subsystem

| Path / package | Responsibility |
|---|---|
| `AppGraph.kt`, `ForgeApp.kt`, `MainActivity.kt` | Application lifecycle and singleton wiring |
| `data/db/Entities.kt` | Room entities, upstream-compatible table layout, own feedback/history tables |
| `data/db/MusicDao.kt` | Query/write API; library, playback, stats, Home, lyrics |
| `data/db/MusicDatabase.kt` | Room schema version, migrations, open helper |
| `data/prefs/Prefs.kt` | DataStore, setting keys and compatibility map |
| `data/AccountSync.kt`, `LibraryActions.kt` | Signed-in sync and local-first library mutations |
| `data/ArtRepair.kt` | Stale/missing artwork lookup and repair |
| `data/ListenBrainz.kt` | Opt-in remote scrobbling and offline pending queue |
| `data/lyrics/Lyrics.kt` | Lyrics domain model, LRC parser, provider lookup/cache |
| `data/backup/AutoBackup.kt` | Daily/pre-restore safety backup rotation |
| `data/backup/BackupExporter.kt` | Consistent DB snapshot and zip writer |
| `data/backup/BackupImporter.kt` | Validate/stage/restore backup and preferences |
| `data/backup/PreferencesProto.kt` | Minimal preferences protobuf codec |
| `playback/MediaEngine.kt` | Media3 caches and data-source chains |
| `playback/StreamResolver.kt` | Resolve URLs/headers/format and protect against stale URLs |
| `playback/ChunkedDataSource.kt` | Bounded HTTP-range stream wrapper |
| `playback/PlaybackService.kt` | ExoPlayer, Android Auto/media session, play time/history/queue |
| `playback/PlayerConnection.kt` | MediaController façade and UI StateFlows/actions |
| `playback/MediaItems.kt` | MediaItem conversion/metadata/credits/art sizing/provenance |
| `playback/PlayRecorder.kt` | Song/artist persistence, listening events and time accumulation |
| `playback/QueueStore.kt` | Atomic saved queue state |
| `playback/ReadAhead.kt` | Network/battery-aware next-track caching |
| `playback/AudioQuality.kt`, `Equalizer*.kt` | Quality options and five-band DSP |
| `playback/BrowseTree.kt`, `SearchPlay.kt` | Media library browse tree and voice/search playback |
| `playback/YouTubeSession.kt` | Provider locale/account/visitor/proxy settings |
| `extraction/InnerTubeXPlayer.kt` | Stream extraction wrapper and client-failure tracking |
| `extraction/potoken/` | Po-token generation, WebView wrapper and result types |
| `download/Downloads.kt`, `ForgeDownloadService.kt` | DownloadManager/index, states, reconciliation and service |
| `download/DownloadMeta.kt` | Persisted item metadata for recovery |
| `download/SharedKeys.kt`, `Duplicates.kt`, `Upgrades.kt` | Shared cache identities, duplicate songs, higher-quality copies |
| `download/Covers.kt`, `Redownloads.kt` | Cover support and post-restore redownload offers |
| `download/MusicExport.kt`, `OggTags.kt` | Cache-to-Music export, remux/tag/cover/lyrics |
| `recommendation/Home.kt` | Pure local-signal data model and deterministic section composer |
| `recommendation/HomeRepository.kt` | Room signals, recent-play merge and correction persistence |
| `ui/home/HomeViewModel.kt`, `HomeScreen.kt` | Home state/actions/feed and rendered UI |
| `ui/ForgeRoot.kt`, `ui/Routes.kt` | Root NavHost, tabs, destinations, central routing |
| `ui/designsystem/Signal.kt` | Shared tokens and surfaces |
| `ui/theme/Theme.kt`, `ArtworkAmbience.kt` | Palette, typography, ambience and artwork blur |
| `ui/components/` | Common components, artwork, song rows, skeletons, shelves, track actions and glass |
| `ui/player/PlayerSheet.kt`, `PlayerPanes.kt`, `MiniPlayer.kt`, `Scrubber.kt` | Full player, panes, mini player and progress control |
| `ui/screens/SearchScreen.kt` | Search ViewModel/Compose UI and suggestions |
| `ui/screens/LibraryScreen.kt`, `LibraryLists.kt` | Library landing/list filters and album/artist grids |
| `ui/screens/CollectionScreen.kt`, `ArtistScreen.kt` | Local/remote collections and artist detail |
| `ui/screens/DownloadsScreen.kt` | Downloads and restore/redownload actions |
| `ui/screens/SettingsScreen.kt`, `LoginScreen.kt` | Preferences, backup/restore and account login |
| `ui/screens/EqualizerScreen.kt`, `TasteScreen.kt`, `StatsScreen.kt`, `AboutScreen.kt` | Audio controls, personalisation inspection, statistics, about |
| `ui/YouTubeLink.kt` | Pasted URL parsing |
| `widget/PlayerWidget.kt` | Android home-screen player widget |

## App tests (initial tree inventory)

| Test | Focus |
|---|---|
| `data/AccountSyncTest.kt` | Snapshot-backed sync deletion rules |
| `data/backup/BackupImporterTest.kt` | Backup formats, safety, malformed archive/data |
| `data/db/MigrationTest.kt` | Schema migration preservation |
| `download/CoversTest.kt` | FLAC cover-picture block plus basic LRC round-trip |
| `download/DownloadMetaTest.kt` | Saved metadata decoding |
| `download/DuplicatesTest.kt` | Audio identity / shared bytes rule |
| `download/OggTagsTest.kt` | Ogg comments and CRC/tag structure |
| `download/SniffAudioMimeTest.kt` | Format detection |
| `download/UpgradesTest.kt` | Download quality upgrade rules |
| `playback/ArtworkUrlTest.kt` | Artwork URL sizing/handling |
| `playback/BrowseTreeTest.kt` | Media library browse hierarchy |
| `playback/ChunkedDataSourceTest.kt` | Ranged streaming behavior |
| `playback/EqualizerProcessorTest.kt` | DSP processing behavior |
| `playback/YouTubeLanguageTest.kt` | Supported provider language mapping |
| `recommendation/HomeComposerTest.kt` | Deterministic personal shelf selection |
| `recommendation/MergeRecentTest.kt` | Local/remote recent-play merge |
| `ui/StackBlurTest.kt` | Artwork blur processing |
| `ui/YouTubeLinkTest.kt` | Pasted link parsing |
| `ui/screens/PlaylistInsightTest.kt` | Playlist insight summary |

Paths are relative to `app/src/test/kotlin/dev/musicforge/`, except where noted. The initial inventory found no dedicated lyrics parser/provider test.

## InnerTube tests (initial tree inventory)

Paths are relative to `innertube/src/test/kotlin/com/metrolist/innertube/`:

- `PlaybackRegistrationTest.kt`
- `PodcastEnrichmentTest.kt`
- `SearchAuthenticationTest.kt`
- `UploadProgressInputStreamTest.kt`
- `models/MusicResponsiveHeaderRendererTest.kt`
- `pages/AlbumPageTest.kt`
- `pages/PageHelperTest.kt`
- `pages/ParserRegressionTest.kt`
- `pages/PlaylistPageTest.kt`
- `pages/PodcastPageTest.kt`
- `pages/SearchPageTest.kt`

## Files intentionally not described line-by-line

`innertube/models/`, response types, vendored backdrop internals and Android resources contain numerous protocol/value declarations and low-level drawing primitives. Their detailed behavior should be learned from the specific file when a change touches them. They are indexed by module/path above; this page does not claim every line in the repository has been read.
