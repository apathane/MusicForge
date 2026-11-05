# BitChord Automix Wiring — Porting Reference

## 1. Players

- **Two ExoPlayer instances** owned by `CrossfadeController`: `active` (backs the MediaSession, audio focus, notification) and `standby` (idle, loaded with the incoming track). Both are full ExoPlayers built identically; roles swap at every transition via `[onHandoff]`.
  - `CrossfadeController:124-126` — `private val active: () -> ExoPlayer`, `private val standby: () -> ExoPlayer`,
  - `CrossfadeController:136` — `private val onHandoff: (outgoing: ExoPlayer, incoming: ExoPlayer) -> Unit`,
  - `CrossfadeController:72-74,97-103` — role-swap semantics: before handoff, `active` is the session player and `standby` is idle; after handoff they trade places. The queue index, metadata, notification and UI flip at `[onHandoff]` (halfway through the blend per the equal-power `sin`/`cos` curve), not at the track boundary.

- **MediaSession**: Owned by `PlaybackService.MediaLibrarySession` (built at `PlaybackService:116-125`). The session player is whichever ExoPlayer `active` currently points at.

- **Track handover**: At transition start, the standby player is loaded with the incoming track positioned at the plan's cue point and started silently (`CrossfadeController:79-80`). The handoff fires at `HANDOFF_AT` (equal-power midpoint); `[onHandoff]` moves the MediaSession, audio focus, listeners and bookkeeping onto the incoming player. No re-buffering or re-seeking is needed because both players own the queue.

## 2. Analysis Pipeline

- **Source**: Analysis audio comes from the `AudioCache` (cached YouTube Opus copies) or, for device-local files, `LocalAudioSource` which reads directly from the file descriptor (`TrackAnalyzer:343`). Streamed tracks never write a second copy via the cache.

- **Phases**: 
  - **Phase 1** — DSP-only pass (native `audio_analysis.cpp`); produces beat grid, energy curve.
  - **Phase 2** — `BeatTracker` ONNX model over the full track; measures BPM, downbeats, phrase boundaries.
  - **Phase 3** — `VocalTracker` open-unmix model; produces vocal activity mask and vocal probability.
  - `TrackAnalyzer:66-78` — `BeatTracker` and `VocalTracker` initialized with model paths from assets, thread count from `AppSettings.automixPerformanceMode.value.inferenceThreads`.

- **Storage**: Results lives in `ConcurrentHashMap<String, TrackAnalysis> results` (`TrackAnalyzer:161`). Persisted to disk via `AnalysisStore { context.filesDir }` (`TrackAnalyzer:204`). A stored result is restored once per session via `restoreOnce` (`TrackAnalyzer:264-275`).

- **URI resolution**: `analysisUriFor` resolves the playback URI to a YouTube Opus URI for analysis (`TrackAnalyzer:882-910`). For non-YouTube/local tracks, a YouTube match is sought via `SourceResolver`/`YtMusicRepository`. The canonical YouTube `v` parameter is used to identify the recording across renditions (`TrackAnalyzer:357,550`).

- **Native + ONNX**: The tracker/ vocal model files are unpacked from the APK assets via `unpackedAsset` (`TrackAnalyzer:95-103`). ONNX Runtime memory-maps these files. The native analyzer (Phase 1) lives in `native/analyzer/`. Model failures are logged via `AnalysisLog.install` (`TrackAnalyzer:84`).

## 3. Transition Execution

- **Scheduling a blend**: `CrossfadeController.considerAutoTransition` / `planAutoTransition` evaluates whether a transition is due, then calls `begin(fadeMs, endMs = duration, smart = false)` (`CrossfadeController:879-1038`). For Automix, `considerSmartTransition` requests analysis, computes a `TransitionPlan` via `planTransition`, and arms the fade.

- **What is ramped**:
  - **Volume**: Equal-power `sin`/`cos` crossfade curve (constant power across the blend) (`CrossfadeController:115-118`).
  - **Filters**: `TransitionFilterProcessor` rides a low-pass over the outgoing track and a high-pass over the incoming track; cutoffs are moved on the beat per the `TransitionPlan.transitionStyle` (`TransitionFilterProcessor:118-121`, `TransitionPlanner:1694-1698`).
  - **Tempo**: `incomingPlaybackRate` stretches/compresses the incoming track to beat-match the outgoing one (`CrossfadeController:360,1049`). The outgoing track is never tempo-shifted (`TransitionPlanner:1154-1155`).
  - **Bass swap**: `bassSwapFraction` (default 0.7) controls when the low end hands over from outgoing to incoming, beat-aligned (`LoopMixSession:205-215`, `TransitionPlanner:606-818`).
  - **Echo out** (Advanced Automix): trailing repeats of the outgoing track's last beat, faded by `ECHO_FEEDBACK=0.4` (`TransitionFilterProcessor:524`, `TransitionPlanner:1342`).

