# Build, Test and Agent Workflow

[← Vault index](README.md) · [Engineering decisions](DECISIONS.md) · [Quest Board](../docs/QUEST-BOARD.md)

## Before working

1. Check current branch and HEAD; this vault may be behind a newer commit.
2. Read `vault/README.md`, `vault/STATE.md`, this page, and the relevant Quest Board item.
3. Read `AGENTS.md` and `CLAUDE.md` before any code edit. Those repo rules outrank generic agent defaults.
4. Inspect relevant source plus tests on the active branch. This vault is a map, not a reason to skip source reading.
5. Keep the work scoped to the files required by the task. If the fix requires wider scope, explain why before expanding.

## Toolchain and commands

- JDK 21 / Kotlin JVM toolchain 21.
- Android SDK platform 37; compile SDK 37, target SDK 36, minimum API 26.
- Build outputs and Gradle project caches are configured to live in `/dev/shm`; this avoids expensive writes on the developer's slow disk, but build outputs are disposable across reboot.
- Main app package: `dev.musicforge`; app ID `dev.musicforge.app`; debug suffix `.debug`.

Prescribed local build gate:

```sh
./scripts/check.sh
```

That script runs:
- `:app:assembleDebug`
- `:app:testDebugUnitTest`
- `:app:lintDebug`

CI's `.github/workflows/ci.yml` additionally runs:
- `:innertube:testDebugUnitTest`
- `:innertube:lintDebug`

So a local `scripts/check.sh` pass is not identical to full CI. When a task touches `:innertube`, run or request the corresponding checks if available. The repository rules prohibit running multiple heavy Gradle commands concurrently on the same machine; `scripts/check.sh` should be the normal entry point.

Focused test examples from `CLAUDE.md`:

```sh
./gradlew :app:testDebugUnitTest --tests '*BackupImporterTest*'
./gradlew :app:testDebugUnitTest --tests 'dev.musicforge.download.OggTagsTest.crcMatchesKnownOggValue'
```

Do not run baseline-profile generation or device benchmarks against a phone containing real user data. The documented baseline profile task can reinstall the app/reset its data. Device checks are opt-in; don't invoke adb/emulator unless the user/task says so.

## Code changes

- Work on a task-specific branch/worktree. The project guidance says not to push to main; coordinator review/merge is expected.
- Make small commits that answer “what changed and why”; include measurements when performance changes.
- Repo contribution guide asks the final commit-message line to be:
  `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`
  or replace it with the actual agent signature.
- Keep changes local and reviewable. Don't create PRs, merge or publish releases unless explicitly requested.
- Update `CHANGELOG.md` under current phase for meaningful project changes.
- For UI changes, examine `docs/screenshots/`; for DB changes, update `app/schemas/` and add/adjust migration tests; for new compatible settings, consider `Keys.importable`.
- Don't modify upstream generated baseline profiles or generated schema files by hand unless the project procedure calls for it.

## Test map

### App tests

- `data/AccountSyncTest.kt`
- `data/backup/BackupImporterTest.kt`
- `data/db/MigrationTest.kt`
- `download/CoversTest.kt`
- `download/DownloadMetaTest.kt`
- `download/DuplicatesTest.kt`
- `download/OggTagsTest.kt`
- `download/SniffAudioMimeTest.kt`
- `download/UpgradesTest.kt`
- `playback/ArtworkUrlTest.kt`
- `playback/BrowseTreeTest.kt`
- `playback/ChunkedDataSourceTest.kt`
- `playback/EqualizerProcessorTest.kt`
- `playback/YouTubeLanguageTest.kt`
- `recommendation/HomeComposerTest.kt`
- `recommendation/MergeRecentTest.kt`
- `ui/StackBlurTest.kt`
- `ui/YouTubeLinkTest.kt`
- `ui/screens/PlaylistInsightTest.kt`

The initial repository inventory found no dedicated `LyricsTest` / `LrcTest` file. Check again before creating one; Q1 starts with lyrics parser/provider regression coverage.

### InnerTube module tests

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

### UI performance

`docs/BENCHMARKS.md` contains transfer measurements and emulator rendering captures. The emulator uses a different GPU/rendering path from the target Honor X7b and can overstate GPU performance. Keep the device, variant, scenario and measurement method with any new benchmark.

## Agent comparison discipline

The Quest Board's Hermes/FCC item is an experiment, not project architecture. If comparing agents, use an isolated worktree and the same bounded task, test instructions and available models. Do not allow two agents to edit the same files/worktree concurrently. Score implementation correctness, passing tests, scope discipline, provider reliability/cost and human repair required. Keep credentials and real user backups out of agent workspaces wherever practical.
