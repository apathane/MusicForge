# Handoff — Hermes (Nemotron 3.5 Super VL), 2026-10-28

## Done
PlayerSheet.kt (the only file touched): all three owner bug-list items from the board, :app:compileDebugKotlin passes, committed as 98d139f on p3/sheet (not pushed).

- Output-device button crash: tapping the headphones icon went straight to
  startActivity(Intent("android.settings.panel.action.MEDIA_OUTPUT")) and crashed
  with ActivityNotFoundException on MagicOS. It now goes through startActivitySilently,
  which catches resolution failures (ActivityNotFoundException / SecurityException),
  falls back to Settings.ACTION_SOUND_SETTINGS, and shows
  Toast("Can't open output settings on this phone") if neither resolves.
- Volume slider: 6 dp track wrapped in 21 dp vertical padding (48 dp touch target,
  padding counts toward the target); progressBarRangeInfo + setProgress("Adjust volume")
  semantics so TalkBack can read and adjust it; setStreamVolume is no longer called
  on every drag frame — local fraction updates during the drag and flushes once off
  the main thread (withContext(Dispatchers.Default)) on tap, drag end/cancel, or a
  ~120 ms debounce while dragging.
- Output-device lookup: audioManager.getDevices() now runs off the main thread via
  refreshDevice (withContext(Dispatchers.Default)), used by the initial LaunchedEffect
  and by the audio-device callback/broadcast receiver instead of running synchronously
  in produceState at composition.

## Open
Remaining PlayerSheet.kt review items not in this task's scope, left untouched:
"Next" disabled by colour alone (~451-452), title tap node with empty onClick (~366),
"Play next" 48 dp target (~427). No device/emulator verification (adb off per AGENTS.md);
no unit tests exist for these composables.

## Risks
- Compose UI resolves to 1.13.0-alpha01, not the catalog's 1.12.1 (conflict resolution
  picks the higher version); this version exposes no Role.Slider, so slider state is
  exposed via progressBarRangeInfo + setProgress action. TalkBack behaviour on the X7b
  was not verified.
- flushJob is held in mutableStateOf, so creating/cancelling the debounce job
  recomposes the row — bounded, since the row already recomposes per drag frame.
- OutputDeviceButton shows "Speaker"/headphones icon for one frame until the
  background getDevices() lookup completes.
