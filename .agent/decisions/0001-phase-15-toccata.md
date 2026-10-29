# 0001 · Phase 15: Toccata (0.15.0)

**Context.** The owner tried 0.14.0 and asked for: an updater that doesn't re-download, transitions
for everything (back currently makes the surface vanish), player controls not cut off at the
bottom, a full-bleed cover without the dark band, an option to hide the top bar, radio / endless
play and the streaming basics, GPU-friendly drawing, loading that is always visible, an app that is
"self-aware", a custom Home with better portraits and listening tracking, and the basic settings
every player has.

**Choice.** Phase 15 is *Toccata* (a fast, touch-driven form: motion, speed and feel). It ships as
previews `p1-0.15.0`, `p2-0.15.0`, … then `v0.15.0`. Work is split into packages that own
separate files so agents can run side by side; the coordinator merges and reviews.

**Guides.** Metrolist for the foundations every player needs (queue, radio, audio settings,
settings layout). ConvX and BitChord for the advanced feel (motion, player, Home, loading, glass).
All three are GPL-3.0 like MusicForge: porting is allowed with a credit line in the commit and a
comment where a piece of logic came across. Read the sources; don't guess their APIs.

**What "self-aware" means here.** The app notices its own situation and says so plainly:
offline, metered or slow network, battery saver, low storage, a failing stream, a stale sync. It
adapts (for example, Home falls back to downloads offline, artwork is requested smaller on a
metered network) and shows one short line about it, never a stream of toasts.

**Rules for every package.**
- Read the skills named in the brief before editing, apply them, and list them in a `Skills:` line
  in each commit body. The runner rejects work that skips them.
- Device budget: Honor X7b (Adreno 610, 90 Hz). Animate with `graphicsLayer` / draw-phase reads,
  springs for gestures, short tweens for fades; respect the system animator scale.
- Settings that MusicForge shares with Metrolist/ConvX keep their key names (`Keys.importable`).
- Build on CI (`scripts/ci-check.sh`), never local Gradle.

**Rules out.** New DI frameworks, Material bottom sheets/dialogs for new UI, live blur behind
scrolling content, invented content on Home.
