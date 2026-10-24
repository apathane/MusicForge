#!/usr/bin/env bash
# The one build gate every agent runs: debug build, unit tests, lint.
# Safe in parallel git worktrees: each gets its own RAM build folder (see build.gradle.kts) and
# project cache, and the JVMs are capped so two checks fit in 14 GB.
set -euo pipefail
cd "$(git rev-parse --show-toplevel)"
[ -f "$HOME/MusicForge/tools/env.sh" ] && source "$HOME/MusicForge/tools/env.sh"
exec ./gradlew --project-cache-dir "/dev/shm/mfgc-$(basename "$PWD")" \
  -Dorg.gradle.jvmargs=-Xmx2g -Pkotlin.daemon.jvmargs=-Xmx1536m \
  :app:assembleDebug :app:testDebugUnitTest :app:lintDebug "$@"
