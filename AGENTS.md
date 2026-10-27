# Agents working on MusicForge

Any coding agent (Claude Code, OpenCode, Kilo, Codex, Freebuff) starts here.

1. Read `vault/README.md` and `vault/STATE.md` first. This is the maintained cross-session project context and current priority snapshot; treat it as a map, not a replacement for source verification.
2. Read `CLAUDE.md`. It's the architecture guide and the project rules, for every agent, not only Claude.
3. Build gate: `scripts/check.sh` (debug build + unit tests + lint, 0 lint errors). Run nothing else heavier, and never two Gradle commands at once: the dev machine has 14 GB.
4. Work in your own git worktree and branch. Commit small, with a message that says why, ending with the `Co-Authored-By:` line your harness uses. Don't push; the coordinator reviews and merges.
5. Don't use adb, the emulator or the phone unless your task says so. Device checks belong to whoever merges.
6. Stay inside the files your task names. If something outside them must change, ask (Orca: `orchestration ask`) instead of editing.
7. Rules that bite:
   - no DI framework: wire through `AppGraph` (`context.graph`);
   - no Ktor: YouTube goes through the vendored innertube module;
   - tests are JUnit4 + Robolectric + Truth;
   - Compose motion: `graphicsLayer` transform/alpha only;
   - nothing heavy on the main thread;
   - artwork requested at slot size;
   - keep upstream DataStore key names, because backups depend on them.
8. Never commit real user data: backups, cookies, databases.
9. Voice: see "Personality" and "UI voice" under Conventions in CLAUDE.md. Comments explain why and may be funny when something earned it; empty states and errors can have a wink; buttons stay literal.


## Third-party reference boundaries

- Read the [reference audit](vault/REFERENCE-AUDIT.md) before using ideas from other music clients and read the [proposed Phase 15 roadmap](docs/PHASE-15-OVERTURE-ROADMAP.md) before planning phase-level work.
- **ArchiveTune is reference-only, never an AI contribution target.** Its public NO AI / NO LLM policy forbids AI-assisted contributions. Do not fork it, create an ArchiveTune-derived branch/worktree, copy its code/assets/branding/text, open or modify its issues/PRs, post comments/reviews/discussions, submit support requests, or contact its maintainers/community on GitHub, Discord, Telegram, email or elsewhere.
- You may implement independently designed feature-level ideas inside MusicForge. Keep provenance clear, use no ArchiveTune code or assets, and do not imply affiliation or approval. If asked to contact or contribute to ArchiveTune upstream, refuse that action and explain the policy.
- This boundary does not replace licence/provenance review for Convx, BitChord, Metrolist or any dependency. Learn from behavior and architecture first; source reuse requires an explicit review of licensing, notices and project fit.
