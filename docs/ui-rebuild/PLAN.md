# UI rebuild plan, Toccata (2026-10-31)

Owner's request (2026-10-31): rethink the UI from its foundation; a full rebuild is fine. Study what
the other apps do (features, motion, more), bring in OpenGhost's typing and springs, plan
thoroughly, delegate.

Branch: `agent/p15-ui-rebuild` (from `agent/p15-automix-loop` at the P7 code). Releases go out as
P8, P9… under 0.15.x when a stage below is done and checked.

## 0. Rules this plan obeys

Owner's motion rules (2026-10-31, `docs/motion/motion-plan-0.15.md` on `agent/phase15-final`):

1. Every animation follows the OpenGhost motion system (`.agent/decisions/0002-motion-language.md`).
2. **An exit is the exact reverse of its entrance.** This overrides 0002 §2.3/§2.4, which close
   faster than they open. Exact reverse = the same duration, the entrance curve run backwards
   (cubic-bezier(x1,y1,x2,y2) reversed is (1-x2, 1-y2, 1-x1, 1-y1)), and any fade moved to the
   other end. Drag-driven surfaces get it free: every transform is a function of one offset.
3. Transitions carry a slight 3D tilt (6° `rotationX`, `cameraDistance = 12 * density`).
4. A transition grows from where the user tapped and shrinks back into it.

Project rules (`CLAUDE.md`, `docs/PERSONALIZED-UI-SPEC.md` §3, decision 0002 §3):

- Honor X7b budget (Adreno 610, 90 Hz): animate in `graphicsLayer` / draw phase only, no per-frame
  recomposition, no animated blur, at most two ambient loops on screen, cap staggered arrivals at 5.
- No DI framework (AppGraph), Media3 pinned to 1.10.x, one `:app` module, JUnit4 + Robolectric.
- Signal identity stays: canvas/ink/muted/line tokens, Inter, artwork as the one colour field.
  The rebuild makes it a real system instead of per-screen styling.
- Frequency rule (Emil): what the user does 100×/day (skip, play/pause, tab switch) gets little or
  no motion; occasional surfaces (sheets, pages, menus) get the full treatment.

## 1. What the scans found (verified against source)

Six read-only scans (Kilo, free models) of the reference apps and of MusicForge; findings below were
spot-checked in the source before being used. ArchiveTune is read for ideas only (it forbids AI
contributions; nothing is copied from it) and its scan was too vague to rely on.

### OpenGhost (typing, springs)
- Streaming text: `stream-view.js:4-5` `PACE {lag .28, drain .14, min 40, max 2400, smooth .1}`,
  `WAVE {duration 220, max 90, spread 50}`; rate eases toward `backlog / lag` (or `/ drain` once
  complete) with `1 - exp(-dt / .1)`; only the newest 90 graphemes animate, staggered inside 50 ms;
  each rises .35 em and fades over 220 ms on (.33,1,.68,1). Spans unwrap to plain text afterwards.
- Title writing: `chat-list.js:32` `WRITE {duration 360, step 18, spread 420}` — letters rise .5 em
  from scale .9, 18 ms apart, the whole title inside 420 ms however long.
- Composer wave (`composer-text.js`): each typed character rises .35 em over 220 ms (cubic out);
  deleted characters leave ghosts that shrink and fade over 240 ms, stagger ≤ 12 ms.
- Springs: semi-implicit Euler, 4 ms substeps, velocity kept when the goal moves; settle at
  |error| < .01 and |v| < .05. Values: press [230,27], glide [520,40], highlight fade [320,32],
  smooth height [260,32], dock lens glide [260,28], stretch [520,34], press grow [420,30], slider
  follow [1400,75] / settle [320,26] (`effort-slider.js:5-6`), morph [420,37].
- Stretch along speed (`dock.js`, `effort-slider.js`): stretch goal = min(.28, |v|·.03), drawn as
  scale(1+s, 1-.35s) — the lens squashes in the direction it travels.
