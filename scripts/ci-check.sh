#!/usr/bin/env bash
# Build on GitHub Actions instead of locally (the dev laptop has 14 GB and several agents).
# Pushes HEAD to agent/<branch> (never main), waits for the CI run on that commit, and prints
# the result; on failure, the compiler/lint errors. Exit code = CI's result.
set -euo pipefail
cd "$(git rev-parse --show-toplevel)"
branch=$(git branch --show-current); [ -n "$branch" ] || { echo "detached HEAD: commit on a branch first"; exit 2; }
case "$branch" in main|master) echo "refusing to push main from an agent"; exit 2;; esac
sha=$(git rev-parse HEAD); remote="agent/${branch#agent/}"
git push -q --force-with-lease origin "HEAD:refs/heads/$remote"
echo "pushed $sha to $remote; waiting for CI…"
for i in $(seq 1 40); do
  id=$(gh run list --workflow ci.yml --commit "$sha" --limit 1 --json databaseId -q '.[0].databaseId' 2>/dev/null || true)
  [ -n "$id" ] && break; sleep 15
done
[ -n "${id:-}" ] || { echo "no CI run appeared for $sha"; exit 3; }
gh run watch "$id" --interval 30 >/dev/null 2>&1 || true
result=$(gh run view "$id" --json conclusion -q .conclusion)
echo "CI $id: $result"
if [ "$result" != success ]; then gh run view "$id" --log-failed 2>/dev/null | grep -E 'e: |error:|FAILED|Lint found|Expected' | sed 's/^.*Z //' | head -40; exit 1; fi
