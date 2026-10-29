# 0002 · Motion language from OpenGhost

**Context.** The owner wants MusicForge's motion, typing and loading to feel like OpenGhost
(`upstream/OpenGhost`, MIT, web: CSS + JS). **Choice:** copy OpenGhost's numbers, not its
techniques — its `filter: blur()`, `@keyframes` and rAF springs become Compose `graphicsLayer` /
draw-phase reads with the same timings. **Rules out:** animated blur (§3), per-frame
recomposition, Material sheets/dialogs, and animating what the user does 100× a day (track skip,
play/pause).

Counts below are declarations in `styles.css` (93 `cubic-bezier()` uses). `--motion-easing` has 6
literal mentions but hundreds of `transition`s; it is the house curve.

---

## 1. Tokens

### Easing

| OpenGhost curve | Count | Where it is used | Compose |
|---|---|---|---|
| `0.32, 0.72, 0, 1` | 6 + `--motion-easing` | the house curve: sheets, panels, rows, glyph paths, plan arcs (styles.css:323) | `EaseDrawer = CubicBezierEasing(.32f,.72f,0f,1f)` |
| `0.34, 1.56, 0.64, 1` | 21 | overshoot: trash lid, lock shackle, dots → close, `approval-pop`, chips | `EaseBackOut` |
| `0.34, 1.45/1.5/1.4/1.36/1.3/1.25, 0.64, 1` | 10/9/6/1/2/1 | same family, softer for bigger elements | `EaseBackOutSoft(.34,1.45,.64,1)` |
| `0.22, 1, 0.36, 1` | 10 | content arriving: link chip, skill-sheet words, media frames, meter arcs | `EaseOutQuint` |
| `0.33, 1, 0.68, 1` | 3 | **per-grapheme typing** (`md-w`, styles.css:4001) | `EaseOutCubic` |
| `0.37, 0, 0.63, 1` | 6 | micro panels/menus open (133 ms), symmetric fast in-out | `EaseInOutSoft` |
| `0.4, 0, 1, 1` | 4 | exits: sidebar away, dock leave | `EaseInQuart` |
| `0.45, 0.05, 0.55, 0.95` | 3 | looping sweeps: text wave, shimmer band | `EaseInOutWave` |
| `0.3, 0.6, 0.2, 1` / `0.55, 0, 0.7, 0.4` | dock | letters flying out of the lens / a name going home | `EaseFlight` / `EaseHome` |
| `0.65,0,0.35,1`, `0.3,1.2,0.4,1`, `0.3,0.7,0.4,1`, `0.45,0,0.35,1`, `0.3,0.7,0.2,1`, `0.25,0.8,0.3,1`, `0.2,0.9,0.25,1`, `0.4,0,0.2,1` | 1-2 each | one-offs (settings lines, transitions) | map to the nearest named token |

### Springs

OpenGhost integrates `v += (goal-h)*k - v*c` per 8 ms substep (mass 1), so `k,c` are
stiffness/damping. Compose: `dampingRatio = c / (2 * sqrt(k))`, same `stiffness = k`.

| `[k, c]` | Ratio | Use | Compose |
|---|---|---|---|
| `[260, 32]` | .99 | smooth height, scrollbar size — critically damped (smooth-height.js:4) | `spring(260f, .99f)` |
| `[230, 27]` | .89 | **press**: glyph drops 2 px, opacity ×.7 (icon-button.js:23,63) | `spring(230f, .89f)` |
| `[320, 32]` | .89 | highlight fade in lists | `spring(320f, .89f)` |
| `[520, 40]` | .88 | highlight **glide** between rows (chat-list.js:4) | `spring(520f, .88f)` |
| `[260, 28]` / `[420, 30]` | .87 / .73 | dock lens glide / press kick (dock.js:50) | `spring(260f, .87f)` |
| `[520,34]` / `[260,24]` / `[320,26]` | .75/.74/.73 | stretch along speed, ghost move/shape, slider settle | `spring(520f, .75f)` |
| `[190,24]`, `[120,20]`, `[500,45]` | .87/.91/1.0 | send-button widen, sidebar split, effort level | match the ratio |

### Durations

| Bucket | Values (count) | Use |
|---|---|---|
| instant | 110 (21), 133 (10) | menu close / open |
| short | 150 (27), 160 (22), 180 (14), **200 (218)** | fades, colours, opacity, scrims |
| short-mid | 220 (13), 250 (40) | per-grapheme text, small transforms |
| mid | 300 (81), 350 (38), 380 (panels, out-of-CSS) | rows, panels, sheets |
| long | 420 (7), 450 (42), 480 (4), 500 (54), 600 (23 = `--motion-duration`), 700-900 hero | arrivals, replies, layout, stat counters |
| ambient | 0.8 spin, 1.1 progress, 1.9 wave, 2.2 shimmer, 2.8 breathe | loops only |

