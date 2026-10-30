# Code review 0.15 final (verified by coordinator)

Source: read-only via reviews (W2a UI, W2b playback/recommendation), each finding re-checked against the source at 65b4b15.
Paths below are relative to `app/src/main/kotlin/dev/musicforge/`.

## Confirmed
| Sev | Where | Finding |
|---|---|---|
| bug | `ui/screens/TasteScreen.kt:92` | Taste row `TrackRow(..., onClick = {})`: tap does nothing (dead UI). |
| bug | `data/LibraryActions.kt:32,45,56` | Remote like, playlist add and playlist remove failures are only `Timber.w`. Local DB keeps the change; no retry, no user message. Local and remote can drift. |
| bug | `playback/PlaybackService.kt:~457-467` | `YouTube.registerPlayback` failure only logged. Play-history entry lost, no retry. |
| polish | `ui/backdrop/backdrops/LayerBackdrop.kt:168` | Open TODO on fallback calculation. |

## Unverified
| Sev | Where | Finding |
|---|---|---|
| unverified | `ui/home/HomeScreen.kt:358` | Header `combinedClickable(onClick = {})`: may be intended (long-press only). |
| unverified | `playback/PlaybackService.kt:47` | Scope cancelled only in `onDestroy`. Suspected but not reproduced. |

## Rejected
- W2b #15 "`createPlaylist` `.join()` is a no-op": false. `addToPlaylist` is `scope.launch {…}`, which returns a `Job`, so `.join()` waits.
- W2b #1 "zombie coroutines after process kill": not meaningful, a killed process takes its coroutines with it.
- W2b #21 `MusicDao.kt:289` `statsFor()`: file path not found at that line in 65b4b15, not verified.
- W2b #3–#6 severity "data-loss": overstated. The local-first design is intentional; the real issue is silent failure with no retry (see table above).

## Not verified, low priority
W2b #2, #7–#14, #16–#20, #22: plausible race or edge-case claims, no repro. Re-check after the click-through (W1) if a screen shows symptoms.
