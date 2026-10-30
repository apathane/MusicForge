# 0003 · Phase 15 final UI pass (last feature phase)

Status: active. Owner away; work is unattended. Branch `agent/phase15-final`.

## Scope and limits

- Last feature phase (Toccata 15). Owner: "massively improve ui in this phase".
- Owner has asked for every setting, hidden menu and context option to be clicked through and inspected.
- Limits (standing rules, not negotiable tonight):
  - No release, no tag, no GitHub release. Toccata P1 is already published; do not republish.
  - No push to `main`. Agents push only `agent/**`. Coordinator merges.
  - No signing material in any file, branch, commit, log or `.agent/`.
  - ArchiveTune is off-limits (no AI contributions).
  - Subagents: via only (`via:opencode`, free models). No native Claude subagents.
  - Every new or changed phase gets a from-scratch review before the owner confirms it.

## Workstreams

Each workstream runs in its own git worktree, so two agents never edit the same checkout.
Worktrees live outside the repo (`../wt-<name>`), branches are `agent/<name>`.

| Id | Workstream | Owner (via) | Writes | Done when |
|---|---|---|---|---|
| W0 | This plan | coordinator | `.agent/decisions/0003-*` | committed |
| W1 | Click-through audit on the emulator (every top-level screen, every setting, hidden menus, long-press context menus, top and bottom scroll) | one via agent, drives the device alone | `docs/reviews/clickthrough-0.15-final.md`, screenshots in `docs/reviews/shots/` | every screen in the checklist has a verdict, with a screenshot |
| W2 | Codebase review of Toccata UI (`ui/`, `playback/`, `recommendation/`) for bugs, dead UI and missing states | 2–3 via agents, read-only, one file group each | `docs/reviews/code-review-0.15-final.md` | findings with file:line and a repro |
| W3 | Research: `AgriciDaniel/claude-mods-brain`, plus Claude Code plugins for skill/glowup/status-line ideas | 1 via agent, read-only | `docs/research/plugins-2026-10-30.md` | list of candidates with licence, last push, what they do, risk |
| W4 | UI improvements from W1+W2 findings | coordinator reviews, via agents implement one screen each | code on `agent/ui-<screen>` branches | builds, unit tests and lint pass in CI |
| W5 | Update notification system | via agent, after W4 | code + tests | a new release is detected and shown, verified by fake-feed test |
| W6 | Site redesign (interactive, OS-in-browser feel) | later, separate branch | `site/` | sloplint passes or the failures are listed |
| W7 | Glowup pane (remove unused parts, more features) | later, home folder config, not repo | glowup pack files | owner confirms on wake |

## Rules for agents

- Read `CLAUDE.md` and `.agent/README.md` first. Do not read `keys/`, `*.backup`, or anything under `vault/`.
- Write only in your worktree and only the file set named in your brief.
- Commit small, message says why. Do not force-push. Do not push `main`.
- Phone and emulator: one device, one driver at a time (W1). Other agents do not touch adb.
- Report the exact commands run and the exact output that proves each claim.

## Acceptance for the click-through (W1)

Checklist is generated from the app, not from memory:
1. Launch cold, screenshot.
2. Each bottom tab: top of list, scroll to the bottom, back to top, one row long-press, one overflow menu.
3. Settings: every row opened; every toggle flipped and flipped back; every dialog opened and dismissed.
4. Player: open, scroll queue, long-press a track, overflow menu, lyrics, back.
5. Search: empty state, one query, result long-press.
6. Context menus: each `*Actions` sheet opened.
7. Anything that crashes, hangs over 3 s, or shows a blank page is a bug with a repro.

## Notes for the owner (to read on wake)

- Release decisions are yours. Nothing here publishes.
- The emulator runs the CI debug build from `agent/ci-debug-gate` (`9d21cd6`), not from this branch.
