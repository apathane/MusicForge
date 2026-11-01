MusicForge P9 · 0.15.0 · Toccata — Forge, the Set, and safer local music
A second interface to try, Automix you can watch, and a fix that keeps your local music safe. Back up your library first.

What's new
- Forge: a new interface you can switch on under Settings → Appearance → Interface. Classic stays the default and keeps working exactly as before; switching keeps your music playing and your place.
  - The dock: one capsule at the bottom with what's playing, its current lyric line typing in, and the tabs.
  - The Set: in the player, the queue becomes a mix. Each join between two songs shows the transition Automix has planned there (blend, beats, tempo, key) or honestly says it's still analysing. Tap the next join to open the mix view.
- Mix view (from BitChord): both songs' waveforms laid over each other where they blend, the faders the mix rides, and the playhead moving through it.
- The seek bar shows where the next blend lands, says "Mixing" while one plays, and breathes on the beat.
- With Automix on, the crossfade length is hidden: Automix times each transition from the songs themselves.

Fixed
- Local music: rescanning could delete songs that hadn't changed, or songs outside the picked folder. Scans now only remove files they can prove are gone, and never remove anything when access is missing or a card is unmounted. Folder names with apostrophes work.
- Local music asks for the storage permission it needs and says when it doesn't have it.

Checked on an emulator (no sound there). Tell me how Forge and the Set feel on your phone.

MusicForge is AGPL-3.0. Automix code and the mix view: Orchard (SFG545) and BitChord (Kushagra Singh), AGPL-3.0. See NOTICE. Motion numbers from OpenGhost (MIT).
The APK is about 90 MB: it carries the two analysis models and their runtime.
