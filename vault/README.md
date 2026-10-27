# MusicForge Vault

> **Primary project context for ChatGPT and coding agents.** Start here before discussing, researching or changing MusicForge.

This vault is a maintained map of the project, not a replacement for source code. Code establishes what currently works; tests establish what is exercised; the changelog records shipped history; the roadmap describes intent. When they disagree, record the disagreement and verify the current branch rather than silently blending them.

**Vault status:** initial deep pass completed 2026-10-27  
**Project:** [MusicForge](../README.md)  
**Current review branch:** `docs/musicforge-quest-board`  
**Board:** [Quest Board](../docs/QUEST-BOARD.md)

## Read this first

1. [Current state and immediate priorities](STATE.md)
2. [Project identity and product rules](PROJECT.md)
3. [Architecture and critical data flows](ARCHITECTURE.md)
4. [Subsystem guide and source map](SUBSYSTEMS.md)
5. [Engineering invariants and known doc conflicts](DECISIONS.md)
6. [Build, test and agent workflow](WORKFLOW.md)
7. [Project history and decision context](CONTEXT.md)
8. [Source/test file index](FILE-MAP.md)
9. [Active quest board](../docs/QUEST-BOARD.md)

For a focused task, read this index, STATE, the relevant subsystem section, the actual source files and relevant tests. Do not assume this vault remains current after code changes unless it has been updated.

## Truth labels

- **Verified in source:** directly confirmed in repository code or config during the 2026-10-27 review.
- **Documented:** stated in a project document, but not necessarily verified against runtime behavior.
- **Roadmap:** intended or proposed; not a claim of implementation.
- **Needs verification:** source/docs conflict, implementation behavior is not proven by static reading, or real-device testing is required.

## Operating agreement for future ChatGPT sessions

MusicForge is the user's **first-priority ongoing project** and their main intended use of ChatGPT. Favor MusicForge context when relevant, reuse this vault rather than rebuilding context from scratch, and keep the vault and quest board current as work proceeds.

At the start of a MusicForge task:
- Check the branch and current HEAD, then read this page and STATE.
- Read `AGENTS.md` and `CLAUDE.md` before code work.
- Verify the relevant source and tests on the active branch. This vault was initially written from `main` plus the review-branch documents; don't treat old notes as more authoritative than current source.
- Separate code that's implemented, code that's tested, and features that are only planned.
- Make the smallest change that solves the task. Avoid unrelated refactors.
- Update STATE, the relevant page and Quest Board when findings materially change.
- Never store cookies, account tokens, signing keys, user backups, database dumps or other secrets in this vault.

## What this vault covers

- Product purpose, design language, release/phase status and project context.
- Module boundaries, service and data ownership, playback/download byte path, cache invariants, database/backup compatibility, account sync and recommendations.
- Subsystem-to-file maps and the test inventory.
- Non-negotiable implementation rules, documented inconsistencies and research questions.
- The ordered Quest Board and review workflow.

It is a **living index, not omniscient memory**. If a claim matters for a code change, open the actual file. If the implementation changes, revise the vault in the same focused change whenever possible.

## Source hierarchy

1. Actual source/config and tests on the active branch.
2. `AGENTS.md` and `CLAUDE.md` for working rules and architecture.
3. `CHANGELOG.md` and released tags for history.
4. `README.md` for user-facing product claims.
5. `docs/PERSONALIZED-UI-SPEC.md` for design intent, subject to current implementation.
6. This vault for orientation and cross-links.
7. `docs/QUEST-BOARD.md` for chosen next work. The board is a plan, not proof of completion.

## Maintenance checklist

When code changes:
- [ ] Update [STATE.md](STATE.md) if current status/priorities changed.
- [ ] Update [ARCHITECTURE.md](ARCHITECTURE.md) or [SUBSYSTEMS.md](SUBSYSTEMS.md) if a boundary/data flow changed.
- [ ] Add/update a decision in [DECISIONS.md](DECISIONS.md) if an invariant or trade-off changed.
- [ ] Update [FILE-MAP.md](FILE-MAP.md) if files/modules/tests were added or moved.
- [ ] Update [CONTEXT.md](CONTEXT.md) for durable decisions or phase milestones, not every trivial edit.
- [ ] Update `docs/QUEST-BOARD.md` and `CHANGELOG.md` when appropriate.
- [ ] Verify the docs against current source. Don't mark a feature done merely because it was implemented once.

## Useful links

- [Repository README](../README.md)
- [Contributor guide](../CONTRIBUTING.md)
- [Agent entry rules](../AGENTS.md)
- [Architecture/agent guide](../CLAUDE.md)
- [Changelog](../CHANGELOG.md)
- [Personalized UI spec](../docs/PERSONALIZED-UI-SPEC.md)
- [Transfer/performance benchmarks](../docs/BENCHMARKS.md)
- [Quest Board](../docs/QUEST-BOARD.md)
