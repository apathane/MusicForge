# BitChord automix: how its PlaybackService wires CrossfadeController

Source: BitChord main (b690dc1). Mapped by a Kilo agent (laguna-s-2.1, read-only) on 2026-10-31; file:line refer to BitChord's PlaybackService.kt and CrossfadeController.kt. Used as the guide for wiring MusicForge's PlaybackService. Spot-check line numbers before relying on them.

**(1) Where PlaybackService creates the two ExoPlayers and how each is configured**

Players are built in `setUpMediaSession`/`onCreate` and assigned as roles:
- `PlaybackService.kt:1757` sets `configuredFloatOutput = shouldEnableFloatOutput()`.
- `PlaybackService.kt:1758` builds the active player: `exoPlayer = buildPlayer(spatialAudioProcessorA, equalizerProcessorA, transitionFilterA, loudnessA, ownsSession = true)`.
- `PlaybackService.kt:1765` builds the spare: `sparePlayer = buildPlayer(spatialAudioProcessorB, equalizerProcessorB, transitionFilterB, loudnessB, ownsSession = false)`.
- `PlaybackService.kt:1772-1773` assigns `player = exoPlayer` and `spare = sparePlayer`.
- `PlaybackService.kt:1778` aligns foci: `sparePlayer.audioSessionId = exoPlayer.audioSessionId` (same session id on both sinks).
- `buildPlayer` (`PlaybackService.kt:3445`) is identical for both except the `ownsSession` flag: `ExoPlayer.Builder(this)`, `setRenderersFactory(silenceSkippingRenderers(...))`, `setMediaSourceFactory(requireNotNull(mediaSourceFactory))`, `setLoadControl(farBufferingLoadControl())`, `setAudioAttributes(AUDIO_ATTRIBUTES, ownsSession)`, `setHandleAudioBecomingNoisy(ownsSession)`, `setMaxSeekToPreviousPositionMs(BACK_RESTARTS_AFTER_MS)`.
- `mediaSourceFactory` (`PlaybackService.kt:1749`) = `DefaultMediaSourceFactory(AudioCache.playbackFactory(defaultDataSourceFactory), DsdExtractorsFactory())` with a `PermanentAwareLoadErrorPolicy`.
- Renderers: `silenceSkippingRenderers` (`PlaybackService.kt:6593`) returns a `FfmpegFallbackRenderersFactory` that (a) enables float output per `configuredFloatOutput`, (b) selects a FLAC software decoder on affected Samsung devices, and (c) overrides `buildAudioSink` to wrap a `DefaultAudioSink` (SilenceSkipping + Sonic chain) in a `PrecisionAudioSink` carrying a `DspChain`, all fronted by `BitPerfectRoutingSink/BitPerfectAudioSink`.
- Audio processors / sink placement: `DspChain(spatial, equalizer, transition, loudness)` is constructed at `PlaybackService.kt:6678` inside `buildAudioSink`; the **TransitionFilterProcessor is the third stage of that per-player DspChain**, and **LoopMixPort lives on the filter itself** — `val loop = LoopMixPort()` at `TransitionFilterProcessor.kt:76`. The A/B pair (`transitionFilterA`/`transitionFilterB`) is fixed to the players that own them; `activeFilter`/`spareFilter` (`PlaybackService.kt:627-628`) are the *roles* that trade places at every handoff; roles read fresh each call (`createCrossfadeController` `loopPortOf` at `PlaybackService.kt:1989`).

**(2) Exact body of the onHandoff lambda — how MediaSession player is swapped, listener movement, focus, audioSessionId**

`onHandoff = ::adoptPlayer` (`PlaybackService.kt:1923`). Body of `adoptPlayer(outgoing, incoming)` (`PlaybackService.kt:3471`):
1. `setSessionOwner(outgoing, owns=false)` then `setSessionOwner(incoming, owns=true)` — focus/becoming-noisy handed off by role, outgoing released before incoming claims (`setSessionOwner` at `PlaybackService.kt:3524` calls `setAudioAttributes`/`setHandleAudioBecomingNoisy`). Order avoids holding two focus requests.
2. `outgoing.removeListener(playbackListener)` (`PlaybackService.kt:3475`), `outgoing.removeAnalyticsListener(formatListener)` (`:3476`); fields swap (`player=incoming`, `spare=outgoing`, `activeFilter`/`spareFilter` swap at `:3481-3483`); then `incoming.addListener(playbackListener)` (`:3484`), `incoming.addAnalyticsListener(formatListener)` (`:3485`), `adoptFormatOf(incoming)` (`:3486`). Listeners move *after* the field swap so the first callback already sees the new arrangement.
3. MediaSession swap: `mediaSession?.player = newSessionPlayer(incoming, requireNotNull(crossfade))` (`PlaybackService.kt:3488`) — this is **setPlayer via property assignment**, passing a **ForwardingPlayer** wrapper: `SessionPlayer` is declared `: ForwardingPlayer(player)` at `PlaybackService.kt:7851` (import at `:25`).
4. Explicit `onTrackBecameCurrent(...)` (`:3497`) driven by handoff since the incoming track never fires `onMediaItemTransition` on the new session player.
5. `autoplayLoadJob?.cancel(); ...; loadAutoplayForCurrentTrack()` (`:3509-3512`).

audioSessionId is *not* re-adopted inside adoptPlayer: it is established once at build (`sparePlayer.audioSessionId = exoPlayer.audioSessionId`, `:1778`) and re-aligned only on a full rebuild (`rebuildPlayersForOutput`, `:6938`). Focus + becoming-noisy are moved by `setSessionOwner`; `updateAudioEffectSession` (`:3535`) reads `activePlayer.audioSessionId` and re-broadcasts the open/close audio-effect-control session intents.

