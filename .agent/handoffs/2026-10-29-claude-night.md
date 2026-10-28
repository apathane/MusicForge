# Handoff: Claude (Opus), night of 2026-10-28

Night mode: maintenance, cleanup, a regular release, and the first playground features.

## Done
- **0.14.0 · S0nata** published as the regular release (tag `v0.14.0`, code 1409). APK checksum and the permanent signer were verified after download.
- `publish-release.yml` replaces the P4-only workflow: it reads phase/patch/preview from `app/build.gradle.kts` and the title/notes from `.github/release-notes.md`, and publishes once per version.
- Pre-release review (Hermes) triaged by hand; fixes: seek-bar semantics for TalkBack, stable queue row keys, cover URLs keep their query string, sign-in account read off Main.
- Updater hardening on top of PR #3: asset names stripped of paths, old APKs cleared, progress per whole percent.
- Cleanup: committed APKs and an unused `backup_rules.xml` removed; merged and stale branches deleted (p3/*, agent/*, release/p4-updater, release/0.13.1 — its tag stays).
- `playground` branch: hidden Playground section (7 taps on the version) and a listening streak on Stats.
- Agent runner: runs register in a shared folder, cap 3 at once, wait for RAM, a stalled edit task is killed, and empty answers count as failures. `agents.sh` lists every agent and its RAM.

## Open
- Real-device test of the updater (P4 → 0.14.0 is the first real update path).
- Small main-thread I/O items listed on the board.
- `docs/musicforge-quest-board` branch (63 commits of agent docs) is not merged; it overlaps `.agent/` and needs a human call.
- Identity decision (owner).
