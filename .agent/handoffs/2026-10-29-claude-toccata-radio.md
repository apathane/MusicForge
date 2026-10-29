# Handoff: Claude, Toccata radio (2026-10-29)

Phase 15 "Toccata". Branch `toccata/radio` sits on 0.14.0 S0nata (code 1409).

## What already exists in baseline (do not duplicate)
- Radio continuation: `PlaybackService.maybeExtendRadio()` calls `YouTube.next(WatchEndpoint(videoId=seed, playlistId="RDAMVM$seed"), continuation)`, de-dups against a `HashSet` of queued mediaIds, marks items `markedFromRadio()`, and triggers at ≤3 remaining (ahead of the 5-item read-ahead cadence).
- Audio quality: `StreamResolver.resolve()` reads `Keys.AudioQuality` → `AudioQuality.parse`; `InnerTubeXPlayer.toInnerTubeX` maps HIGH/LOW/AUTO (AUTO→LOW on metered). 4 MiB chunk (`DEFAULT_CHUNK`) untouched.
- Skip silence: `Keys.SkipSilence` flow → `player.setSkipSilenceEnabled` live.
- Settings: "Playback" section already has the audio-quality picker (AUTO/HIGH/LOW) and skip-silence toggle.

## Changes this session
- `TrackActionsSheet`: added "Start radio" row (uses new `ForgeIcons.Radio`). Long-press on any track (Home, player sheet, queue) seeds the service's radio via a custom command.
- `PlaybackService`: added `ACTION_START_RADIO` custom command + `EXTRA_MEDIA_ID` arg. Seed → clears continuation → `maybeExtendRadio()`. Registered in `availableSessionCommands`.
- `PlayerConnection.startRadio(item)`: sends the custom command through the MediaController (`sendCustomCommand`).
- `MediaItems.kt`: extracted de-dup into two pure functions — `filterFreshRadioIds(ids, queued)` and `List<T>.filterFresh(queued, idOf)` — and routed `maybeExtendRadio` through `filterFresh`.
- `RadioDedupTest`: pure JVM tests (Truth, no Robolectric) for both helpers: keeps fresh, drops queued, order preserved, empty/all-queued cases.

## Commit
`d7d62f6 feat(radio): "Start radio" in the long-press menu + de-dup test`

## Notes for next agent
- Radio threshold currently fires at ≤3 remaining. Task wording said "≈2 items left"; the existing 5-item read-ahead already fetches before drain. No change made — verify against desired latency on device.
- `startRadio` clears any in-flight continuation and re-seeds; concurrent menu taps within one process are safe (single radioJob guard), but a rapid second tap before the fetch resolves could queue two calls — both are idempotent on the server side.
- CI: run `./scripts/ci-check.sh`. Builds go to `/dev/shm/musicforge-build/app`. Never run gradle locally (low RAM).
- No Media3 version bump (pinned 1.10.x). No crossfade (out of scope for T3).

## Unfinished
- None for T3 scope. Crossfade explicitly deferred per task.
