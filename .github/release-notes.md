MusicForge P7 · 0.15.0 · Toccata — Automix preview 3 (from BitChord)
Fixes the crash in P5 and P6: those builds closed the moment Automix analysed a song. Back up your library first.

What's fixed since P6
- Automix no longer crashes the app. The release build's minifier had renamed classes the analysis runtime looks up by name; it now leaves them alone. Checked on a minified build: both analysis models run and songs analyse with no crash.
- Colours: switches and the crossfade slider use the app's own greys instead of Material's default lavender.
- Shared downloads (one song's file kept for a duplicate) are analysed from that file.

What Automix does
- Settings → Playback → Crossfade, then Automix. Tracks are analysed on the phone (tempo, beats, key, vocals, intro and outro) and blended on the beat: DJ filter sweeps with a bass swap, tempo-matched blends, or an equal-power crossfade at your crossfade length when two songs are too far apart in tempo.
- Loop mix: when a song has an instrumental stretch that grooves, Automix can loop whole bars of it on a tempo that ramps toward the next song, then let go into it. Synced lyrics and the vocal model keep loops out of sung bars, so many pairs still get a normal blend.
- Checked on an emulator: analysis, blends, handoffs and a full loop mix ran end to end. The emulator has no sound, so none of it has been heard yet: tell me how it sounds on your phone.

MusicForge is AGPL-3.0. Automix code: Orchard (SFG545) and BitChord (Kushagra Singh), AGPL-3.0. See NOTICE.
The APK is about 90 MB: it carries the two analysis models and their runtime.