- Slider physics (`effort-slider.js`): rubber band past the ends (limit .16, k .55), magnet to ticks
  (reach .22, pull .45), fling adds v·.06 to the goal.
- Spotlight panel: in 133 ms (.37,0,.63,1) from scale(1.09,.96); highlight glides between rows.

### BitChord (automix surfaces, player, lyrics)
- Scrubber marks the planned mix window as a brighter stretch (`ThinSlider.kt:185`), the fill
  breathes on the blend's beat during a mix, and a "Mixing" label crossfades with the quality badge.
- Mini player rises with a rubber band when pulled up: `pullLift = .5·2·ease·(√(1+p/ease) − 1)`,
  ease 72 dp (`MiniPlayer.kt:155-170`), springs back (damping .65).
- Lyrics: word sweep with a 30 dp feathered wipe, per-letter glow (6 dp), duet lane 44 dp, gap dots.
- Queue reorder is placement-only (200 ms, no crossfade ghosting); swipe a row to queue it, with a
  haptic tick when it arms (45 %).
- Scrubber thickens 7 → 12 dp under the finger on a bouncy spring.

### Metrolist (features, lyrics)
- Lyrics anchored at 35 % of the viewport (`ExperimentalLyrics.kt:133`), lines scroll in on
  `tween(750, delay = min(distance·20, 200))` so they cascade (`:734`); grapheme-aware word fill with
  `BreakIterator` (`LyricsLine.kt:108`); a progress ring in gaps between lines.
- Mini player: swipe to skip with sensitivity-scaled thresholds (`MiniPlayer.kt:351`), progress ring
  around the play button drawn in the draw phase.
- Composite download state for albums/playlists; local artist-name aliases; theme colour from art.

### MusicForge today (what is rebuilt)
- 16 routes, 4 tabs; ViewModels with StateFlow (keep). Design system is thin: `Signal.kt`,
  `Motion.kt`, `TypeIn.kt`, `Glass.kt`, `Common.kt`.
- Debt: artwork radii 6/8/10/14/30 dp scattered; three `PillButton`s; two playlist pickers; two
  section headers; hardcoded colours in the player; ~15 animation specs off the Motion tokens;
  Material `ModalBottomSheet` and `DropdownMenu` that don't match the look.

## 2. Architecture of the rebuild

Keep: data layer, ViewModels, `AppGraph`, `PlayerConnection`, navigation routes and arguments.
Rebuild: everything a user sees, on a new kit.

The kit lives in the existing `dev.musicforge.ui.designsystem` package (the foundation; nothing in it
knows about screens). It stays there rather than moving to a new package: a move would churn every
import and collide with worker branches. File names below are the targets:

