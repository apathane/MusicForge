# Security Policy

## Reporting a Vulnerability

If you discover a security vulnerability in MusicForge, **do not open a public issue**. Instead, report it privately via **GitHub's private vulnerability reporting** (the "Security" tab on the repository → "Report a vulnerability").

We will acknowledge receipt within 72 hours and work with you to understand and fix the issue. Please include:

- A description of the vulnerability and its impact
- Steps to reproduce (if applicable)
- The app version/codename (e.g., "Rondo 0.13.0") and Android version
- Any relevant logs — **strip cookies, account tokens, and backup file paths before pasting**

## What Not to Post in Public Issues

- **Cookies** (`InnerTubeCookie`, `SESSION`, `SAPISID`, etc.)
- **Backup files** (`.musicforge-backup`, `.metrolist-backup`, `.convx-backup`)
- **Database dumps** or `room_*` files
- **Device IDs**, `visitorData`, or `delegationContext`
- **Po-tokens** or `po_token.html` contents

These contain your YouTube Music identity. Treat them like passwords.

## Supported Versions

Only the **latest release** receives security updates. Older versions (including pre-release builds tagged `*-build-*`) are not supported.

| Version | Supported |
|---------|-----------|
| Latest release (see [Releases](../../releases)) | ✅ |
| Everything else | ❌ |

## Security-Related Design Notes

- The app stores your YouTube Music cookie in DataStore (`Keys.InnerTubeCookie`). It is never logged, never sent anywhere except `music.youtube.com`, and never included in backups exported via the in-app "Export backup" button.
- Downloads are written to the app's private cache (`downloadCache`). They are not world-readable. `MusicExport` copies them to `Music/MusicForge` via MediaStore only when you explicitly choose "Save to files".
- The app does not use analytics, crash reporters, or telemetry. Core network calls to `music.youtube.com` support stream extraction, search and account sync. `lrclib.net` is used for lyric lookup. `api.listenbrainz.org` is contacted only when the listener explicitly enables ListenBrainz scrobbling and supplies a token. These are the documented data destinations; any new destination must be reviewed and disclosed.
- Google sign-in uses a WebView that captures the `music.youtube.com` cookie. The WebView is not reused for anything else.
- The vendored `innertube` module and InnerTubeX (JitPack) handle stream extraction. We pin InnerTubeX to a specific commit in `settings.gradle.kts`.