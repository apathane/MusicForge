# Handoff: GPT-6 (ChatGPT), 2026-10-28

## Who I am

I am GPT-6 running in ChatGPT, acting as a temporary release helper while Claude was unavailable. I used GitHub repository tools to inspect files, review checks, update the PR, merge it, and verify the published release. I did not use a local checkout, run an emulator, or test the updater on a physical phone. The build and signing claims below come from GitHub Actions.

## Done

- **PR #3 merged into `main`:** [updater and release versioning](../../pull/3), merge commit `625f65039dc55827d341a7a63ae281da52cb7cb4`.
- **PR CI passed:** [run 40768137709](../../actions/runs/40768137709). The Android build, tests, lint, and APK artifact steps completed successfully.
- **P4 published:** [`p4-0.14.0`](../../releases/tag/p4-0.14.0), title *The Updater Has Entered the Chat*. [Release workflow 40769284957](../../actions/runs/40769284957) passed.
- The release workflow built and tested the candidate, verified the APK signer against the permanent preview-3 certificate, and published both `MusicForge-p4-0.14.0.apk` and `SHA256SUMS.txt`.
- APK SHA-256 reported by GitHub: `aa37a8e4b818bc8db01a681a962a81c3406c1622dcf4f943a7cb2e091c825ff3`.
- **Versioning scheme:** P4 tag `p4-0.14.0`, app version `0.14.0-p4`, Android version code 1404. The completed `0.14.0` reserves code 1409; `0.14.1` reserves 1419. Keep preview tags compatible with older `v0.14.0-preview.N` releases.
- Opened [issue #4](../../issues/4), a proposal for ranking verified agent contributions. It is a discussion item, not an implementation request.

## What changed

- Added the in-app GitHub Releases updater with manual/daily checks, preview opt-in, progress, checksum validation, and Android installer handoff.
- Added the FileProvider metadata/paths and safer retry behavior.
- Made updater version parsing understand the new preview tag convention and explicit release-note version codes.
- Added tests for preview, stable, patch, legacy-tag, and explicit-code ordering.
- Added a release workflow that refuses to publish without the permanent key, matching certificate, successful build/tests/lint, and a SHA-256 manifest.
- Updated README, changelog, board, and facts to reflect the release scheme and shipped updater.

## Still open

- **Real-device updater test:** verify update discovery, checksum failure handling, Android install-permission flow, and installation over preview 3. Do not describe this as device-tested yet.
- Keep the app's long-term identity decision with the owner.
- Ranking-system proposal #4 needs owner/agent discussion before implementation.

## Working style for the next agent

- Check the current local time in `Africa/Johannesburg`. The owner's intended modes are:
  - **Day:** “underpaid MAPPA animator” mode. Prioritise clear fixes and getting necessary work shipped.
  - **Night:** slow, calm, low-token maintenance. Clean up carefully, fix bugs, document discoveries, and explore fun features on a separate playground branch when they are genuinely useful.
- Prefer small commits, reuse existing findings, delegate isolated review or exploration to subagents when available, and avoid repeated broad scans or expensive builds that do not answer a concrete question.
- Keep `main` reliable. Use reviewable branches for changes; experiments that may never ship belong on a playground branch.
- Do not merge unrelated work just to make the branch list look tidy. No automatic pushes or PRs unless the owner has authorised them. The owner explicitly authorised the P4 merge and publication in this session.
- Claude is expected to return around 23:30 local time; hand it this file and the latest board state rather than assuming it knows this session's work.
