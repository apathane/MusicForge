# Phase 15 Proposal: Overture

> **Status: proposed, not started.** This is planning, not a claim that Sonata is finished or Phase 15 approved. **Overture** is a proposed codename only. Do not change app/build.gradle.kts, versionName, versionCode or release tags until the owner decides to begin.

[Quest Board](QUEST-BOARD.md) · [Reference audit](../vault/REFERENCE-AUDIT.md) · [Vault](../vault/README.md)

## The thesis

**Make MusicForge's core listening experience feel unmistakably finished before making it much bigger.**

MusicForge already has hard infrastructure: measured bounded-range downloads, persistent Media3 downloads, cache reconciliation, offline export, local-first recommendations, account/library sync, equalizer, player overlay, line-synced lyrics, artwork ambience and safety backups. Do not rebuild these. Turn the existing foundations into one coherent product.

The phase has three pillars:

1. **Signature Now Playing:** confident composition, artwork-led color, reachable controls, honest audio/output information and a queue that feels considered.
2. **Lyrics that behave like an instrument:** reliable lookup first; word/syllable timing and smooth synchronization after the data model and clock are tested.
3. **Discovery with purpose:** an Explore experience that complements factual, local-first Home instead of cloning a generic feed.

The goal is not the biggest feature list. It is the most coherent listening experience that runs well on the target phone.

## Guardrails

- Target device remains the Honor X7b (Snapdragon 680, Adreno 610, Android 14, locale en-ZA). Emulator-only smoothness is not the shipping gate.
- Keep network, DB, parsing, palette extraction and audio analysis off the main thread.
- Follow current architecture: manual wiring through AppGraph, vendored innertube for YouTube Music APIs, InnerTubeX for stream extraction, Media3 playback/downloads, Room and Preferences DataStore. No new DI framework or parallel YouTube stack.
- Preserve database/table/preference-key compatibility with Metrolist and ConvX backups. Persistence changes require migrations and tests.
- Keep Home local-first and explainable. Label remote content; do not invent content to fill empty shelves.
- No ads, analytics, trackers or hidden network destinations. Optional third-party providers need explicit controls, failure handling and privacy docs.
- Keep live glass to proven low-cost surfaces unless profiling justifies more. Compute artwork ambience once per track and reuse it.
- Never claim lossless, hi-res, bit-perfect, codec, bit depth, sample rate or actual output quality unless live source/route data proves it.
- Run scripts/check.sh and report what really ran. CI also runs innertube tests/lint. Never run data-reset device tooling against irreplaceable real data.
- Do not copy another project's source for speed. Use the product lesson, then implement and test an independent MusicForge solution.

## Phase 0: finish the Sonata hand-off

**Purpose:** do not build Phase 15 on an unverified phase.

Create a Sonata gap matrix from README, CHANGELOG, PERSONALIZED-UI-SPEC, screenshots, source and tests. Mark each promise **done**, **partial**, **missing** or **needs device verification**. A stale roadmap is not proof that a feature remains missing.

### Steps

1. Inspect the active branch's ForgeRoot, PlayerSheet, PlayerPanes, MiniPlayer, StreamResolver, PlaybackService, PlayerConnection and relevant ViewModels.
2. Review player screenshots and walk every control. Record duplicates, placeholders, missing labels and awkward gestures before redesign.
3. Verify two-way history sync on a disposable account/database: remote pull, local play submission, merge/dedupe, sign-out/account switching and partial/empty response handling. Failed sync must not delete local history.
4. Verify volume/output controls, queue changes, seek, pause/resume, system controls, recreation and foreground/background behavior. Mark device-only checks honestly.
5. Compare Explore, Search and collection work against current source. Do not create duplicate pages because an older spec listed them.
6. Resolve documentation contradictions and record which gaps carry into Overture.

**Acceptance:** every Sonata promise has status/evidence; no placeholder control appears functional; sync and backup failures preserve local data; scripts/check.sh passes or its failure is recorded. Hardware-only assertions stay unverified until the device is checked.

Do not start the main Overture build until this gate has a credible status report.

## Phase 1: design the Now Playing signature

**Purpose:** make the most-used screen feel intentional, not merely animated.

### Steps