**(3) What analysisFor and requestAnalysis are wired to (TrackAnalyzer calls)**

Both are passed at `createCrossfadeController` (`PlaybackService.kt:1924-1929`):
- `analysisFor = { item -> trackAnalyzer.analysisFor(item.mediaId) }` → `TrackAnalyzer.analysisFor(trackId: String): TrackAnalysis` at `TrackAnalyzer.kt:253` (`results[trackId] ?: TrackAnalysis(...)`).
- `requestAnalysis = { item, durationMs -> item.localConfiguration?.uri?.let { uri -> trackAnalyzer.request(item.mediaId, uri, durationMs / 1000.0) } }` → `TrackAnalyzer.request(trackId, uri, durationSeconds)` at `TrackAnalyzer.kt:312`.

Also wired (not asked but referenced alongside): `analysisRunningFor = { item -> trackAnalyzer.isAnalysing(item.mediaId) }` (`PlaybackService.kt:1958`) → `TrackAnalyzer.isAnalysing(trackId)` at `TrackAnalyzer.kt:288`; `mixWaveform`/`loopRegion` also delegate to `trackAnalyzer` (`PlaybackService.kt:1982-2000`). The analyzer instance is `trackAnalyzer = TrackAnalyzer(this, AudioCache)` (`PlaybackService.kt:642`).

**(4) How settings (automix on/off, crossfade length) reach the controller**

The controller reads them **directly off `AppSettings`** rather than as constructor args — there is no automix/crossfade-length parameter in the `CrossfadeController` constructor (`CrossfadeController.kt:121`):
- Automix on/off = `AppSettings.smartFadeEnabled.value`, read inside the controller (e.g. `CrossfadeController.kt:905`, `:1000`, `:1012`, `:1845, :2856`).
- Crossfade length = `AppSettings.crossfadeSeconds.value`, read via `configuredFadeMs()` = `AppSettings.crossfadeSeconds.value * 1000L` (`CrossfadeController.kt:2881`) and consulted at `:1017`, `:1082`, `:1850`.
The service applies runtime speed changes to the controller when not transitioning (`PlaybackService.kt:7095-7098`), and rebuilds players (re-creating the controller via `createCrossfadeController`, `:6957`) only when an output-format setting changes (`outputPcmMode`, float capability, bit-perfect mode — `observeSettings`, `PlaybackService.kt:7029`).

**(5) Every other constructor argument and what PlaybackService passes for it**

From `createCrossfadeController` (`PlaybackService.kt:1919-2004`), the non-default args in declaration order:
- `scope` → `scope` (`:1920`).
- `active` → `{ requireNotNull(player) }` (`:1921`).
- `standby` → `{ requireNotNull(spare) }` (`:1922`).
- `onHandoff` → `::adoptPlayer` (`:1923`).
- `analysisFor` → `trackAnalyzer.analysisFor(item.mediaId)` (`:1924`).
- `requestAnalysis` → `trackAnalyzer.request(..., durationMs/1000.0)` (`:1925`).
- `filters` → anonymous `TransitionFilters` object calling `activeFilter.setCutoffs`/`spareFilter.setCutoffs` (incoming=active, outgoing=spare), `incomingEcho`/`outgoingEcho` (`PlaybackService.kt:1936-1957`); `parkEchoes` touches both `transitionFilterA`/`transitionFilterB` (`:1954`).
- `analysisRunningFor` → `trackAnalyzer.isAnalysing(item.mediaId)` (`:1958`).
- `onArmIncoming` → `spareLoudness().track(item.mediaId, nextId)` (`:1963`).
- `onBlendHeadroom` → `loudnessA.setBlendTrim(trim); loudnessB.setBlendTrim(trim)` (`:1965-1968`).
- `onSettled` → if `boundedEditState.hasPendingEdits` then `scope.launch { applyDeferredBoundedQueueEdit() }` (`:1969-1975`).
- `versionSwapActive` → `versionSwapJob?.isActive == true` (`:1976`).
- `nextStreamReady` → `!DirectSourceGate.isActive() || DirectSourceGate.isStreamReady(item)` (`:1981`).
- `mixWaveform` → `trackAnalyzer.mixWaveform(item.mediaId, uri, durationMs/1000.0, from, to, bins)` (`:1982-1986`).
- `loopPortOf` → `when { target===player -> activeFilter.loop; target===spare -> spareFilter.loop; else -> null }` (`:1989-1995`).
- `loopRegion` → `trackAnalyzer.loopRegion(item.mediaId, uri, durationMs/1000.0, from, to, rate)` (`:1996-2000`).
- `syncedLines` → `::loopMixLyrics` (`:2001`).
- `loudnessGain` → `::loudnessGainFor` (`:2002`).
- `onMixPositionJump` → `(mediaSession?.player as? SessionPlayer)?.announceMixPosition(wrapped)` (`:2003`).

Defaults from the constructor signature (`CrossfadeController.kt:144-232`) are not passed: `analysisFor` default `{ TrackAnalysis() }`, `requestAnalysis` default `{ _, _ -> }`, `filters` default `TransitionFilters.None`, `analysisRunningFor` default `{ false }`, `onArmIncoming`/`onBlendHeadroom`/`onSettled`/`versionSwapActive`/`nextStreamReady`/`mixWaveform`/`loopPortOf`/`loopRegion`/`syncedLines`/`loudnessGain`/`onMixPositionJump` all default to no-op lambdas — every one is overridden by the service above.