| File | Holds |
|---|---|
| `Tokens.kt` | colour roles (all M3 roles filled, as in `1307b06`), type roles (Display/Section/Title/Body/Meta from the spec), radii (10/14/22/30 only), spacing (4 dp unit, 20 dp gutter), artwork sizes |
| `Motion.kt` (moved) | curves + their exact reverses, durations, Compose springs from OpenGhost's [k,c] (`dampingRatio = c / 2√k`) |
| `Mirror.kt` | `Curve` + `Mirror`: one object that yields an enter spec and its exact time-reverse for tweens; helpers for fades at either end |
| `GhostTransition.kt` (moved) | page push/pop from the tap origin + tilt (already on this branch) |
| `Physics.kt` | OpenGhost [k,c] springs as Compose specs, and the pure shaping maths: stretch-along-speed, rubber band, magnet-to-step, fling target, BitChord's pull lift |
| `TypePace.kt` + `TypeText.kt` | pacing maths (PACE/WAVE/WRITE, graphemes) + composables `WriteTitle` (OpenGhost chat-list WRITE), `StreamText` (PACE/WAVE model for text that arrives over time), `TypeWave` (composer wave for a text field); graphemes via `BreakIterator` |
| `Press.kt` | press scale 0.96 (`Motion.Press`), icon-button glyph drop 2 dp + alpha .7 |
| `Surface.kt` | `SignalCanvas`, `SignalSurface`, `ArtworkSurface` (spec §3) |
| `Row.kt` | one `SignalRow` for tracks, queue rows, local songs, search results (art, title, meta, reason, trailing slot, swipe actions) |
| `Buttons.kt` | `Pill` (primary/tonal/quiet), `IconDisc`, `Chip` — replaces the three PillButtons |
| `Sheet.kt` | `ForgeSheet`: our own bottom sheet (drag, fling, scrim) with exact-reverse close; replaces `ModalBottomSheet` |
| `Menu.kt` | `AnchoredMenu`: grows from its trigger (scale(1.09,.96) → 1 in 133 ms) and reverses; replaces `DropdownMenu` |
| `Slider.kt` | `PhysicsSlider`: follow/settle springs, stretch, magnet to steps, rubber band, fling (crossfade length, volume, EQ bands) |
| `Scrubber.kt` | seek bar: thickens under the finger, automix window marker, beat-synced fill during a mix |
| `NavPill.kt` | tab bar with a lens that glides between tabs (glide [260,28] + stretch along speed) |
| `Skeleton.kt`, `Progress.kt`, `Toast.kt` | 0002 §2.6–2.9, retuned |

Screens move to the kit one at a time on this branch; the old components are deleted when their
last caller is gone. No feature flag: the branch ships only when every screen is on the kit.

## 3. Motion map (every surface, its spec, its reverse)

| Surface | Entrance | Exit (exact reverse) | Notes |
|---|---|---|---|
| Page push/pop | grow from tap point .86→1, opaque in first 120 ms, 6° tilt, 380 ms EaseDrawer | same curve backwards, fade in last 120 ms | done (`33b3799`); page below scales .96 only |
| Tabs | fade 200 ms | fade 200 ms | 100×/day: no movement |
| Full player | rises out of the mini player: scale .92→1, tilt 6°→0 off the sheet's offset | same function of the offset | done (`754be36`); programmatic open/close switches to a mirrored tween; drag release keeps the spring with velocity |
| Mini player | pull-up rubber band (BitChord formula) | spring back | new |
| Sheets (`ForgeSheet`) | 380 ms EaseDrawer from below, scrim 200 ms | mirrored | drag release: spring [520,40] with fling velocity |
| Menus (`AnchoredMenu`) | 133 ms (.37,0,.63,1) from scale(1.09,.96) at the trigger | mirrored, back into the trigger | row long-press anchors at the press point |
| Nav pill lens | glide [260,28], stretch min(.28,|v|·.03) | — | replaces the colour crossfade |
| Lists (user-caused inserts) | rise 8 dp + fade, 350 ms, stagger ≤5 × 40 ms | mirrored on removal | not on scroll |
| Queue reorder | placement only, 200 ms | — | BitChord |
| Pop (like, downloaded, added) | .85→1, 450 ms EaseBackOut | mirrored | 0002 §2.2 |
| Titles that appear once | `WriteTitle`: 18 ms/letter, ≤420 ms | — | screen headers on first show, new playlist names, restore summary |
| Search field | `TypeWave`: each typed grapheme rises .2 em / 160 ms | deleted graphemes ghost out 240 ms | quieter than OpenGhost's .35 em (typing is frequent); off with reduced motion |
| Lyrics | anchor 35 %, cascade 750 ms + min(d·20,200) ms, grapheme word fill | — | Metrolist + BitChord; draw phase only |
| Scrubber | thickens 5→9 dp on a bouncy spring | — | automix marker + beat pulse |
| Slider | follow [1400,75], settle [320,26], magnet, rubber band | — | crossfade length, volume, EQ |
| Loading | breathe 2.8 s, shimmer 2.2 s (≤1), bar 1.1 s | — | 0002 §2.6-2.8 |