### Copy this
```kotlin
// Lifted from OpenGhost's styles.css; tuned by eye, like a guitar you cannot hear. Copy, don't vibe.
object Motion {
    val EaseDrawer = CubicBezierEasing(.32f, .72f, 0f, 1f)
    val EaseBackOut = CubicBezierEasing(.34f, 1.56f, .64f, 1f)   // overshoot lives past y = 1
    val EaseOutQuint = CubicBezierEasing(.22f, 1f, .36f, 1f)
    val EaseOutCubic = CubicBezierEasing(.33f, 1f, .68f, 1f)
    val EaseInOutSoft = CubicBezierEasing(.37f, 0f, .63f, 1f)
    val EaseInQuart = CubicBezierEasing(.4f, 0f, 1f, 1f)
    val EaseInOutWave = CubicBezierEasing(.45f, .05f, .55f, .95f)
    // Press is 2 px down and a tenth of the glyph's alpha, which is what the ghost's buttons do.
    val Press = spring<Float>(dampingRatio = .89f, stiffness = 230f)
    val Glide = spring<Float>(dampingRatio = .88f, stiffness = 520f)
    val Settle = spring<Float>(dampingRatio = .99f, stiffness = 260f)
    const val Instant = 110; const val Menu = 133; const val Fade = 200
    const val Row = 350; const val Panel = 380; const val Arrive = 450; const val Layout = 600
}
```

---

## 2. Patterns

### 2.1 Row / message enter — rise + fade

- **Looks:** 8 px up, fading in over 350 ms on `EaseDrawer`; the tool row variant starts −4 px
  over 450 ms.
- **Source:** `styles.css:1804-1812` (`message-in`), `:2376`, `:2811`.
- **MusicForge:** rows that appear *because of the user*: queue insert, playlist add, a search
  result page. Not on scroll — LazyColumn items enter by layout; re-animating them on a fling is
  the jank T9 measured.
- **Recipe:** `val t = remember { Animatable(0f) }`, `LaunchedEffect(k) { t.animateTo(1f,
  tween(350, easing = EaseDrawer)) }`, read in `graphicsLayer { alpha = t.value; translationY =
  (1f - t.value) * 8.dp.toPx() }`. Stagger at most 5 items, 40 ms apart.

### 2.2 Pop in with overshoot

- **Looks:** grows from scale 0.85 to 1 with a small overshoot; OpenGhost also blurs it from 8 px.
- **Source:** `styles.css:2555` (`approval-pop`, 450 ms, `EaseBackOut`, stagger `180ms + i*45ms`);
  `mini-pop` at `:16958` comes from `scale(1.09, 0.96)` + blur.
- **MusicForge:** a state the user just caused: heart on a row, download-complete tick, "Added to
  queue" chip, tab badge. Never on a page's first paint.
- **Recipe:** `Animatable(0.85f)` → `animateTo(1f, tween(450, easing = EaseBackOut))`, read in
  `graphicsLayer { scaleX = v; scaleY = v }`. Drop the blur (§3).

### 2.3 Sheet / drawer open and close

- **Looks:** open 380 ms on `EaseDrawer` from 20 px off; close 200 ms on `EaseInQuart`; close is
  always faster. Scrim in 200 ms, out 160 ms.
- **Source:** `side-panels.js:21` `IN = {380, EaseDrawer}`, `:25` `OUT = {200, cubic-bezier(0.4,0,1,1)}`,
  applied at `:175-183`; `--motion-duration: 0.6s` (`styles.css:322`) for the panel's width.
- **MusicForge:** `PlayerSheet.kt` (already an `Animatable` offset, spring 0.86/420), the transfer
  sheet in `DownloadsScreen`.
- **Recipe:** keep the interrupted-drag pattern. Programmatic open/close:
  `tween(380, easing = EaseDrawer)` / `tween(200, easing = EaseInQuart)`; a drag release uses
  `spring(dampingRatio = .87f, stiffness = 520f)` with `initialVelocity` from `VelocityTracker` —
  the physics OpenGhost gets free. Read `offset.value` inside `graphicsLayer`, never in composition.

### 2.4 Menu from its trigger

- **Looks:** 133 ms from `scale(1.09, 0.96)` + opacity 0, anchored at the button; out in 110 ms to
  `scale(1.06)`.
- **Source:** `row-menu.js:8-12` (`OPEN`, `CLOSE`, `FROM`, `AWAY`), used at `:94` and `:175`;
  `place()` at `:99` puts it beside the row, inside the window.
