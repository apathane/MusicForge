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
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.withFrameNanos
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.musicforge.automix.AutomixState
import dev.musicforge.ui.designsystem.reducedMotion
import kotlin.math.PI
import kotlin.math.cos

/**
 * Apple-Music-style scrubber: a thin bar that thickens under the finger, no thumb. The
 * playing position is read only while drawing, so playback never recomposes the bar.
 *
 * Automix shows through it, as BitChord's ThinSlider does: the stretch of the song the next blend
 * is planned to cover is drawn brighter before it arrives, and while a blend plays the filled part
 * breathes on the blend's beat. Both are read in the draw phase from a frame clock that only runs
 * during a blend.
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
    val window by AutomixState.smartTransitionWindow.collectAsStateWithLifecycle()
    val mixing by AutomixState.smartMixInProgress.collectAsStateWithLifecycle()
    val blend by AutomixState.smartMixBlend.collectAsStateWithLifecycle()
    // Ticks once a frame during a beat-known blend; read only while drawing.
    var clock by remember { mutableLongStateOf(0L) }
    val still = reducedMotion()
    val beat = blend?.takeIf { mixing && it.playing && it.beatMs > 0f && !still }
    LaunchedEffect(beat) {
        if (beat == null) return@LaunchedEffect
        while (true) clock = withFrameNanos { it }
    }

    Column(modifier) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(48.dp)
                // TalkBack sees a slider: swipe up/down to step, and the value it reads is real.
                .semantics {
                    contentDescription = "Seek"
                    if (durationMs > 0) {
                        progressBarRangeInfo = ProgressBarRangeInfo(
                            (position.value.toFloat() / durationMs).coerceIn(0f, 1f), 0f..1f, steps = 0,
                        )
                        setProgress("Seek") { f -> onSeek((f.coerceIn(0f, 1f) * durationMs).toLong()); true }
                    }
                }
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
            // The planned blend, brighter on the part not yet played.
            window?.let { w ->
                val from = size.width * maxOf(w.start.coerceIn(0f, 1f), fraction)
                val to = size.width * w.end.coerceIn(0f, 1f)
                if (to > from) drawRoundRect(color.copy(alpha = 0.5f), Offset(from, top), Size(to - from, h), radius)
            }
            // During a blend the fill dips and swells once a beat (a raised cosine, peak on the beat).
            val pulse = beat?.let { b ->
                val sinceMs = (clock - b.beatAnchorNanos) / 1e6f
                val phase = ((sinceMs % b.beatMs) + b.beatMs) % b.beatMs / b.beatMs
                val level = (1f + cos(2f * PI.toFloat() * phase)) / 2f
                0.55f + 0.45f * level
            } ?: 1f
            drawRoundRect(color.copy(alpha = pulse), Offset(0f, top), Size(size.width * fraction, h), radius)
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