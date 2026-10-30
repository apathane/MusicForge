# Crossfade and automix: BitChord study, 2026-10-31

Source: https://github.com/kushagrasinghx/BitChord (GPL-3.0, latest `main` at b690dc1, read-only clone).
Design doc read: `web/docs/automix.md`. Code read: `shared/.../playback/smart/` (TransitionPlanner, TransitionPolicy, TrackAnalysis, LoopMix).

## Licence: read before taking anything
- BitChord's automix engine is **AutoMix by David Finnegan, AGPL-3.0**, ported into BitChord (`automix.md` says so).
- MusicForge is **GPL-3.0 only**. Copying AGPL code would make the combined app AGPL. Do not copy code from that engine.
- Clean-room rule for us: take the design ideas below and write our own code. Credit the idea sources in the changelog.

## What BitChord does (ideas, not code)
1. Analysis of the playing and next track: tempo, beat grid, downbeat, key, energy curve, intro and outro. Cached.
2. Only the playing and the next song are analysed. A song without a ready mix plays to its end and crossfades there.
3. Planning: the blend starts on a phrase boundary (8 bars) before the outgoing outro. Blend length shortens when the beat grid is weak (confidence < 0.6 → 8 bars, < 0.45 → 4 bars).
4. Mixing: equal-power cross, bass swap at the midpoint, high-shelf cross over the blend. Pairs that can't beatmatch get a filter mix. Only a song with no grid gets a plain 12 s equal-power crossfade.
5. Handoff at the blend midpoint: queue and Now Playing move to the next track there.
6. Pause, seek, Next or another pick cancels a blend in flight.

Settings (from `sharedUi` strings): Playback → Crossfade; Automix [beta]; Automix also in Up Next's overflow menu; a performance mode (`AutomixPerformanceMode`); a per-song analysis status line.

## MusicForge today (confirmed in source)
- `data/prefs/Prefs.kt:93-95`: `CrossfadeEnabled`, `CrossfadeSeconds`, `CrossfadeGapless` exist.
- Nothing in `playback/` reads them. Nothing in `ui/` mentions crossfade.
- So the three prefs are **dead**: no playback effect, no Settings control. They exist for backup-key compatibility (`Prefs.kt:126-132`), which is a good reason to keep the keys, not to leave them unused.

## Options for us (X7b: Snapdragon 680, 16 GB storage class, Adreno 610)
| Option | What it gives | Cost | Fit |
|---|---|---|---|
| A. Volume ramp at track end (single player) | Fade out over N s before the end, fade in at the start of the next | Low: a Player.Listener + a timed volume ramp | Ship first. Honest name: fade, not overlap |
| B. Two ExoPlayers, overlap | Real crossfade, equal-power curves | Medium: second player, audio focus, handoff at midpoint | Second |
| C. Beatmatched automix (BitChord-style) | Phrase-aligned blends, bass swap | High: beat/phrase analysis on-device, CPU and battery cost on this SoC | Later, measured first |

Recommended order: A now (turns the dead prefs into a working Crossfade switch and length), B after A is verified on the device, C only with a battery and CPU measurement on the X7b.

## Settings to surface (keys already exist)
- Crossfade on/off → `CrossfadeEnabled`
- Length 0–12 s → `CrossfadeSeconds`
- Gapless albums skip the fade → `CrossfadeGapless`
- Automix row: shown as "Coming later" with the reason, not as a fake toggle.

## Not done here
No code change. Next step is a branch implementing option A with a device test: two tracks, a fade at the boundary, measured with `dumpsys media.audio_flinger` volume levels or logcat ramp events.
