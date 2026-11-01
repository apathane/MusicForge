# Handoff · 2026-11-01 · P9: Forge experience, the Set, local-music safety (Claude, coordinator)

Branch `agent/p15-experience` = latest main (11a3175) + the shipped P4–P8 line (`agent/p15-ui-rebuild`)
+ the work below. PR to main opened for the owner's merge. Published as `p9-0.15.0` (code 1509).

| Work | Branch | Done by | Verified |
|---|---|---|---|
| Scanner never deletes on incomplete/folder/incremental scans; chunked deletes; path as args | agent/p15-local-scan | DeepSeek V4.1 Flash (OpenCode); test-loading fix by coordinator | LocalScanRulesTest in CI |
| Interface selector (Classic default / Forge), `forge.experience`, ExperienceTest | agent/p15-shell | coordinator | CI |
| Forge dock (now playing + lyric line + tab lens) | agent/p15-shell | DeepSeek V4.1 Flash | CI; emulator screenshot |
| BitChord mix view port (MixView.kt) | agent/p15-mixview | Nemotron 3.5 Super (Kilo); import fix by coordinator | CI |
| The Set (Forge queue as a mix) | agent/p15-set | Nemotron 3.5 Super, 2 auto CI fixes | CI; emulator screenshot (honest states) |
| BitChord MixPulse + "Mixing" label; crossfade slider hidden with Automix | agent/p15-scrubber | Nemotron 3.5 Super; test fix by coordinator | CI |
| Local music permission/scan states; READ_MEDIA_AUDIO + READ_EXTERNAL_STORAGE(≤32) were missing from the manifest | agent/p15-local-ux | Nemotron 3.5 Super, 1 auto fix | CI |

Not done / limits:
- Forge covers the shell (dock), the player's queue (the Set) and the mix view; Home, Search, Library,
  Collection, Artist and Settings screens are shared with Classic (same kit pieces). Per-screen Forge
  layouts are the next wave.
- The owner's real test playlist was not found in the workspace; scanner rules are covered by unit tests
  only, not by a real-library run.
- Emulator died twice mid-test (qemu exited, no log, no OOM); the Automix-on run of the P9 R8 build was
  done on the owner's phone instead (see below).
- No independent review pass yet on the merged diff.

Tooling: `tools/queue.sh` (worker queue with CI loop, alerts in tools/briefs/done/ALERTS.log),
`tools/queue-watchdog.sh`, `tools/checkin.sh`, `tools/pull-refs.sh` (refs every 6 h), briefs in
`tools/briefs/` (outside the repo). Decision 0004 records model choice and constraints.
