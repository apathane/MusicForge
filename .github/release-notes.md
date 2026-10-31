MusicForge P4 · 0.15.0 · Toccata
Smaller fixes and the first of the update, taste and crossfade work, from the Toccata final pass.

- Taste: the artists in "Who you play most" open their artist page. They did nothing before.
- Updates: "Skip this version" hides a release until a newer one comes out, and "bring it back" undoes it. When the check fails you now get a plain line: "No connection" or "GitHub is limiting update checks for now", instead of the raw error.
- Playlists and likes: when a change can't reach YouTube Music, you get a short message. The change stays on this phone, so nothing is lost. There is no automatic retry yet.
- Crossfade: the Crossfade length setting now does something. Each track fades in and out over the length you pick (0 to 12 seconds). This is a fade through silence, not an overlap of two songs, and the gapless option is not wired yet.

Not checked on a phone yet: the playlist message, the crossfade sound, and the update install and checksum path. Those are in the next build's checks.

This is a preview. Back up your library first, and report anything device-specific.