- **MusicForge:** `TrackActions.kt` row menu, the player's more menu, sort menus.
- **Recipe:** set `transformOrigin = TransformOrigin(triggerX / width, triggerY / height)` once in
  `graphicsLayer`, animate one `Animatable` 0→1 with `tween(133, EaseInOutSoft)`, map to
  `scaleX = 1.09f - .09f * v`, `scaleY = .96f + .04f * v`, `alpha = v`. Exit `tween(110,
  EaseInQuart)` to 1.06, anchored at the row.

### 2.5 Typing — letter by letter, word by word

- **Looks:** each grapheme rises 0.35 em and fades from blur over 220 ms on `EaseOutCubic`; only
  the newest ≤90 characters animate, and the stagger fits inside 50 ms so a burst never queues.
  Rate: ≥40 chars/s, ≤2400, backlog ÷ 0.28 while streaming, ÷ 0.14 once complete, smoothed 100 ms.
- **Source:** `stream-view.js:5` (`WAVE`), `:4` (`PACE`), `:149-152`, `:252-262`, `:288-299`;
  `styles.css:4001-4018` (`.md-w`, `md-wave`, `md-focus`). Titles: `chat-list.js:33-42` (360 ms per
  letter, 18 ms step, whole title inside 420 ms) and `styles.css:1281-1291` (rise 0.35 em, blur
  2 px). Words: `styles.css:2531` (`60ms + i*26ms`), `:19792` (`380ms + w*55ms`).
- **MusicForge — where text types in:** a playlist name the user just created or renamed
  (`LibraryScreen`, `LibraryLists`); the restore/import summary line, once; the first-run welcome
  (1× per install). **Not** the now-playing title on every track (100+/day — Emil's frequency rule
  says no animation at all), **not** lyrics (they must track playback; OpenGhost only animates text
  it just received), **not** empty-state bodies (read often; typing delays reading).
- **Recipe:** one progress read in the draw phase; reveal words above ~24 characters:

```kotlin
val measure = rememberTextMeasurer()
val strike = remember { Animatable(0f) }
LaunchedEffect(title) { strike.snapTo(0f); strike.animateTo(1f, tween(420, easing = EaseOutCubic)) }
Canvas(Modifier.fillMaxWidth()) {
    val words = title.split(' ')                    // counts words, so no per-frame composition
    var x = 0f
    words.take((strike.value * words.size).toInt()).forEach { w ->
        val l = measure.measure("$w ", style = style)
        drawText(l, topLeft = Offset(x, 0f)); x += l.size.width
    }
}
```
  Graphemes: `Intl.Segmenter` becomes `java.text.BreakIterator.getCharacterInstance()`; `toList()`
  splits Zulu click letters and emoji. One grapheme is not one char.

### 2.6 Breathing placeholders

- **Looks:** opacity 1 → .45 → 1 over 2.8 s, ease-in-out, no movement.
- **Source:** `styles.css:15146` (browser), `:19963` (plan), `md-media-wait` at `:14296`
  (1.8 s, alpha only), `peek-wait` at `:16504` (1.1 s alternate).
- **MusicForge:** the one big plate waiting for a cover or a chart (Home, collection header).
  `SkeletonRows` pulses 0.45-0.9 over 900 ms; retune to 1.0 ↔ .45 over 2800 ms.
- **Recipe:** `rememberInfiniteTransition` + `animateFloat(1f, .45f, infiniteRepeatable(tween(2800,
  easing = EaseInOutWave), RepeatMode.Reverse))`, read in `graphicsLayer { alpha = v }`.

### 2.7 Wave / shimmer skeleton

- **Looks:** a 100° gradient band sweeps a bar every 2.2 s linearly; on text it is a gradient
  clipped to the letters, 1.9 s on `EaseInOutWave`.
- **Source:** `styles.css:3759-3778` (`.mini-bone`), `:2921-2935` (`compact-wave`).
- **MusicForge:** at most one shimmer at a time — a loading artwork tile, or the now-playing title
  while a stream resolves. Rows keep the breathe from 2.6: eight shimmering rows is eight shaders.
- **Recipe:** `Modifier.drawBehind` with a `Brush.linearGradient` whose offsets come from an
  `Animatable` read inside the lambda; `infiniteRepeatable(tween(2200, LinearEasing))`.

### 2.8 Progress bars

- **Looks:** indeterminate = a 1.1 s band travelling −100 % → 240 % on `EaseDrawer`; determinate =
  a 0.6 s fill; a busy glyph spins 0.8 s linearly.
- **Source:** `styles.css:15018` (`browser-progress`), `:14784` (`browser-spin`), `:8333`
  (`pass-spin`), `:1212` (dash offset, 360 ms).
