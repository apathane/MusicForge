# Update check, device check (branch agent/p15-update-picker, build at b4962fa)

Device: Android emulator (x86_64, API 34), debug build installed with `adb install`.

| Case | Steps | Result | Evidence |
|---|---|---|---|
| Online, nothing newer | Settings → Check for updates | "You're up to date" | the row text, dumped via uiautomator |
| Offline | wifi and mobile data off → Check for updates | "No connection. Couldn't check for updates." | screenshot `shots/p15-update-offline-fixed.png`; logcat `UnknownHostException` |
| Before the fix | same, previous build | raw "Unable to resolve host…" | `shots/p15-update-offline.png` |

Not covered here:
- Update available, skip, bring back: needs a newer release or a test feed. Not run.
- Rate limit (429 / 403 with quota 0): not run; GitHub limit not reachable from the emulator.
- Invalid checksum, install permission prompt: not run.

Note: an earlier "up to date" result came from a build that failed to install (signature mismatch with the CI-signed debug build). It is not counted. The uninstall/reinstall made the build match the branch.
