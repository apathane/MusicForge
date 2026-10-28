# Agents working on MusicForge

Any coding agent (Claude Code, OpenCode, Kilo, Cline, Copilot, Freebuff) starts here.

0. **Ponytail at ultra, for the whole session.** Lazy senior dev: the smallest change that fully solves the task. Read `~/.claude/skills/ponytail/SKILL.md` and follow its **ultra** rules (question the request, skip anything the need doesn't justify, name what you skipped). Never cut validation, error handling that prevents data loss, security, accessibility, or anything the owner asked for. End with what you skipped and any risk.
0. Read `.agent/README.md`, then `.agent/facts.md`, `.agent/board.md` and the newest `.agent/handoffs/` file: the shared vault every agent reads and writes. Before you finish, add a handoff, update the board, and record any new fact, gotcha or decision there. It is public: private things go in `.agent/private/` (gitignored).
1. Always on, at their highest setting: the `ponytail` skill (ultra: smallest complete change, cut scope rather than stall) and the `caveman` skill (ultracave: terse replies; code, commands and errors stay verbatim). Messages are the biggest token cost.
1. Read `CLAUDE.md`. It's the architecture guide and the project rules, for every agent, not only Claude.
2. Build on GitHub Actions, not locally: commit, then run `scripts/ci-check.sh` (pushes your branch to `agent/<branch>`, waits for CI, prints errors). Fix and repeat until it passes. Local Gradle only if CI is down. Old build gate for reference: `scripts/check.sh` (debug build + unit tests + lint, 0 lint errors). Run nothing else heavier, and never two Gradle commands at once: the dev machine has 14 GB.
3. Work in your own git worktree and branch. Commit small, with a message that says why, ending with the `Co-Authored-By:` line your harness uses. Don't push; the coordinator reviews and merges.
4. Don't use adb, the emulator or the phone unless your task says so. Device checks belong to whoever merges.
5. Stay inside the files your task names. If something outside them must change, ask (Orca: `orchestration ask`) instead of editing.
6. Rules that bite:
   - no DI framework: wire through `AppGraph` (`context.graph`);
   - no Ktor: YouTube goes through the vendored innertube module;
   - tests are JUnit4 + Robolectric + Truth;
   - Compose motion: `graphicsLayer` transform/alpha only;
   - nothing heavy on the main thread;
   - artwork requested at slot size;
   - keep upstream DataStore key names, because backups depend on them.
7. Never commit real user data: backups, cookies, databases.
8. Voice: see "Personality" and "UI voice" under Conventions in CLAUDE.md. Comments explain why and may be funny when something earned it; empty states and errors can have a wink; buttons stay literal.
