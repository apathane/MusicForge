# Gotchas

- **fcc version check false alarms.** Under load, `fcc-pi`/`fcc-cline` can fail in seconds with "FCC requires Pi 0.80.4 / Cline 3.0.55 or newer" even though newer versions are installed: the version probe times out. Retry through `tools/fallback-run.sh` or pick another harness.
- **Free models end turns early.** Codex on Gemma 4 exited 0 mid-thought with no changes. Never treat rc=0 as done: check for commits (fallback-run.sh does).
- **Agents forget to commit** (OpenCode finished its edits twice without committing). The coordinator commits after review, or the brief says "commit" in its last line.
- **Free-model code compiles less often than it claims.** It's left missing imports and invented APIs. Every branch goes through CI (`scripts/ci-check.sh`) before merge.
- **Don't run nine agents through fcc at once.** It allows 2 requests per provider: they queue, then time out.

- **Free-model reviews over-call main-thread I/O.** innertube's suspend calls go through Ktor's OkHttp engine, which does the network on its own dispatcher, so they don't throw NetworkOnMainThreadException on Main. Check the call chain before "fixing" one.
- **Never edit a running shell script in place.** Bash reads scripts as it executes, so an in-place edit to `tools/fallback-run.sh` sent a live run into a loop that never ended. Write the new version to a temp file and `mv` it over the old one: running copies keep the old inode.
