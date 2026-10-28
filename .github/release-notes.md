MusicForge 0.14.0 · S0nata
The first regular Sonata release: previews 1–4, plus a night of careful fixes.

- In-app updater: daily and manual checks in Settings, preview opt-in, download progress, SHA-256 verification before the installer opens
- Player: full-bleed cover art, output-device picker that survives MagicOS, volume slider with proper touch targets
- Accessibility: TalkBack can now read and move the seek bar; 48 dp targets across the queue and mini player
- Queue: rows keep their identity when the queue changes, so a swipe in progress isn't reset
- Sign-in: an explicit "Use this account" step instead of a reload loop; the account is read off the main thread
- Artwork: signed thumbnails stay signed, missing covers fall back to the video thumbnail, resized Google covers keep their query string
- Updater hygiene: asset names can't carry paths, old update APKs are cleared, progress redraws once per percent
- Repo: old committed APKs removed

Signed with the permanent key: installs over preview 1 and later. Coming from 0.13 or older? Back up, uninstall, install, restore once.
