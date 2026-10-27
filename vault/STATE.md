# Current State and Priority

[← Vault index](README.md) · [Quest Board](../docs/QUEST-BOARD.md)

**Last checked:** 2026-10-27  
**Context branch:** `docs/musicforge-quest-board`  
**Priority order:** Vault context first, then the highest-value implementation quest.

## First priority: this vault

The user explicitly designated MusicForge as their first-priority ongoing project and main use of ChatGPT. This vault is intended to let each future session start from persistent, source-linked project context rather than re-deriving the same architecture.

- [ ] Keep this vault truthful as code changes.
- [ ] Add durable discoveries, decisions and test results to the appropriate page.
- [ ] Correct stale information in the existing docs when it causes real agent confusion.
- [ ] Keep the Quest Board statuses consistent with code and tests.
- [ ] After the vault is reviewed/merged, use [Q1: lyrics reliability](../docs/QUEST-BOARD.md#q1--make-lyrics-dependable) as the next focused implementation candidate.

**Important:** creating this vault is completed on the review branch; keeping it maintained is an ongoing priority. Do not start broad product work before understanding the existing code path and current tasks.

## Repository state checked

- **Main HEAD at initial review:** `d4af468a44fffc7e5bb586ee45241bb0f4626fa8` (2026-10-25, CI SDK refactor).
- **Review branch:** `docs/musicforge-quest-board`, branched from main and contains Quest Board docs plus this vault.
- **Main app phase:** Sonata, phase 14, `0.14.0-sonata` in `app/build.gradle.kts`.
- **Latest published stable release returned by GitHub API:** `v0.13.0-rondo`, published 2026-10-24. Confirm releases before external/public claims.
- **Room:** schema version 3, migrations 1→2 and 2→3.
- **Open GitHub issues:** none returned by the repository issue search on 2026-10-27. This is not a substitute for checking the issue tracker later.
- **Tests:** JUnit4 + Robolectric + Truth for app JVM tests; `:innertube` has its own unit tests. No dedicated MusicForge lyrics test file exists in the current test tree at this review.
- **Build gate:** `scripts/check.sh` runs app debug assemble, app unit tests and app lint. CI also runs `:innertube:testDebugUnitTest` and `:innertube:lintDebug`.
- **No implementation build/test was run for the vault-doc changes.** These are documentation-only commits, and source behavior has been inspected statically.

## Current app status: implemented vs planned

### Verified in source / history

- The current navigation code defines Home, Search, Library and Downloads as four top-level tabs.
- Main screens exist for artists/collections, library albums/artists, login, settings, stats, equalizer, Taste and About.
- The player has artwork/lyrics/queue panes, line-synced lyrics, tap-to-seek, queue reorder/remove, a codec/quality display, Up Next, volume and output-device UI.
- A history sync implementation exists: pull YouTube Music history, merge it into the local recent shelf, push eligible local plays through the playback checkpoint path.
- The download path uses Media3's persistent index, range/chunked source, cache reconciliation, duplicate byte sharing, quality upgrades and export.
- Backups, pre-restore snapshots, backup import/export and daily safety backups exist.
- Home is composed from local signals and separately loads the YouTube Music feed.
- The personalisation/taste page and recommendation feedback exist in code.
- The app has an equalizer, sleep timer, Android Auto browsing and a player widget.
- Optional ListenBrainz integration exists in source, with a file queue for offline scrobbles.

### Roadmap or not yet confirmed complete

README/CHANGELOG describe Sonata as in progress. The roadmap calls for:
- Full-bleed artwork redesign. A relevant commit note says the first player pass still showed artwork as a card and lists follow-ups for icon glyphs/touching discs. Re-check current code/device before assuming all are still open.
- Mood/genre grids in Search.
- A new Explore tab.
- Multi-select in collection pages.
- Finishing the redesigned player, including details from the spec.
- Two-way history sync is already present in source, but must be verified through tests and failure-path review before calling it robust.
- Crossfade is explicitly deferred to a separate phase for decoder cost on the target Snapdragon 680. Preference keys exist; their existence alone does not prove crossfade is fully implemented.
- Word/phrase-level karaoke lyrics, lyrics source selection and timing offset are planned items in the Quest Board, not current confirmed features.

### Priority order on the Quest Board

1. **Q0: maintain/finish the Vault as the top ChatGPT context priority.**
2. **Q1: lyrics reliability and parser/provider tests.** This is the next code task because lyrics already exist and the current test inventory has no dedicated parser test.
3. **Q2/Q3: verify current Sonata player and account-history sync behavior against actual code and tests.**
4. **Q4: Explore, Search and collection navigation work after an exact gap audit.**
5. **Q5–Q10: lyric architecture and user controls, word-sync, UI polish, small details.**
6. **Q11/Q12: playback/download resilience and reproducible performance measurements.**
7. **Q13: Hermes/FCC evaluation.** Tooling is an experiment, not a blocker for product work.

The Quest Board is the actionable checklist; this page is the status snapshot. Update this status whenever a quest is completed or the source changes.

## Known doc/source inconsistencies to resolve carefully

1. **Resolved on this branch:** `CLAUDE.md` now states Room schema version 3, documents migrations 1→2 and 2→3, and explains how `recommendation_feedback` and `remote_history` behave during restore. This correction is not on `main` until the review branch is merged.
2. `README.md`/CHANGELOG discuss phase 14 as active while the latest stable release metadata is phase 13. This is a normal branch-vs-release distinction, but public claims should be exact.
3. `docs/PERSONALIZED-UI-SPEC.md` labels itself as originally targeting Nocturne/phase 10+, but it remains a design reference. Do not assume every screen listed in its migration plan is still unimplemented.
4. **Resolved on this branch:** `CONTRIBUTING.md` and `SECURITY.md` now document `api.listenbrainz.org` as an explicit opt-in destination, alongside YouTube Music and LRCLIB. Check that wording if adding another network integration.
5. `scripts/check.sh` omits the `:innertube` test/lint tasks that CI runs. The script is the prescribed local gate, but it is narrower than CI.
6. Current Sonata UI status is not a checklist yet. The roadmap, commits and source indicate a mix of implemented and open features; do a code-by-code gap audit before marking Sonata done.
7. **Resolved on this branch:** `SECURITY.md` previously said the account cookie was never included in exported backups, but `BackupExporter` copies the DataStore preferences file into the archive. The corrected guidance says backups can contain account/session secrets and must be treated as sensitive. Verify this against the current exporter before changing backup format.

## How to update this page

Use exact commit/ref and date, mention whether a claim came from code, docs or a device measurement, and never mark runtime behavior “verified” from source inspection alone.
