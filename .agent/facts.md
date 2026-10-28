# Facts

- **Release naming:** previews use tags like `p4-0.14.0` and app versions like `0.14.0-p4`; patch releases increment the third numeric component (`0.14.1`, `0.14.2`) and may use code-related humorous subtitles. Never compare subtitles to determine update order.
- **Android version codes for phase 14:** preview slots 1–8 sort before slot 9 for the completed patch (P4 = 1404, completed 0.14.0 = 1409, 0.14.1 = 1419). Release notes can provide the explicit code for updater compatibility.
- **P4 updater release:** `p4-0.14.0` publishes `MusicForge-p4-0.14.0.apk` and `SHA256SUMS.txt`; CI verifies the permanent signing certificate before publication. Real-device update/install behavior still needs manual verification.