- **MusicForge:** `LoadingBar.kt` — change the sweep from `tween(1200, LinearEasing), Reverse` to
  `tween(1100, EaseDrawer), Restart`, translating the band instead of reversing it. Downloads use
  the determinate branch with `tween(600, EaseDrawer)`; the bar stays a `drawBehind` rect.

### 2.9 Toast / notify in and out

- **Looks:** slides in from +30 px while fading over the first 420 ms of a 4.2 s life, holds
  ~3.3 s, leaves to +16 px fading over the last 500 ms; its count badge pops 1 → 1.35 → 1.
- **Source:** `styles.css:11209-11235` (4.2 s, `notify-toast`, `notify-count`).
- **MusicForge:** the queue's Undo line and download-finished line. One at a time, never a stream.
- **Recipe:** `AnimatedVisibility(visible, enter = slideInHorizontally(tween(420, EaseDrawer)) { it }
  + fadeIn(tween(420)), exit = slideOutHorizontally(tween(500, EaseInQuart)) { it / 2 } +
  fadeOut(tween(500)))`. Keep the 3.3 s dwell in the ViewModel, not the animation.

### 2.10 Count changes

- **Looks:** the number counts up over 900 ms on an ease-out cubic after a 220 ms beat; digits keep
  their final width so nothing after them shifts. `fontFeatureSettings = "tnum"` or they wobble.
- **Source:** `stats-card.js:272-284`, `styles.css:11230` (`notify-count` scale).
- **MusicForge:** downloaded/total in `DownloadsScreen`, Stats totals, playlist counts after a sync.
- **Recipe:** `animateIntAsState(target, tween(900, easing = EaseOutCubic), label)`. Small numbers
  only; no count-up on a value that changes every second.

### 2.11 Press feedback

- **Looks:** scale 0.96 on a button, 0.95 on a filled one, 0.97 on a small chip; the ghost's icon
  buttons instead drop 2 px and lose 30 % glyph opacity. 100-160 ms, never waits for the click.
- **Source:** `icon-button.js:23` (`press:[230,27]`), `:60-73`; `styles.css:2782`, `:11278`, `:15833`.
- **MusicForge:** `Modifier.pressScale` (`designsystem/Signal.kt`) already does this at 0.97 with
  `spring(0.8, 900)`; retune to `0.96f` and `Motion.Press`. Structure, timing and haptics stay.

### 2.12 Reduced motion

- **Looks:** movement is dropped, not everything: springs snap to target, panels open instantly,
  streaming text lands whole, loops stop, opacity still changes.
- **Source:** 20 `@media (prefers-reduced-motion: reduce)` blocks from `styles.css:1943`;
  `icon-button.js:63-65` (`if (reduced) { s[0] = goal; s[1] = 0 }`), `stream-view.js:131`,
  `side-panels.js:175-181` (instant away, no `animate()`).
- **MusicForge:** Compose `tween`/`spring` already scale with Android's animator duration scale.
  Read the scale once for what Compose does not scale (infinite loops):
  `Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)`; at 0, swap
  2.1/2.3/2.4 for `fadeIn(tween(0))` and hold 2.6/2.7 at a static alpha. Never encode meaning in
  motion alone — the state text and content description still say what happened.

---

## 3. Budget — Honor X7b (Adreno 610, 90 Hz)

- **`graphicsLayer` and draw-phase reads only.** If it changes layout (width, height, padding,
  `Arrangement`), it is wrong. OpenGhost animates `height` and `grid-template-rows` (smooth-height,
  stats card); those do not port — fixed row, animate alpha.
- **No per-frame recomposition.** Animated values are read inside `graphicsLayer { }`,
  `drawBehind`, `drawWithContent` or `Canvas` — not in composable parameters, `Text` arguments or
  `Modifier.offset(dp)`.
- **No blur.** The 2-12 px `filter: blur()` transitions (menu, mini chat, approval words, typed
  letters) become alpha + scale. The only blur stays the pre-baked backdrop.
- **Layer count.** 20 staggered rows = 20 offscreen buffers; cap animated arrivals at 5 (2.1).
- **Loops:** at most two ambient loops on screen (one skeleton, one progress), each a single layer
  on `infiniteRepeatable`, stopped when the screen is not resumed.
- **Measure:** `adb shell dumpsys gfxinfo dev.musicforge.app.debug` before and after. A pattern
  that drops more than 2 frames in 120 on this device loses duration, not correctness.
- **Interruptibility** is part of the budget: every sheet, drawer and menu must reverse mid-flight
  (2.3, 2.4). OpenGhost's `@keyframes` are entrance-only for that reason, and so are ours.