Reduced motion (animator scale 0): movement off, opacity stays, typed text lands whole, loops stop.

## 4. Features to adopt (in rebuild order)

1. Scrubber automix marker + beat pulse + "Mixing" label (BitChord; our port already publishes
   `SmartAnalysis` / transition window — wire only).
2. Mini player pull-up rubber band; play-button progress ring (Metrolist).
3. Swipe a row to queue it, with an arm haptic (BitChord).
4. Lyrics: 35 % anchor, cascading scroll, grapheme-aware word fill, gap indicator.
5. Queue placement-only reorder; composite download badge for albums/playlists.
6. Search typeahead with live media rows (BitChord).

Later (not in this rebuild): artist-name aliases, lyrics share-as-image, listen together, widgets.

## 5. Stages, owners, gates

Each stage is a set of work items with disjoint files, so workers never edit the same file. Workers
are free Kilo/OpenCode models (`kilo run --agent code`), each on its own `agent/p15-ui-*` branch
cut from the stage base; the coordinator (Claude) writes the kit API first, reviews every diff,
merges into `agent/p15-ui-rebuild`, runs CI and records the result on the emulator before the next
stage starts. Native Claude subagents are not used (owner's rule).

| Stage | Work | Owner | Gate |
|---|---|---|---|
| S0 Foundation | `ui/kit` tokens, Motion+Mirror, Springs, Press, Surface, Buttons, Row API | coordinator | CI green; unit tests for MirrorSpec reverse maths, spring stretch/rubber/magnet maths, grapheme split |
| S1 Overlays | `ForgeSheet`, `AnchoredMenu` → migrate TrackActions/CollectionActions/sleep sheet/sort menus | worker A | emulator recording: open/close are mirrors; drag dismiss; back button |
| S1 Controls | `PhysicsSlider`, `Scrubber` (+automix marker/pulse), `NavPill` lens | worker B | recording; gfxinfo ≤2 janky frames in 120 |
| S1 Text | `WriteTitle`, `StreamText`, `TypeWave` + tests | worker C | unit tests (pacing maths, 90-grapheme cap, Zulu clicks/emoji graphemes) |
| S2 Player | mini player (pull, ring), full player on kit, lyrics pane, queue pane | coordinator + worker A | device check by owner (audio) |
| S2 Browse | Home, Search, Library, Downloads on kit | workers B, C | screenshots light/dark; recording of pushes |
| S3 Pages | Collection, Artist, Library lists, Local music | worker A | same |
| S3 Settings | Settings + Equalizer/Taste/Stats/About/Login/Customise home | worker B | same |
| S4 Polish | delete old components, lint, baseline profile, gfxinfo pass per screen | coordinator | CI, gfxinfo table in `docs/ui-rebuild/PERF.md` |

Releases: **P8** after S0+S1+S2 Player (the most visible change: player, sheets, menus, sliders,
typing); **P9** after S3+S4. Each release: R8 check APK installed and driven on the emulator before
publishing (the P5/P6 lesson).

## 6. Worker brief template

Every worker gets: the kit API (file paths, signatures), the exact files it owns, the motion row(s)
from §3 it implements with numbers, the X7b rules, "no Material sheets/menus", "a joke in code you
write" (repo rule), commit author/trailer from `CLAUDE.md`, and "push only your `agent/p15-ui-*`
branch, never force-push". Output is verified by the coordinator against the code; claims without a
passing build are not accepted.

## 7. Risks

- Free models write plausible Compose that does not compile: every worker branch goes through CI
  before review; the coordinator fixes or redoes.
- A custom sheet loses Material's accessibility (focus, TalkBack dismiss): `ForgeSheet` must expose
  a dismiss action and trap focus; tested with TalkBack on the emulator.
- Typing on frequent text annoys: the map above limits it to once-shown titles and a quiet search
  wave; the owner can turn either down after P8.
- The emulator cannot judge smoothness (software GPU drops frames): gfxinfo numbers come from the
  owner's X7b before P9.