- **Loop-Mix renderer pipeline**: 
  - `LoopMixRenderer` (native `libbitchord_loopmix`) holds two decoded regions (decks) looped on whole bars, mixed to one stereo stream (`LoopMixRenderer:13-18`).
  - `LoopMixRenderer.render(frame, interleaved, offset, frames)` is called on the audio thread to mix the two decks (`LoopMixRenderer:57-58`).
  - `LoopMixPort` mediates each player's sink connection to the renderer; `armOutgoing`/`armIncoming` swap the active deck (`LoopMixPort:54-65`).
  - `LoopMixSession` sets gain envelopes (outgoing fades out, incoming fades in), a low-pass/low-end swap, and a high-pass roll-off on the outgoing deck (`LoopMixSession:119-226`).
  - The renderer's `setEnvelope` with `KIND_GAIN`, `KIND_LOW_DB`, `KIND_HIGH_PASS_HZ` controls the cross-fade shape and filter sweeps (`LoopMixRenderer:48-49`).

## 4. User-Visible Crossfade/Automix Settings

From `Prefs.kt`:

| Key                | Type      | Default | UI label (from SettingsScreen)               |
|--------------------|-----------|---------|---------------------------------------------|
| `CrossfadeEnabled` | bool      | —       | (manual crossfade toggle)                   |
| `CrossfadeSeconds` | float     | —       | (manual crossfade duration)                 |
| `CrossfadeGapless` | bool      | —       | (manual crossfade gapless)                  |

Additional playback settings from `SettingsScreen.kt` Section "Playback":
- `AudioNormalization` (bool) — "Even out loudness"
- `Autoplay` (bool) — "Keep playing similar songs"
- `DoubleTapSeek` (bool) — "Double-tap cover to seek"
- `SwipeMiniToSkip` (bool) — "Swipe mini player to skip"
- `ShowLyrics` (bool) — "Show lyrics"
- `LyricsTextSize` (float) — lyrics text size
- `AudioQuality` (choice) — AUTO / HIGH / LOW

Note: `smartFadeEnabled` is referenced in the original BitChord's `CrossfadeController` but does not appear in MusicForge's `Prefs.kt`; Automix is enabled by default when analysis is available.

## 5. BitChord-Specific Dependencies

Any porting app must provide these interfaces/classes:

| Dependency | File:line | Description |
|------------|-----------|-------------|
| `LoopMixRenderer` | `LoopMixRenderer:13-99` | Native renderer (`libbitchord_loopmix`); `available` checks `System.loadLibrary("bitchord_loopmix")`. Provides `setDeck`, `setEnvelope`, `prepare`, `render`, `setFadeOut`. |
| `LoopMixPort` | `LoopMixPort:28-173` | Sink-side port with `@Volatile outArm/inArm/generation`. `armOutgoing`, `armIncoming`, `disarm`. Published state: `sampleRate`, `channelCount`, `precision`, `supportsLoopMix`. |
| `TransitionFilterProcessor` | `TransitionFilterProcessor:28-540` | Float32 state-variable filter (24 dB/oct Butterworth, cascaded second-order sections). `configure(sampleRate, channelCount)`, `setCutoffs`, `process`, `onConfigure`, `onFlush`, `onReset`. |
| `TrackAnalyzer` (BeatTracker + VocalTracker) | `TrackAnalyzer:66-513` | ONNX-model-based analysis: beat grid, vocal mask, energy curve. Requines `Context`, `AudioCache`. Model assets unpacked from APK. |
| `AudioCache` | `LocalAudioSource:1-118` / `TrackAnalyzer:43-44` | Manages cached renditions of analysis URIs; `renditionsOf`, `isComplete`, `requestAnalysisHead`, `cacheKeyOf`. Decides which copy of a recording to analyse. |
| `BitPerfectOutput` | `CrossfadeController:17,970` | Checks whether bit-perfect output is active; if so, Automix is disabled (needs two decoded streams with gain). |
| `AnalysisStore` | `TrackAnalyzer:204` | Persists `TrackAnalysis` to disk (`context.filesDir`). `save(trackId, whole)`, `load(trackId)`. |
| `ConcurrentHashMap`-based result store | `TrackAnalyzer:161-170` | In-memory `results` map keyed by track ID, surviving process death. |
| Native lib `libbitchord_analyzer` | Implicit in `TrackAnalyzer` | Phase 1 DSP analyzer; not separately exposed but required for Phase 1 features. |

## 6. Unverifiable / Unfinished

- **`smartFadeEnabled` setting**: Referenced in the original BitChord `CrossfadeController` (`CrossfadeController:1012`) but absent from MusicForge's `Prefs.kt`. Its presence/absence in the new app would affect whether Automix is gated by a manual crossfade toggle.
- **`onHandoff` integration in PlaybackService**: The `onHandoff` callback is declared by `CrossfadeController` but its concrete invocation is in the service layer (not found in the read files). The handover of MediaSession/focus/listeners at the halfway point needs verification in the actual service code.
- **Full automix UI**: `SettingsScreen.kt` has no dedicated "Automix" section; all Automix control is implicit (analysis availability + `CrossfadeEnabled`). Explicit user controls for Automix on/off, performance mode, or advanced parameters are not present in the readable UI code.
- **`automixPerformanceMode`**: Referenced in the scratchpad `TrackAnalyzer` but not found in the MusicForge codebase grep; may be defined in a different version or build flag.