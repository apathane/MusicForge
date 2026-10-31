MusicForge P5 · 0.15.0 · Toccata — Automix preview (from BitChord)
A test build for Automix: beat-matched blends between songs, ported from BitChord's automix (itself built on Orchard). Back up your library first.

Automix
- Settings → Playback → Crossfade, then Automix. Crossfade length is 1 to 12 seconds.
- Each song and the next one are analysed on the phone: tempo, beats and downbeats (the Beat This! model), key, where the vocals are (Open-Unmix), the intro and the outro. The blend is planned from that: a DJ filter sweep with a bass swap, or an equal-power crossfade, on the beat.
- The next song takes over halfway through the blend; the queue, notification and lyrics move with it.
- Not in this build: the loop mix (bar-looped, tempo-ramped blends). It needs an audio path MusicForge doesn't have yet.
- Checked on an emulator (analysis, plan, blend and handoff all ran). Not yet heard on a phone: tell me how it sounds.

Also in this build
- The song menu no longer shows Download twice; Sleep timer and other rows have proper icons; the Share icon draws correctly.
- Everything from P4: Taste artist rows, update skip and plain errors, playlist sync messages.

MusicForge is now AGPL-3.0 (this build on). Automix code: Orchard (SFG545) and BitChord (Kushagra Singh), AGPL-3.0. See NOTICE.
The APK is larger (about 110 MB): it carries the two analysis models and their runtime.
