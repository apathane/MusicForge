package dev.musicforge.ui.player

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

/**
 * Apple-Music-style scrubber: a thin bar that thickens under the finger, no thumb. The
 * playing position is read only while drawing, so playback never recomposes the bar.
 *
 * [codecLabel] is the centred pill (e.g. "Opus 133 kbps") read from the song's `format`
 * row; null hides it.
 */
@Composable
fun Scrubber(
    position: State<Long>,
    durationMs: Long,
    color: Color,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
    codecLabel: String? = null,
) {
    var drag by remember { mutableStateOf<Float?>(null) }
    val thickness by animateDpAsState(if (drag != null) 9.dp else 5.dp, spring(stiffness = 700f), label = "scrub")

    Column(modifier) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(48.dp)
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
        // Times on either side, the codec pill centred between them.
        val playedSec by remember { androidx.compose.runtime.derivedStateOf { position.value / 1000 } }
        val shown = drag?.let { (it * durationMs).toLong() } ?: (playedSec * 1000)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(clock(shown), style = MaterialTheme.typography.labelMedium, color = color.copy(alpha = 0.6f))
            Spacer(Modifier.weight(1f))
            if (codecLabel != null) {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .background(color.copy(alpha = 0.14f))
                        .padding(horizontal = 10.dp, vertical = 3.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(codecLabel, style = MaterialTheme.typography.labelSmall, color = color.copy(alpha = 0.85f))
                }
            }
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

/** "Opus 133 kbps" from the stored `format` row; null when the codec or bitrate is unknown. */
fun codecPill(mimeType: String?, codecs: String?, bitrate: Int?): String? {
    if (bitrate == null || bitrate <= 0) return null
    val raw = codecs?.trim()?.takeIf { it.isNotEmpty() }
        ?: mimeType?.substringAfter("codecs=", "")?.substringBefore(';')?.trim()?.takeIf { it.isNotEmpty() }
    val name = when (raw?.lowercase()) {
        "opus" -> "Opus"
        "mp4a", "mp4", "aac" -> "AAC"
        "mp3", "mpeg" -> "MP3"
        "vorbis" -> "Vorbis"
        null -> if (mimeType?.lowercase() == "audio/mp4") "AAC" else null
        else -> if (mimeType?.lowercase() == "audio/mp4") "AAC" else raw.replaceFirstChar { it.uppercaseChar() }.takeIf { it.isNotBlank() }
    }
    val resolved = name ?: if (mimeType?.lowercase() == "audio/mp4") "AAC" else null
    if (resolved.isNullOrBlank()) return null
    return "$resolved ${bitrate / 1000} kbps"
}