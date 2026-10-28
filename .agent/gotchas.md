# Gotchas

- **fcc version check false alarms.** Under load, `fcc-pi`/`fcc-cline` can fail in seconds with "FCC requires Pi 0.80.4 / Cline 3.0.55 or newer" even though newer versions are installed: the version probe times out. Retry through `tools/fallback-run.sh` or pick another harness.
- **Free models end turns early.** Codex on Gemma 4 exited 0 mid-thought with no changes. Never treat rc=0 as done: check for commits (fallback-run.sh does).
- **Agents forget to commit** (OpenCode finished its edits twice without committing). The coordinator commits after review, or the brief says "commit" in its last line.
- **Free-model code compiles less often than it claims.** It's left missing imports and invented APIs. Every branch goes through CI (`scripts/ci-check.sh`) before merge.
- **Don't run nine agents through fcc at once.** It allows 2 requests per provider: they queue, then time out.
