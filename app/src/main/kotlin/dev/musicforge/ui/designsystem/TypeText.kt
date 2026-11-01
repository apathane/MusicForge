package dev.musicforge.ui.designsystem

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.TextUnit

/**
 * A title that writes itself in, OpenGhost's chat-list WRITE (chat-list.js:32): each grapheme rises
 * half an em from 90 % size while it fades in over 360 ms on the house curve, the next one 18 ms
 * later, and however long the title, the last letter starts inside 420 ms. One linear clock is read
 * per glyph in the draw phase; each glyph eases its own slice of it. When it has landed the row is
 * swapped for a plain [Text], so kerning, selection and TalkBack are the ordinary ones.
 *
 * Plays once per distinct text. Long titles (over 90 graphemes) and reduced motion get plain text:
 * nobody waits for a wall of letters, not even a wall of letters.
 */
@Composable
fun WriteTitle(text: String, modifier: Modifier = Modifier, style: TextStyle = TextStyle.Default) {
    val graphemes = remember(text) { TypePace.graphemes(text) }
    var settled by rememberSaveable(text) { mutableStateOf(false) }
    if (text.isEmpty() || settled || graphemes.size > TypePace.WAVE_MAX || reducedMotion()) {
        Text(text, style = style, modifier = modifier)
        return
    }
    val n = graphemes.size
    val total = TypePace.WRITE_MS + TypePace.writeDelayMs(n - 1, n)
    val clock = remember(text) { Animatable(0f) }
    LaunchedEffect(text) {
        clock.animateTo(total, tween(total.toInt(), easing = LinearEasing))
        settled = true
    }
    val risePx = with(LocalDensity.current) {
        if (style.fontSize != TextUnit.Unspecified) style.fontSize.toPx() * 0.5f else 12f
    }
    Row(modifier.semantics { contentDescription = text }) {
        graphemes.forEachIndexed { i, glyph ->
            val start = TypePace.writeDelayMs(i, n)
            Text(
                glyph,
                style = style,
                modifier = Modifier.graphicsLayer {
                    val t = ((clock.value - start) / TypePace.WRITE_MS).coerceIn(0f, 1f)
                    val p = Motion.EaseDrawer.transform(t)
                    alpha = p
                    translationY = risePx * (1f - p)
                    val s = 0.9f + 0.1f * p
                    scaleX = s
                    scaleY = s
                },
            )
        }
    }
}

/**
 * Text that arrives over time (a translation, a summary that fills in as work finishes), paced the
 * way OpenGhost paces a streamed reply (stream-view.js): [TypePace.Stream] decides how many
 * graphemes are visible this frame from the backlog, and the newest of them rise 0.35 em and fade
 * in over 220 ms. Pass the whole text received so far; set [done] when no more is coming.
 *
 * Recomposes only when the visible count changes (one Text per new grapheme), and each glyph's
 * rise is read from a clock in the draw phase. Settles into a plain [Text] once caught up and done.
 */
@Composable
fun StreamText(text: String, done: Boolean, modifier: Modifier = Modifier, style: TextStyle = TextStyle.Default) {
    val graphemes = remember(text) { TypePace.graphemes(text) }
    val reduced = reducedMotion()
    val stream = remember { TypePace.Stream() }
    var shown by remember { mutableStateOf(0) }
    var landed by remember { mutableStateOf(false) }
    // Seconds since this composable started, read only inside graphicsLayer.
    var now by remember { mutableFloatStateOf(0f) }
    // When each grapheme became visible, on the same clock.
    val bornAt = remember { ArrayList<Float>() }
    val latestCount by rememberUpdatedState(graphemes.size)
    val latestDone by rememberUpdatedState(done)

    LaunchedEffect(reduced) {
        var last = withFrameNanos { it }
        val origin = last
        while (true) {
            val frame = withFrameNanos { it }
            val dt = ((frame - last) / 1e9f).coerceAtMost(0.1f)
            last = frame
            now = (frame - origin) / 1e9f
            stream.push(latestCount, latestDone)
            stream.advance(dt, reduced)
            val visible = stream.shown.toInt()
            while (bornAt.size < visible) bornAt.add(now)
            if (visible != shown) shown = visible
            if (latestDone && stream.caughtUp && now - (bornAt.lastOrNull() ?: 0f) > TypePace.WAVE_MS / 1000f) {
                landed = true
                break
            }
        }
    }

    // Plain text only after the last letter has finished rising, not the moment it appears.
    if (landed) {
        Text(text, style = style, modifier = modifier)
        return
    }
    val risePx = with(LocalDensity.current) {
        if (style.fontSize != TextUnit.Unspecified) style.fontSize.toPx() * 0.35f else 10f
    }
    val firstWave = (shown - TypePace.WAVE_MAX).coerceAtLeast(0)
    Row(modifier.semantics { contentDescription = text }) {
        for (i in 0 until shown.coerceAtMost(graphemes.size)) {
            if (i < firstWave) {
                Text(graphemes[i], style = style)
            } else {
                Text(
                    graphemes[i],
                    style = style,
                    modifier = Modifier.graphicsLayer {
                        val born = bornAt.getOrElse(i) { now }
                        val t = ((now - born) * 1000f / TypePace.WAVE_MS).coerceIn(0f, 1f)
                        val p = Motion.EaseOutCubic.transform(t)
                        alpha = p
                        translationY = risePx * (1f - p)
                    },
                )
            }
        }
    }
}
