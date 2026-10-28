#!/usr/bin/env bash
# The .agent vault is public. Fail if anything in it looks private: emails (noreply addresses
# are fine), home-directory paths, or token/key/cookie shapes. Run locally or in CI.
set -uo pipefail
cd "$(git rev-parse --show-toplevel)"
hits=$(git ls-files -co --exclude-standard .agent | grep -v '^.agent/private/' | xargs -r grep -nIE \
  -e '[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}' \
  -e '(/home/|/Users/|C:\\Users\\)[A-Za-z0-9._-]+' \
  -e 'gh[pousr]_[A-Za-z0-9]{20,}|sk-[A-Za-z0-9_-]{20,}|AIza[0-9A-Za-z_-]{35}|xox[abp]-[A-Za-z0-9-]{10,}' \
  -e '(SAPISID|__Secure-[0-9A-Z]+PSID)=[A-Za-z0-9_/+-]{12,}' \
  -e 'BEGIN [A-Z ]*PRIVATE KEY' \
  | grep -vE 'noreply@|users\.noreply\.github\.com|example\.(com|org)')
if [ -n "$hits" ]; then
  echo "vault-guard: private-looking content in the public vault (move it to .agent/private/):"
  echo "$hits"
  exit 1
fi
echo "vault-guard: .agent is clean."
