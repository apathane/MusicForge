MusicForge P6 · 0.15.0 · Toccata — Automix preview 2 (from BitChord)
The second Automix test build: the loop mix is switched on, and lyrics now guide it. Back up your library first.

What's new since P5
- Loop mix: when the end of a song has an instrumental stretch, Automix can loop whole bars of it on a tempo that ramps toward the next song, bring the next song in over its own loop, then let go into it. It only loops bars with no singing, so many pairs still get a normal blend: that's expected.
- Lyrics: synced lyrics now tell the loop mix where the singing is, together with the vocal model.
- Under the hood: BitChord's precision audio path (Float32) now runs around each player, which is what the loop mix needs.

Still true from P5
- Settings → Playback → Crossfade, then Automix. Tracks are analysed on the phone (tempo, beats, key, vocals, intro and outro) and blended on the beat: DJ filter sweeps with a bass swap, tempo-matched blends, or an equal-power crossfade.
- Checked on an emulator: analysis, plans, blends and handoffs run; the loop mix passes its checks and declines on songs whose ending is all vocals. Not yet heard on a phone, and a full loop mix has not been seen yet: tell me how it sounds.

MusicForge is AGPL-3.0. Automix code: Orchard (SFG545) and BitChord (Kushagra Singh), AGPL-3.0. See NOTICE.
The APK is about 90 MB: it carries the two analysis models and their runtime.
