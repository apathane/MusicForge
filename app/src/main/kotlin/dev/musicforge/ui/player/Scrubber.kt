package dev.musicforge.ui.player

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

/**
 * Apple-Music-style scrubber: a thin bar that thickens under the finger, no thumb. The
 * playing position is read only while drawing, so playback never recomposes the bar.
 */
@Composable
fun Scrubber(
    position: State<Long>,
    durationMs: Long,
    color: Color,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    var drag by remember { mutableStateOf<Float?>(null) }
    val thickness by animateDpAsState(if (drag != null) 9.dp else 5.dp, spring(stiffness = 700f), label = "scrub")

    Column(modifier) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(28.dp)
                .pointerInput(durationMs) {
                    detectTapGestures { o -> if (durationMs > 0) onSeek((o.x / size.width * durationMs).toLong()) }
                }
                .pointerInput(durationMs) {
                    detectHorizontalDragGestures(
                        onDragStart = { o -> drag = (o.x / size.width).coerceIn(0f, 1f) },
                        onHorizontalDrag = { change, _ -> drag = (change.position.x / size.width).coerceIn(0f, 1f) },
                        onDragEnd = {
                            drag?.let { if (durationMs > 0) onSeek((it * durationMs).toLong()) }
                            drag = null
                        },
                        onDragCancel = { drag = null },
                    )
                },
        ) {
            val h = thickness.toPx()
            val top = (size.height - h) / 2
            val fraction = drag ?: if (durationMs > 0) (position.value.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
            val radius = CornerRadius(h / 2)
            drawRoundRect(color.copy(alpha = 0.22f), Offset(0f, top), Size(size.width, h), radius)
            drawRoundRect(color, Offset(0f, top), Size(size.width * fraction, h), radius)
        }
        // Times: the only part that recomposes with playback, a few times a second.
        val shown = drag?.let { (it * durationMs).toLong() } ?: position.value
        Row(Modifier.fillMaxWidth()) {
            Text(clock(shown), style = MaterialTheme.typography.labelMedium, color = color.copy(alpha = 0.6f))
            Spacer(Modifier.weight(1f))
            Text("-" + clock((durationMs - shown).coerceAtLeast(0)), style = MaterialTheme.typography.labelMedium, color = color.copy(alpha = 0.6f))
        }
    }
}

fun clock(ms: Long): String {
    val total = ms / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
