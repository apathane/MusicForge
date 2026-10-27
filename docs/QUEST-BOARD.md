# MusicForge Quest Board

> The working board for MusicForge's next steps. Quests are ordered by dependency and user value, not by how flashy they look. Update status only after checking the code and tests.

**Current phase:** Sonata (phase 14, version 0.14.0)  
**Build gate:** scripts/check.sh  
**Board synthesis:** 2026-10-27

## How to use this board

- **NEXT**: the best next task to pick up.
- **READY**: scoped well enough to implement after dependencies are met.
- **IN PROGRESS**: README/changelog describe active work; verify source before treating any item as unfinished.
- **LATER**: valuable, but should not displace reliability or Sonata completion.
- **DECISION**: requires a design or tooling decision first.
- Tick a quest only after acceptance checks pass. Keep changes small and reviewable; run scripts/check.sh.

## Q0 · Keep the Vault accurate and useful
**Status: ACTIVE · Priority: P0 · Size: ongoing**

The repository vault is the first priority for future ChatGPT work on MusicForge. It exists to preserve technical context, source maps, invariants and project history so work does not restart from zero each session.

- [x] Create the vault index, current-state snapshot, product context, architecture, subsystem guide, engineering invariants, workflow guide, history and source/test map.
- [x] Link the vault from README and make it the first required read in AGENTS.md and CLAUDE.md.
- [x] Correct stale Room schema context and document the opt-in ListenBrainz service in security/contributor docs.
- [x] Correct the misleading claim that exported backups never contain the YouTube Music cookie; document that the ZIP is unencrypted and may contain YouTube, ListenBrainz and proxy credentials.
- [ ] Verify the docs against the reviewed source, then review/merge the documentation branch.
- [ ] Keep the vault current whenever a source, test, architecture decision or meaningful phase status changes.
- [ ] After code work, record test outcomes and distinguish what ran locally from what CI/device checks remain.

**Acceptance:** a new agent can identify the architecture, find the relevant source/tests, preserve the known invariants and identify the current quest without rediscovering the entire repository. Treat vault notes as a map, not a substitute for current source.

**Start here:** [Vault index](../vault/README.md) · [Current state](../vault/STATE.md) · [Architecture](../vault/ARCHITECTURE.md) · [Subsystem map](../vault/SUBSYSTEMS.md) · [Source/test map](../vault/FILE-MAP.md)

## The next implementation quest

### Q1 · Make lyrics dependable
**Status: NEXT · Priority: P0 · Size: M**

MusicForge already has LRCLIB lookup, a YouTube Music plain-lyrics fallback, a Room cache, line timing, tap-to-seek and export support. Improve correctness before adding richer animation.

- [ ] Expand the existing `download/CoversTest.kt` LRC round-trip into focused parser tests, or move lyrics cases to a dedicated `LrcTest.kt`.
- [ ] Cover malformed timestamps, empty lyrics, repeated timestamps, multiple stamps on one line, and invalid/out-of-range values.
- [ ] Test provider failure, empty response, cache hit/miss and duration mismatch.
- [ ] Review title/artist normalization and matching of live, remix and alternate versions.
- [ ] Ensure a failed source does not prevent a later fallback from being tried.
- [ ] Preserve source and lyric-format metadata where available.

**Acceptance:** parser and matching tests cover these cases; bad provider data cannot crash the lyrics pane; existing plain and line-synced lyrics still work; scripts/check.sh passes.

