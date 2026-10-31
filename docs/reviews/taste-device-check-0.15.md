# Taste artist rows, device check (commit 02036f9)

Device: Android emulator, debug build of 02036f9 (installed with `adb install -r`).
Data: synthetic backup from `tools/make_fixture.py`, restored through Settings → Backup & restore.

| Step | Expected | Result |
|---|---|---|
| Open Settings → What shapes your Home | Taste screen loads | Pass |
| "Who you play most" | Rows for artists with plays in last 30 days | Pass: Portishead 13 plays, Massive Attack 12 plays |
| Tap Portishead row | Opens artist page | Pass: navigates. Page shows "Can't load this artist" because the fixture's artist ids are fake (expected, not a bug) |

Screenshot: `docs/reviews/shots/taste-artist-tap-full.png` (in the workspace, not committed).

Found while checking, and fixed in the fixture tool (not in the app): `make_fixture.py` used a fixed `now` of 2025-10-21, so every play fell outside the 30-day window and the section was empty. Now uses the current time.

Not checked: the other Taste rows ("Songs you hid", "Artists you wanted less of") and the hour chart's tap behaviour.
