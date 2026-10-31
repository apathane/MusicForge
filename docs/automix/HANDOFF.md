# Automix port: handoff (2026-10-31)

Branch with everything: `agent/p15-automix-controller` (CI green at 045b344 before the WIP below).
Owner rules: AGPL-3.0 (done), port BitChord's automix fully incl. both ONNX models, builds on CI
(not the laptop), delegate with Kilo (`kilo run --agent code|ask -m kilo/...`) and via; never
nemotron-lightning. Releases approved under 0.15.x; the automix test APK gets a distinct name and
says it comes from BitChord/Orchard.

## Done (all compiling on CI)
- Licence: LICENSE AGPL-3.0, NOTICE (Orchard, BitChord, Signalsmith, ONNX Runtime, Beat This!, UMX-HQ MIT).
- Ported into `dev.musicforge.automix`: TransitionPlanner/Policy, TrackAnalysis/Features, LoopMix*,
  Beat/VocalTracker, MelSpectrogram, AnalysisStore, TrackAnalyzer (full), AudioDecoder,
  LocalAudioSource, MixWaveform, CrossfadeController (full, 4096 lines), TransitionFilterProcessor,
  AudioBlock/PcmBoundary/PcmEncoding/FloatAudioProcessor, TrackAnalysisState.
- MusicForge stand-ins: AudioCache (same API, over MusicForge caches), AutomixState (BitChord's
  AppSettings flows), SleepTimer (after-track off), isVideoOrigin (false), LyricLine (minimal).
- Native: native/analyzer, native/loopmix, Signalsmith; CMake libs musicforge_analysis/_loopmix;
  NDK 28.2.13676358, CMake 4.1.2 pinned, also in CI and publish workflows. ABIs arm64-v8a, x86_64.
- Settings rows: Crossfade, length (1-12 s), Automix (Keys.AutomixEnabled), Keep albums gapless.
- Wiring map of BitChord's PlaybackService: docs/automix/bitchord-wiring-map.md.

## WIP (uncommitted design, LoudnessProcessor committed)
`playback/LoudnessProcessor.kt`: per-player gain stage (track gain x blend trim), replaces
loudness via player.volume (the controller owns volumes now).

## Next steps, in order
1. PlaybackService: two players from one `buildPlayer(eq, filter, loudness, ownsFocus)`; sink
   processors `arrayOf(eq, transitionFilter, loudness)`; `spare.audioSessionId = player.audioSessionId`.
2. `adoptPlayer(outgoing, incoming)`: move `listener`, swap focus (`setAudioAttributes(attrs, owns)`,
   `setHandleAudioBecomingNoisy`), swap active/spare filter and loudness, `session.player = incoming`,
   then run the onMediaItemTransition body by hand (factor it into `trackBecameCurrent`).
3. Construct CrossfadeController (19 seams; see the map and the constructor) and call `start()`;
   TrackAnalyzer(context, AudioCache(engine.downloadCache, engine.playerCache, engine::storageKey,
   engine.playbackSource())). filters: incoming -> activeFilter, outgoing -> spareFilter (as BitChord).
4. Settings -> AutomixState: smartFadeEnabled = CrossfadeEnabled && AutomixEnabled;
   crossfadeSeconds = CrossfadeEnabled ? round(CrossfadeSeconds) : 0. EQ and skip-silence to both players.
5. Remove the P4 fade (Fade.kt, FadeTest, fadeMs/fadeJob/applyVolume ticker).
6. Copy models to app/src/main/assets: beat_this_final0_q8.onnx, vocals_umxhq_int8.onnx (from BitChord).
7. CI build, emulator check with logcat tags Automix*/CrossfadeController, then the test release.