**References:** [MusicForge Lyrics.kt](../app/src/main/kotlin/dev/musicforge/data/lyrics/Lyrics.kt) · [MusicForge PlayerPanes.kt](../app/src/main/kotlin/dev/musicforge/ui/player/PlayerPanes.kt) · [BitChord LyricsRepository.kt](https://github.com/kushagrasinghx/BitChord/blob/main/shared/src/jvmSharedMain/kotlin/com/music/bitchord/data/lyrics/LyricsRepository.kt) · [BitChord EnhancedLrc.kt](https://github.com/kushagrasinghx/BitChord/blob/main/shared/src/jvmSharedMain/kotlin/com/music/bitchord/data/lyrics/EnhancedLrc.kt)

## Main questline: Sonata

### Q2 · Finish the player spec
**Status: IN PROGRESS · Priority: P0 · Size: L**

The README's Sonata scope includes full-bleed artwork, codec/quality pill, Up Next, volume slider, output-device switcher, lyrics and queue panes, crossfade-ready queue pills, and a collapsing glass navigation bar carrying the mini player.

- [ ] Compare current implementation against the Sonata spec; mark every item shipped, partial or missing.
- [ ] Verify the player layout at narrow and large screen sizes.
- [ ] Confirm player controls have meaningful accessibility labels and usable touch targets.
- [ ] Verify volume/output routing on real hardware before calling it complete.
- [ ] Keep liquid glass limited to the nav bar and mini player unless profiling supports more.
- [ ] Check pause/resume, seek, queue changes and track transitions for UI state drift.

**Acceptance:** each spec item has an explicit status; no placeholder control is presented as working; build, unit tests and lint pass.

**References:** [README roadmap](../README.md#roadmap) · [PlayerSheet.kt](../app/src/main/kotlin/dev/musicforge/ui/player/PlayerSheet.kt) · [Glass.kt](../app/src/main/kotlin/dev/musicforge/ui/components/Glass.kt) · [ArtworkAmbience.kt](../app/src/main/kotlin/dev/musicforge/ui/theme/ArtworkAmbience.kt)

### Q3 · Verify two-way listening-history sync
**Status: IN PROGRESS · Priority: P0 · Size: M**

The Sonata changelog describes pulling remote plays, merging them into Home and pushing local plays to YouTube Music.

- [ ] Verify local plays are uploaded only when eligible and are not duplicated on retry.
- [ ] Verify remote plays merge with local history without duplicate rows.
- [ ] Test signed-out, expired-cookie, offline and provider-error paths.
- [ ] Confirm Home refreshes predictably after sync and never loses local history.
- [ ] Document YouTube-side limitations that MusicForge cannot guarantee away.

**Acceptance:** retrying sync is safe; local history remains intact through failures; merge behavior is covered by tests.

**References:** [AccountSync.kt](../app/src/main/kotlin/dev/musicforge/data/AccountSync.kt) · [HomeRepository.kt](../app/src/main/kotlin/dev/musicforge/recommendation/HomeRepository.kt) · [CHANGELOG.md](../CHANGELOG.md)

### Q4 · Complete Explore, Search and collection navigation
**Status: READY · Priority: P1 · Size: L**

The current Sonata roadmap calls for mood/genre grids in Search, a new Explore tab, and collection pages with multi-select.

- [ ] Audit existing routes and screens before adding parallel implementations.
- [ ] Add mood/genre entry points to Search with loading, empty and error states.
- [ ] Implement Explore shelves from real provider data, with graceful fallback when unavailable.
- [ ] Add multi-select to collection pages with clear selection state and safe bulk actions.
- [ ] Verify four-tab navigation, scroll-to-top on reselection and mini-player continuity.

**Acceptance:** shelves have real data or explicit empty states; navigation does not interrupt playback; selection actions are reversible where practical.

**References:** [ForgeRoot.kt](../app/src/main/kotlin/dev/musicforge/ui/ForgeRoot.kt) · [Routes.kt](../app/src/main/kotlin/dev/musicforge/ui/Routes.kt) · [SearchScreen.kt](../app/src/main/kotlin/dev/musicforge/ui/screens/SearchScreen.kt) · [README roadmap](../README.md#roadmap)

## Lyrics upgrade questline

### Q5 · Introduce a provider boundary and richer lyric model
**Status: READY · Priority: P1 · Size: M**

Do not replace the working pipeline in one rewrite. Keep the current entry point and separate responsibilities gradually.

- [ ] Define a provider result model for plain text, line-timed lyrics and word-timed lyrics.
- [ ] Separate fetching, matching, parsing, caching and rendering.
- [ ] Add configurable source order only when each source has reliable failure and cancellation behavior.
- [ ] Keep network work off the main thread; cancel obsolete lookups on track change.
- [ ] Preserve cache compatibility or provide a tested migration if the schema must change.

**Acceptance:** LRCLIB and YouTube fallback behavior remains functional; adding a provider does not require provider-specific UI logic.

**References:** [BitChord LyricsSource.kt](https://github.com/kushagrasinghx/BitChord/blob/main/shared/src/jvmSharedMain/kotlin/com/music/bitchord/data/lyrics/LyricsSource.kt) · [BitChord LyricsQuery.kt](https://github.com/kushagrasinghx/BitChord/blob/main/shared/src/jvmSharedMain/kotlin/com/music/bitchord/data/lyrics/LyricsQuery.kt)

### Q6 · Add lyrics source controls and sync offset
**Status: READY · Priority: P1 · Size: S/M**

- [ ] Show the source when known.
- [ ] Add retry/find-lyrics action for missing or incorrect results.
- [ ] Add source selection if multiple sources are configured.
- [ ] Add an offset control that shifts timing earlier or later in small increments.
- [ ] Decide deliberately whether offsets are per-track or global.
- [ ] Keep controls out of the main reading area until requested.

**Acceptance:** offset changes apply immediately, persist according to the chosen scope, and can be reset; missing lyrics have a useful empty state.

**Reference:** [BitChord LyricsOffsetSheet.kt](https://github.com/kushagrasinghx/BitChord/blob/main/sharedUi/src/jvmSharedMain/kotlin/com/music/bitchord/ui/player/LyricsOffsetSheet.kt)

### Q7 · Add word-synced lyrics
**Status: LATER · Priority: P1 · Size: L**

- [ ] Implement enhanced LRC parsing after the richer model exists.
- [ ] Evaluate TTML parsing for syllable-level timings; harden XML parsing against malformed input.
- [ ] Build a playback-following lyric clock that handles pause, seek, speed changes, delayed position reports and track changes.
- [ ] Animate word highlighting without expensive per-frame recomposition.
- [ ] Keep line-synced and plain-text fallbacks.
- [ ] Add deterministic clock/parser tests before polishing animation.

**Acceptance:** word timing stays aligned after pause/seek/speed changes; missing word timing degrades gracefully to line timing.

**References:** [BitChord EnhancedLrc.kt](https://github.com/kushagrasinghx/BitChord/blob/main/shared/src/jvmSharedMain/kotlin/com/music/bitchord/data/lyrics/EnhancedLrc.kt) · [BitChord TtmlLyrics.kt](https://github.com/kushagrasinghx/BitChord/blob/main/shared/src/jvmSharedMain/kotlin/com/music/bitchord/data/lyrics/TtmlLyrics.kt) · [BitChord LyricClock.kt](https://github.com/kushagrasinghx/BitChord/blob/main/sharedUi/src/jvmSharedMain/kotlin/com/music/bitchord/ui/player/LyricClock.kt) · [BitChord LyricClockTest.kt](https://github.com/kushagrasinghx/BitChord/blob/main/sharedUi/src/jvmTest/kotlin/com/music/bitchord/LyricClockTest.kt)

### Q8 · Translation, romanization and lyric sharing
**Status: LATER · Priority: P2 · Size: M**

- [ ] Investigate translation and romanization sources and terms before implementation.
- [ ] Make language choices explicit; do not silently replace original lyrics.
- [ ] Add copy/share lyric-card behavior only with copyright-aware limits and clear user intent.
- [ ] Do not persist generated translations as if they were provider-original lyrics.

**Acceptance:** original lyrics remain available; source/language provenance is clear; feature degrades gracefully without translation data.

## UI polish questline

### Q9 · Tighten the player and lyrics visual hierarchy
**Status: READY · Priority: P1 · Size: M**

- [ ] Keep artwork, track identity and primary controls visually ordered.
- [ ] Center the active lyric line and soften surrounding lines without reducing readability.
- [ ] Pause auto-scroll while the listener reads ahead, then resume deliberately.
- [ ] Add consistent loading, missing-artwork, missing-lyrics and provider-error states.
- [ ] Keep text contrast readable over artwork-derived ambience.
- [ ] Respect reduced-motion preferences and stop unnecessary work while paused/off-screen.

**Acceptance:** the player is coherent across artwork palettes; text remains readable; no continuous animation runs without a visible consumer.

**References:** [PlayerPanes.kt](../app/src/main/kotlin/dev/musicforge/ui/player/PlayerPanes.kt) · [ArtworkAmbience.kt](../app/src/main/kotlin/dev/musicforge/ui/theme/ArtworkAmbience.kt) · [BitChord PlayerLyrics.kt](https://github.com/kushagrasinghx/BitChord/blob/main/sharedUi/src/jvmSharedMain/kotlin/com/music/bitchord/ui/player/PlayerLyrics.kt) · [BitChord ArtworkPalette.kt](https://github.com/kushagrasinghx/BitChord/blob/main/sharedUi/src/jvmSharedMain/kotlin/com/music/bitchord/ui/theme/ArtworkPalette.kt)

### Q10 · Small details that make the app feel finished
**Status: LATER · Priority: P2 · Size: S**

- [ ] Remember the preferred player pane if behavior remains predictable.
- [ ] Review queue reorder/remove/undo affordances and accessibility labels.
- [ ] Add compact, truthful quality/format detail where useful.
- [ ] Review haptics and pressed states for consistency, not quantity.
- [ ] Keep skeletons, pull-to-refresh and tab reselection consistent.
- [ ] Recheck mini-player/nav glass performance on the target Snapdragon 680.

**Acceptance:** small controls are consistent, discoverable and accessible; no visual effect causes measurable regression.

## Reliability and release quests

### Q11 · Protect playback, queue and download reliability
**Status: READY · Priority: P0 · Size: M**

- [ ] Test expired stream URL and failed re-resolution paths.
- [ ] Verify range-resume never splices bytes from different renditions.
- [ ] Test queue restoration when songs are missing or the saved queue is malformed.
- [ ] Test interrupted exports, duplicate downloads and failed artwork repair.
- [ ] Keep backup/restore safety and schema migration tests green.

**Acceptance:** errors are recoverable, no partial data is presented as complete, and library data is preserved.

**References:** [StreamResolver.kt](../app/src/main/kotlin/dev/musicforge/playback/StreamResolver.kt) · [ChunkedDataSource.kt](../app/src/main/kotlin/dev/musicforge/playback/ChunkedDataSource.kt) · [QueueStore.kt](../app/src/main/kotlin/dev/musicforge/playback/QueueStore.kt) · [MusicDatabase.kt](../app/src/main/kotlin/dev/musicforge/data/db/MusicDatabase.kt) · [scripts/check.sh](../scripts/check.sh)

### Q12 · Measure performance before adding more effects
**Status: READY · Priority: P1 · Size: S/M**

- [ ] Capture startup, scrolling and player-open baselines on the target phone.
- [ ] Check idle CPU while paused and while lyrics are visible.
- [ ] Measure liquid glass against solid/artwork fallback.
- [ ] Keep artwork decoding sized to its display slot and avoid repeated palette extraction for the same track.
- [ ] Record device, build variant and method in docs/BENCHMARKS.md.

**Acceptance:** measurements are reproducible and no new animation/backdrop introduces a material regression.

## Agent workflow questline: Hermes + FCC

This is a tooling decision, not a prerequisite for shipping player features.

### Q13 · Decide the right job for Hermes and FCC
**Status: DECISION · Priority: P1 · Size: S**

**Working hypothesis:** FCC and Hermes are not direct substitutes. FCC is the provider/router and launcher layer; Hermes is an agent harness with persistent memory, skills, tools and delegation. FCC's current README lists a Hermes launcher (fcc-hermes) and configurable fallback models. The launcher routes attached Hermes sessions through FCC; changing provider inside Hermes can intentionally leave the FCC route, so verify the active route rather than assuming all requests use FCC. The useful experiment is to compare:

1. **FCC + a coding harness:** current baseline using existing Claude Code/OpenCode/Kilo workflows with FCC routing.
2. **Hermes through FCC:** Hermes launched with fcc-hermes and using FCC's configured model/router.
3. **Standalone Hermes:** Hermes configured directly with a provider.

**Research signal, not a verdict:** Reddit reports are mixed. Several users describe Hermes as useful for persistent memory, remote/background work and orchestration, while delegating implementation to Claude Code, Codex or OpenCode. Others report that Hermes can overrun a task, consume more context than expected, or be less consistent on complex code than a dedicated coding harness. These are individual anecdotes, not controlled benchmarks. Model, provider, tools and task design can dominate results.

- [ ] Confirm current FCC version's fcc-hermes launch and provider behavior from installed-version help/docs.
- [ ] Run the same bounded MusicForge task through all three configurations on a disposable branch/worktree.
- [ ] Use a testable task, such as adding lyrics parser tests without changing runtime behavior.
- [ ] Score correctness, tests, scope discipline, tool reliability, provider usage, wall time and human repair required.
- [ ] Test fallback after provider error and prevent uncontrolled retry loops.
- [ ] Keep the winning setup only if measured results beat the current baseline.

**Safety and workflow**
- Do not give an autonomous agent unrestricted access to secrets, signing keys, cookies or real user backups.
- Use a dedicated branch/worktree; inspect diffs before accepting them.
- Run scripts/check.sh after each agent run.
- Provider quotas and terms change; do not assume a model remains free forever.
- Do not let Hermes and a coding harness edit the same worktree concurrently.

**References:** [FCC README](https://github.com/Alishahryar1/free-claude-code) · [Hermes Agent](https://github.com/NousResearch/hermes-agent) · [Hermes tools docs](https://github.com/NousResearch/hermes-agent/blob/main/website/docs/user-guide/features/tools.md) · [Hermes fallback providers](https://hermes-agent.nousresearch.com/docs/user-guide/features/fallback-providers) · [Reddit: Hermes vs Claude Code, 25 Oct 2026](https://www.reddit.com/r/hermesagent/comments/1xey47r/hermes_vs_claude_code/) · [Reddit: Hermes and strict task boundaries, 23 Sep 2026](https://www.reddit.com/r/hermesagent/comments/1wo8x6r/anyone_using_hermes_with_codexclaude_code_a/) · [Reddit: Hermes as orchestrator, 25 Oct 2026](https://www.reddit.com/r/hermesagent/comments/1xf3vml/how_are_you_guys_using_hermes_as_a/) · [Reddit: coding harness comparison, LocalLLaMA](https://www.reddit.com/r/LocalLLaMA/comments/1w8f7bp/which_agent_harness_do_you_use_and_why/)

### Q14 · Triage existing source TODOs
**Status: LATER · Priority: P2 · Size: S/M**

The main-branch code search found two explicit TODO groups. They are not release blockers, and the transform TODO sits inside sensitive vendored rendering code.

- [ ] Review the two album paths in `innertube/.../YouTube.kt` that currently set `explicit = false`; identify whether the response/model exposes an explicit-content badge and test the mapping before changing it.
- [ ] Only investigate the `LayerBackdrop.kt` outer-transform fallback TODO if a reproducible coordinate issue is found; preserve upstream attribution and add a targeted regression test.
- [ ] Search TODO/FIXME again before taking the quest, because source branches may have moved.

**Acceptance:** metadata is mapped from real response data with tests; any renderer change is justified by a reproducible issue, not a TODO alone.

## Deferred ideas

- Crossfade: README defers it to a dedicated phase because two decoders are costly on the target Snapdragon 680.
- Full translation/romanization before source provenance and lyric format handling exist.
- More live blur/glass surfaces before performance is measured.
- Replacing the entire lyrics system or player in one rewrite.
- Adding more AI agents without an apples-to-apples task evaluation.

## Current source of truth

This board consolidates the Sonata roadmap from [README.md](../README.md#roadmap), phase status from [CHANGELOG.md](../CHANGELOG.md), and the lyrics/player implementation review. README and changelog describe some Sonata items as active; verify current source before changing status. This board is a plan, not a claim that tasks have been implemented or tested.
