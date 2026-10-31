# Motion plan, Toccata final pass (2026-10-31)

Owner's rules (2026-10-31):
1. Every animation follows the OpenGhost motion system (`.agent/decisions/0002-motion-language.md`).
2. An exit is the exact reverse of its entrance.
3. Transitions carry a slight 3D tilt.
4. A transition grows from where the user tapped: mini player → full player out of the mini player; card → page out of the card; closing shrinks back into the origin.

## Current state (checked)
- Route transitions: `ui/ForgeRoot.kt` ~180-241, alpha/slide only. No tilt, no container transform. (via agent, coordinator spot-checked)
- Player open/close: `ui/player/PlayerSheet.kt`, offset + scale; open and close use different curves and durations, so not a reverse. No tilt. Does not grow from the mini player.
- Play/pause: `ui/player/PlayerSheet.kt:588-601` crossfades the icon (140 ms in / 120 ms out); press is `Modifier.pressScale` (`ui/designsystem/Signal.kt:88`, scale 0.96). **There is no 3D tilt anywhere in the UI** (`grep rotationX|rotationY|cameraDistance` outside `ui/backdrop/` is empty). The tilt has to be designed, not reused.
- Tokens: `ui/designsystem/Motion.kt` holds the decision-0002 springs and `reducedMotion()`.

## Building block to add
`Modifier.ghostTransform(key, sharedScope, visibilityScope, tiltDeg = 6f)`:
- `sharedBounds(...)` inside one `SharedTransitionLayout` at `ForgeRoot` for origin-aware container transforms (rule 4).
- `graphicsLayer { rotationX/rotationY from progress × tiltDeg; cameraDistance = 12 * density }` read in the draw phase only (no per-frame recomposition) for the tilt (rule 3).
- One `Motion` spec used for both directions, so exit = reverse of enter (rule 2). Reduced motion snaps.

## Order (smallest first)
1. Song and collection menus (bottom sheets) open from the long-pressed row.
2. Player overflow menu from its button.
3. Mini player → full player (container transform; the hardest and the most visible).
4. Card → album/artist/playlist page.
5. Route transitions in ForgeRoot with tilt.

## Risks on the X7b (Adreno 610, 90 Hz)
- SharedTransitionLayout renders overlays: measure overdraw and frame time per step (`dumpsys gfxinfo`).
- Never animate blur; the Liquid backdrop stays where CLAUDE.md allows it.
- Progress read in graphicsLayer only.

Status: plan only. Not started; Automix comes first (owner).
