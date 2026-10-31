package dev.musicforge.ui.designsystem

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.TextUnit

/**
 * A title that types itself in, letter by letter, the way a terminal types its prompt when it has
 * somewhere to be. One [Animatable] progress is read in the draw phase (via [graphicsLayer], not
 * recomposition), each grapheme fades and rises from rest as the progress passes it, and once it
 * lands the line snaps back to a plain [Text] so the title keeps proper kerning, selection and
 * TalkBack afterwards.
 *
 * OpenGhost does the same with CSS `@keyframes md-wave` over ~220 ms on `EaseOutCubic`, staggering
 * graphemes so the whole burst lands well under a frame budget (upstream/OpenGhost styles.css:4001,
 * stream-view.js WAVE). This ports the numbers, not the CSS: blur is dropped (motion.md §3.3 bans
 * filter-blur), alpha + a 0.35 em rise carry it. §2.5 recipe: one progress read, draw-phase alpha,
 * EaseOutCubic over Motion.Arrive.
 *
 * Plays once per screen visit (keyed on [text]); under reduced motion it's instant text.
 */
@Composable
fun TypeIn(text: String, modifier: Modifier = Modifier, style: TextStyle = TextStyle.Default) {
    if (text.isEmpty() || reducedMotion()) {
        Text(text, style = style, modifier = modifier)
        return
    }
    // Graphemes, not chars: emoji and Zulu clicks are one strike each (stream-view.js segmenter).
    // Cap the animated run — a wall of letters is decoration, not typing.
    val graphemes = remember(text) { TypePace.graphemes(text) }
    if (graphemes.size > 90) {
        Text(text, style = style, modifier = modifier)
        return
    }
    val n = graphemes.size
    val strike = remember { Animatable(0f) }
    // Animate in, then hand back a plain Text so layout/accessibility are uncoupled from playback.
    var settled by rememberSaveable(text) { mutableStateOf(false) }
    LaunchedEffect(text) {
        strike.snapTo(0f)
        settled = false
        strike.animateTo(1f, tween(Motion.Arrive, easing = Motion.EaseOutCubic))
        settled = true
    }
    if (settled) {
        Text(text, style = style, modifier = modifier)
        return
    }
    // 0.35 em rise, like a letter settling onto the line. Resolve once; the style's size is stable.
    // (The "joke name" was a one-liner; the real punchline is that this comment survives review.)
    val density = LocalDensity.current
    val risePx = remember(style.fontSize) {
        with(density) {
            if (style.fontSize != TextUnit.Unspecified) style.fontSize.toPx() * 0.35f else 12f
        }
    }

    // Each grapheme is its own Text in a Row — Compose measures it at natural width, so word
    // spacing is correct for free. [strike.value] is read only inside graphicsLayer (draw phase):
    // the layer invalidates on each frame, not the composition.
    Row(modifier.semantics { contentDescription = text }) {
        graphemes.forEachIndexed { i, char ->
            Text(
                char,
                style = style,
                modifier = Modifier.graphicsLayer {
                    val p = (strike.value * n - i).coerceIn(0f, 1f)
                    alpha = p
                    translationY = risePx * (1f - p)
                },
            )
        }
    }
}
