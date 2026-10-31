# Next Toccata build, release notes (DRAFT, not published)

Publishing needs owner approval (spec section 1). This file is a draft only. The publish workflow reads `.github/release-notes.md`, so the draft is not copied there.

Title: MusicForge P4 · 0.15.0 · Toccata (draft; version code and tag to be decided by the owner)

What changed since P3:
- Taste: artist rows open the artist page.
- Failed YouTube writes say so in a toast. The change stays on the phone. No retry yet.
- Updates: skip a release and bring it back; plain messages when offline or rate-limited.
- Crossfade length works: tracks fade in and out. It is a fade through silence, not an overlap.

Known limits:
- Crossfade has no device audio check yet. The Gapless option is still not read.
- Update-available, skip-and-return and checksum paths are not device-tested.
- The site redesign is not deployed.

Before publishing (owner gate): merge the branches listed in docs/reviews/branch-review-0.15-2026-10-31.md, run the CI build, check the APK on a device.