1. **Audit before changing.** Make a control matrix for artwork, title/artist, like, more actions, seek, transport, shuffle/repeat, volume, output route, quality/source, lyrics, queue, sleep timer and drag gestures. Link each to existing code.
2. **Establish hierarchy.** Artwork anchors the screen. Title and artist are readable. Primary transport is reachable with one thumb. Secondary actions live in a coherent row or sheet.
3. **Make one visual system.** Use artwork-derived color for background, scrubber and selected state. Protect contrast across saturated covers, light/dark and pure-black modes.
4. **Finish artwork composition.** Compare full-bleed with inset artwork using screenshots and the phone. Respect aspect ratio/safe area. Keep transitions short and interruptible. No blur per frame, stacked full-screen glass or perpetual animation.
5. **Make player identity useful.** Source/quality indicator reports known facts. Output pill shows the active Android route when available, not simply the preferred route. Reuse the existing output picker.
6. **Polish gestures/states.** Preserve drag-to-dismiss; lyrics/queue scroll must not drag away the player. Add clear pressed, selected and disabled states. Haptics only when they clarify feedback.
7. **Unify mini-player and navigation.** Keep spacing, icon weight, progress and selection treatment related, but preserve their distinct roles.
8. **Check adaptation.** Portrait, landscape/wide, large font, long labels, missing/loading artwork, offline and accessibility names.

**Acceptance:** all actions have defined behavior; no gesture steals scroll/back; contrast and accessibility are checked; audio labels are truthful; the target phone has no material regression in player-open time, scrolling or idle CPU.

## Phase 2: lyrics reliability before karaoke animation

**Current baseline verified in source:** data/lyrics/Lyrics.kt has Synced, Plain and None. LRC accepts multiple timestamps on one line. Lookup checks Room, LRCLIB exact/search, then YouTube Music plain lyrics. A basic round-trip test exists in download/CoversTest.kt, but no dedicated provider/pipeline suite was found in the initial inventory.

### Steps

1. Expand LRC tests for malformed tags, invalid seconds, empty text/input, one/two/three-plus fractional digits, repeated timestamps, multiple stamps per line, out-of-order lines, Unicode and long lines. Define what is rejected versus normalized.
2. Test exact hit, search hit, empty response, HTTP failure, timeout, cancellation, malformed JSON, cache hit/miss and YouTube fallback. Keep tests deterministic and offline.
3. Fix ambiguous matching. Unknown duration must not favor zero-duration candidates. Define duration mismatch bounds. Use ISRC only with trustworthy metadata; title/artist/album/duration remain fallbacks.
4. Separate provider attempt result from rendered lyrics. Preserve source, format/timing level, match confidence and failure state without exposing provider exceptions to the UI.
5. Bound and cancel requests. One failed source must not block fallback. Avoid duplicate in-flight requests for one track. If sources grow, honour opt-outs strictly.
6. Show loading, cached, synced, plain, unavailable, retry and source states. Never spin forever after a provider fails.
7. Add persistent sync offset only after the pipeline is tested. Explain whether it offsets lyric display or seeks playback.

**Acceptance:** parser/provider tests cover the above; bad remote data cannot crash the player; obsolete work is cancelled on track changes; plain/line fallback remains; disabled sources are never contacted.

## Phase 3: Lyrics v2 with word timing and a proper clock

1. Add an internal model for plain text, line timestamps and word/syllable spans, with an adapter for legacy/cache/imported LRC.
2. Add Enhanced LRC parsing, then TTML only when an actual source and timing semantics are known. Treat XML as untrusted: disable external entities/DTD where supported, bound input size and handle malformed timing/namespaces.
3. Build a pure, separately tested lyric clock. Predict between player samples using a monotonic clock, account for playback speed, smooth small drift and only move backwards after a real seek/skip/restart discontinuity.
4. Keep the clock independent of Composables. Test paused seek, buffering, speed changes, track changes, background/resume and 0.5x to 2x playback.
5. Render active words with restrained emphasis. Preserve punctuation/CJK spacing. If only line timing exists, render line sync honestly.
6. Add source ordering and timing badges only after provider state is trustworthy. Let users read an earlier line without snapping back immediately.
7. Add retry/find lyrics, copy/share selected lines and apply/reset offset. Translation/romanization is a later decision, not a mandatory AI service.
8. Cache format metadata safely. Avoid duplicate documents per view or excessive polling.

**Acceptance:** clock tests cover delayed samples, speed, pause, seek, skip, restart and stalls; no stutter/battery regression; fallback is word to line to plain; source claims are truthful.

## Phase 4: Explore that complements Home

The current source defines Home, Search, Library and Downloads as top-level tabs. README's Sonata description proposed Home, Explore, Library and Search, with Downloads inside Library. This is a navigation decision, not a cosmetic rename. Decide before changing tabs, back stack, deep links or saved state.

### Steps

