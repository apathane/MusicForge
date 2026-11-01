# Scrubber CI fix handoff

Branch `agent/p15-scrubber` — commit `90e5976`.

## What was done

Fixed `:app:compileDebugUnitTestKotlin` failing on
`app/src/test/kotlin/dev/musicforge/ui/player/MixPulseTest.kt:23`:
`0f until 1f step 0.01f` — Kotlin's `until` only exists on `Int`, not
`Float`. Replaced it with an integer loop `0..100` scaled to phase
(`i / 100f`). `beatShape` is a raised cosine squared, so it stays in
`0..1` for any phase and the bound assertion still holds.

Only `MixPulseTest.kt` changed (5 insertions, 2 deletions). Design
unchanged; no other files touched. Not pushed — waiting on CI.

## Verification

None local: owner forbids local Gradle builds. CI runs
`scripts/ci-check.sh` after the commit lands.
