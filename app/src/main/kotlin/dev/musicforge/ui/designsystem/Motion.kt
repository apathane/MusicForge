package dev.musicforge.ui.designsystem

import android.provider.Settings
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * Motion tokens, lifted from OpenGhost's styles.css (MIT, upstream/OpenGhost) and translated from
 * CSS/JS into Compose — curves, springs and durations only, never its blur() or rAF spring. Tuned
 * by eye, like a guitar you cannot hear. Copy, don't vibe.
 *
 * OpenGhost counts 93 cubic-bezier() uses; `--motion-easing` is the house curve. The curves below
 * are declarations read straight out of its styles.css and the timing files cited on each line, so
 * a reviewer can diff us against the source one value at a time.
 *
 * See .agent/decisions/0002-motion-language.md §1 (tokens), §2 (patterns), §3 (budget). The device
 * budget is the Honor X7b (Adreno 610, 90 Hz): animate with graphicsLayer / draw-phase reads,
 * springs for gestures, short tweens for fades.
 */
object Motion {
    // --- Easing (cubic-bezier → CubicBezierEasing) ---

    /** The house curve: sheets, panels, rows, glyph paths, plan arcs. styles.css --motion-easing (6 + 1). */
    val EaseDrawer: Easing = CubicBezierEasing(.32f, .72f, 0f, 1f)

    /** Overshoot — content grows past its target then settles. styles.css:2555 approval-pop (21). */
    val EaseBackOut: Easing = CubicBezierEasing(.34f, 1.56f, .64f, 1f)

    /** Softer overshoot family for bigger elements that would feel violent (10/9/6/1/2/1). */
    val EaseBackOutSoft: Easing = CubicBezierEasing(.34f, 1.45f, .64f, 1f)

    /** Content arriving: link chip, media frames, meter arcs. styles.css (10). */
    val EaseOutQuint: Easing = CubicBezierEasing(.22f, 1f, .36f, 1f)

    /** Per-grapheme typing. styles.css:4001 (.md-w) (3). */
    val EaseOutCubic: Easing = CubicBezierEasing(.33f, 1f, .68f, 1f)

    /** Micro panels/menus open, symmetric fast in-out. styles.css (6) */
    val EaseInOutSoft: Easing = CubicBezierEasing(.37f, 0f, .63f, 1f)

    /** Exits: sidebar away, dock leave. styles.css (4) */
    val EaseInQuart: Easing = CubicBezierEasing(.4f, 0f, 1f, 1f)

    /** Looping sweeps: text wave, shimmer band. styles.css (3) */
    val EaseInOutWave: Easing = CubicBezierEasing(.45f, .05f, .55f, .95f)

    /** Letters flying out of the lens / a name coming home. (dock) */
    val EaseFlight: Easing = CubicBezierEasing(.3f, .6f, .2f, 1f)
    val EaseHome: Easing = CubicBezierEasing(.55f, 0f, .7f, .4f)

    // --- Springs. OpenGhost integrates v += (goal−h)*k − v*c per 8 ms substep (mass 1); Compose
    // uses dampingRatio = c / (2 * sqrt(k)) with stiffness = k. ---

    /** Smooth height, scrollbar size — critically damped (smooth-height.js:4). */
    val SmoothHeight = spring<Float>(dampingRatio = .99f, stiffness = 260f)

    /** Press: glyph drops 2 px and loses a tenth of its opacity (icon-button.js:23,63). */
    val Press = spring<Float>(dampingRatio = .89f, stiffness = 230f)

    /** Highlight fade in lists. */
    val HighlightFade = spring<Float>(dampingRatio = .89f, stiffness = 320f)

    /** Highlight glide between rows. (chat-list.js:4) */
    val Glide = spring<Float>(dampingRatio = .88f, stiffness = 520f)

    /** Dock lens glide / press kick. (dock.js:50) */
    val DockSpring = spring<Float>(dampingRatio = .87f, stiffness = 260f)

    /** Stretch along speed, ghost move/shape, slider settle. */
    val Stretch = spring<Float>(dampingRatio = .75f, stiffness = 520f)

    // --- Durations (ms) ---

    const val Instant = 110      // menu close (21)
    const val Menu = 133        // menu open (10)
    const val Fade = 200         // fades, colour, opacity, scrims (218)
    const val Row = 350          // rows (81)
    const val Panel = 380        // out-of-CSS panels (38)
    const val Arrive = 450       // arrivals, replies (54)
    const val Layout = 600       // --motion-duration (23)

    /** Ambient loop cycles: skeleton breathe, shimmer sweep, toast life. */
    const val Ambient = 2800
}

/**
 * Whether the system animator duration scale is 0 (no motion). Compose's own tween/spring specs
 * already scale with the duration scale, so this guards only the infinite loops and draw-phase
 * reveals Compose can't see — see motion.md §2.12. Movement is dropped, not everything: springs
 * snap to target, opacity still changes.
 */
@Composable
fun reducedMotion(): Boolean {
    val resolver = LocalContext.current.contentResolver
    // Read once per resolver — cheap, and the scale is constant for a whole session on the X7b.
    return remember(resolver) {
        Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}