1. Define Explore's job: **find music the listener did not already know they wanted**, separate from Home's personal/factual reflection and Search's direct retrieval.
2. Start with real catalogue sections already supported by innertube: moods/genres, charts, featured playlists, new releases and editorial collections. Verify endpoints and pagination before coding.
3. Give every shelf provenance and a fitting layout. Never fabricate cover art, rankings or “for you” claims.
4. Build loading, cached/offline, empty, retry and partial-pagination states. One failed shelf must not blank the screen.
5. Reuse current artwork, collection, song-row and open-item components.
6. Keep remote catalogue shelves separate from HomeComposer's local signals.
7. Cancel/replace stale refresh/pagination rather than stacking duplicate requests.
8. Verify phone/wide layouts and offline/slow launch.

**Acceptance:** real responses/pagination; clear provenance; one failure does not take out other shelves; Home remains useful offline; navigation/back stack works after tab switching and reopening the player.

## Phase 5: everyday library details

Prioritize:
1. Collection multi-select and explicit selection mode for bulk actions such as Play Next, add to playlist and download.
2. Consistent Play Next, queue reorder/remove/undo and playlist edits, including serialized rapid writes.
3. Playlist metadata already backed by trustworthy data: count, duration, sorting, cover mosaic, offline/download state and edit actions.
4. Lyrics source, retry, offset, copy/share lines and explanations of codec/output terms.
5. Settings search only if the settings catalogue is large enough to justify indexing.
6. Haptic/pressed-state polish with accessibility/reduced-motion checks.

Do not redo queue persistence, remove-with-undo, pasted URL parsing, playlist insights, haptics, skeleton rows or EQ. Audit code first.

**Acceptance:** bulk actions report outcomes, create no duplicate mappings, recover from failure, and preserve DB/backup compatibility.

## Phase 6: performance, privacy and release hardening

1. Record cold start, player-open latency, long Home/Explore scroll, lyric idle CPU, memory during track changes and download concurrency.
2. Test on the Honor X7b. Emulator performance is directional, not final.
3. Check artwork decode sizing, palette reuse and off-screen animation/polling.
4. Test offline transitions, malformed responses, session expiry, account switching, restore, process death and service reconnect.
5. Audit external requests/tokens. Optional integrations need consent, docs and revocation. Do not upload private history to AI/recommendation services without a separate decision.
6. Treat backups as sensitive, unencrypted archives under current implementation. No cloud backup without encryption design and explicit consent.
7. Run scripts/check.sh and CI's additional innertube tasks. Review diff and update Vault/Quest Board/CHANGELOG, recording device checks not run.

**Release gate:** no unresolved crash/data-loss regression; build/tests/lint and CI pass; target phone verifies smooth playback/player/lyrics/discovery; release notes match real scope.

## Later phases, not Overture

### Phase 16 proposal: Pocket Library
First-class local-file playback/indexing, safe document/folder permissions, local metadata/artwork/lyrics and playlists, with clear identity for local, downloaded and streamed tracks. Design IDs, queue, permissions, migration and backup compatibility first. Current MusicForge is YouTube-video-ID centric.

### Phase 17 proposal: Smart transitions
Evaluate fixed crossfade first and only if two decoders perform on the target phone. Separate tested transition planning from audible execution. Add bounded/versioned/cancellable tempo analysis later. Cancel on user skip, queue edit, disable or player failure. Normal playback wins over speculative mixing. Full Automix stays experimental until battery/memory and transition quality are measured.

### Phase 18 proposal: social and extensible sources
Separately evaluate Listen Together, SMB/WebDAV, recognition, external catalogue/playlist import or add-on providers. Each needs protocol, privacy, permissions, security/trust and compatibility design. Do not create a plugin system merely to avoid writing two providers.

## Explicitly out of scope

- Forking/copying another app's implementation.
- Shipping every theme, integration and setting at once.
- AI DJ/playlists that require paid keys or upload private listening history.
- Unsupported lossless/hi-res/bit-perfect claims.
- Live glass across every surface or video art running behind all screens.
- Cloud backups without encryption and explicit consent.
- Any ArchiveTune upstream interaction. It is read-only feature-level inspiration only. See AGENTS.md and vault/REFERENCE-AUDIT.md.

## Definition of “killer”

A listener can open MusicForge, find music, open a distinctive player, understand what's playing and where audio is routed, follow reliable lyrics without jitter, change the queue without friction and listen offline. It must be fast and dependable on the target phone.

MusicForge should feel like it has a point of view, not like it collected every checkbox from four repositories.
