# T1 updater reuse — handoff (2026-10-29, kilo)

## Done
- `downloadAndInstall()` no longer wipes `cacheDir/updates`; it reuses a cached APK when the file exists and its SHA-256 matches the release manifest, and otherwise downloads fresh.
- Extracted the reuse-or-download decision into a pure `shouldReuseCachedApk(apkFile, expectedSha256): Boolean` (plus `sha256Hex`) in `Checksums.kt` — tested directly, not via a duplicated test double.
- `isCachedApkValid` and the fresh-download path both route through it, so fail-closed checksum behaviour is identical for cached and remote APKs.
- Fresh path calls `proceedToInstall` (dedup); `expectedChecksum` is the single shared "where is the expected hash" lookup.
- Replaced the broken/duplicated `UpdateCheckerCachedApkTest` with a synchronous JUnit4 + Truth + `TemporaryFolder` test of `shouldReuseCachedApk` (no deprecated `runBlockingTest`).
- CI passed (`scripts/ci-check.sh` → `agent/toccata/updater-reuse`, conclusion `success`).

## Open
- Nothing in this task.

## What I couldn't verify
- No local Gradle (low RAM, per CLAUDE.md); relied on CI for compilation + unit tests.
- Haven't run on the X7b; the reuse path keeps existing behaviour and the installer handoff is unchanged, but real-device install-over-preview on hardware is still a TODO (see board "Real-device updater test").
