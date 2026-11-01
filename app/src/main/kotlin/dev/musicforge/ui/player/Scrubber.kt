package dev.musicforge.ui.player

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Stable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.musicforge.automix.AutomixState
import dev.musicforge.automix.MixBlend
import dev.musicforge.ui.designsystem.reducedMotion
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlinx.coroutines.launch

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
    val blend by AutomixState.smartMixBlend.collectAsStateWithLifecycle()
    val still = reducedMotion()
    // One beat clock drives the fill and the "Mixing" label, so they can never drift out of step.
    // Everything it sets is read in the draw phase — a beat costs a redraw, not a recomposition.
    val pulse = rememberMixPulse({ blend }, enabled = !still)
    // Shown once the blend has claimed any of the bar; cross-fades the two centred labels.
    val mixingShown by remember { derivedStateOf { pulse.cover > 0f } }

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
            val fractionBase = drag ?: if (durationMs > 0) (position.value.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
            val radius = CornerRadius(h / 2)
            drawRoundRect(color.copy(alpha = 0.22f), Offset(0f, top), Size(size.width, h), radius)
            // The planned blend, brighter on the part not yet played.
            window?.let { w ->
                val from = size.width * maxOf(w.start.coerceIn(0f, 1f), fractionBase)
                val to = size.width * w.end.coerceIn(0f, 1f)
                if (to > from) drawRoundRect(color.copy(alpha = 0.5f), Offset(from, top), Size(to - from, h), radius)
            }
            // While a blend runs the fill is the whole bar through both songs, and its opacity
            // swells on each beat: full on the beat, resting low between beats.
            val cover = if (drag != null) 0f else pulse.cover
            val fraction = fractionBase + (1f - fractionBase) * cover
            val alpha = if (drag != null) 1f else pulse.alpha(1f)
            drawRoundRect(color.copy(alpha = alpha), Offset(0f, top), Size(size.width * fraction, h), radius)
        }
        // Times on either side, the centred pill between them.
        val playedSec by remember { derivedStateOf { position.value / 1000 } }
        val shown = drag?.let { (it * durationMs).toLong() } ?: (playedSec * 1000)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(clock(shown), style = MaterialTheme.typography.labelMedium, color = color.copy(alpha = 0.6f))
            Spacer(Modifier.weight(1f))
            Box(Modifier.weight(1f)) {
                // The codec pill while nothing blends, "Mixing" while a blend holds the bar.
                // Cover cross-fades them so neither pops in.
                if (codecLabel != null) {
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(50))
                            .background(color.copy(alpha = 0.14f))
                            .padding(horizontal = 10.dp, vertical = 3.dp)
                            .graphicsLayer { alpha = 1f - pulse.cover },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(codecLabel, style = MaterialTheme.typography.labelSmall, color = color.copy(alpha = 0.85f))
                    }
                }
                if (mixingShown) {
                    Text(
                        text = "Mixing",
                        modifier = Modifier
                            .clickable(
                                indication = null,
                                interactionSource = remember { MutableInteractionSource() },
                            ) {
                                // TODO(W5b): open the mix view
                            }
                            .graphicsLayer {
                                alpha = pulse.cover * pulse.alpha(1f, restShare = MIXING_LABEL_REST)
                            },
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Medium,
                            fontSize = (MaterialTheme.typography.labelMedium.fontSize.value + MIXING_LABEL_SIZE_BUMP).sp,
                        ),
                        color = color.copy(alpha = 0.85f),
                        maxLines = 1,
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            Text("-" + clock((durationMs - shown).coerceAtLeast(0)), style = MaterialTheme.typography.labelMedium, color = color.copy(alpha = 0.6f))
        }
    }
}

/** The Automix blend's beat clock, shared by the scrubber fill and the "Mixing" label. */
@Stable
class MixPulse internal constructor(private val blend: () -> MixBlend?) {
    /** Whether a blend is running right now. Derived, so readers recompose when a blend starts or ends, not when its grid is republished. */
    val active: Boolean by derivedStateOf { blend() != null }
    /** Whether the blend is playing; a paused one lets the pulse settle and the clock stop. */
    internal val playing: Boolean by derivedStateOf { blend()?.playing == true }

    /** 0→1 over [COVER_MS] when a blend starts, back to 0 when it ends. */
    internal val coverAnim = Animatable(0f)
    /** 0→1 over [DEPTH_MS]; scales how deep the beat-to-beat dip goes. */
    internal val depthAnim = Animatable(0f)
    /** beatShape(phase): the pulse's brightness through the current beat. */
    internal var level by mutableFloatStateOf(0f)

    /** Kept across restarts of the clock, so a blend ending never jumps the phase. */
    internal var phase = 0.0
    internal var locked = false

    /** 0..1: how far the blend has claimed the bar and the label, eased in and out. */
    val cover: Float get() = coverAnim.value

    /**
     * [fullAlpha] as the pulse modulates it: [fullAlpha] on the beat, [restShare] of it at the
     * half-beat, and [fullAlpha] throughout while the depth fade has it covered.
     */
    fun alpha(fullAlpha: Float, restShare: Float = PULSE_REST): Float =
        pulseAlpha(fullAlpha, level, depthAnim.value, restShare)

    internal fun blendNow(): MixBlend? = blend()
}

/**
 * Runs the beat clock for [blend]. [enabled] off (reduced motion) keeps the cover — the bar still
 * fills and the label still shows — but holds them steady instead of pulsing.
 */
@Composable
fun rememberMixPulse(blend: () -> MixBlend?, enabled: Boolean): MixPulse {
    val current by rememberUpdatedState(blend)
    val pulse = remember { MixPulse { current() } }
    val active = pulse.active
    val playing = pulse.playing
    LaunchedEffect(active, playing, enabled) {
        launch {
            pulse.coverAnim.animateTo(
                if (active) 1f else 0f,
                tween(durationMillis = COVER_MS.toInt(), easing = FastOutSlowInEasing),
            )
        }
        launch {
            // Paused mid-blend, the pulse settles to a steady bar rather than beating on
            // over silence — and the frame clock below gets to stop.
            pulse.depthAnim.animateTo(
                if (active && playing && enabled) 1f else 0f,
                tween(durationMillis = DEPTH_MS.toInt(), easing = FastOutSlowInEasing),
            )
        }
        if (!enabled) return@LaunchedEffect
        // The pulse's own beat clock. The blend publishes a beat anchor every fade tick, and each
        // one is re-derived from player positions that wobble by a few tens of ms — drawn straight,
        // that wobble is the pulse lurching. So this runs a free phase at the blend's tempo and
        // only leans toward the published anchor a little each frame: it follows a real tempo
        // change within a fraction of a second, and never visibly jumps. Runs until the pulse has
        // faded out after the blend.
        var last = System.nanoTime()
        while ((active && playing) || pulse.depthAnim.value > 0f) {
            withFrameNanos {
                val now = System.nanoTime()
                val dt = (now - last).coerceIn(0L, MAX_FRAME_NANOS)
                last = now
                val current = pulse.blendNow()
                val beatNanos = current?.beatMs?.takeIf { it > 0f }?.let { it * 1_000_000.0 }
                var phase = pulse.phase
                if (beatNanos != null && current != null) {
                    phase += dt / beatNanos
                    val target = Math.floorMod(now - current.beatAnchorNanos, beatNanos.toLong()) / beatNanos
                    // floorMod keeps the phase on the beat circle. Without it the clock would lap
                    // itself and the pulse would chase its own tail — much like this comment chasing
                    // a joke and settling for a pun instead.
                    // Shortest way round the circle to where the beat should be. The very first lock
                    // snaps — nothing is showing yet — and every one after only leans.
                    val error = ((target - phase) % 1.0 + 1.5) % 1.0 - 0.5
                    phase += if (pulse.locked) error * PHASE_PULL else error
                    pulse.locked = true
                } else {
                    // No tempo known, or the blend just ended: a slow breath from wherever the
                    // phase already is, which the depth fade then takes away.
                    phase += dt / BREATH_NANOS
                    if (current != null) pulse.locked = false
                }
                phase -= floor(phase)
                pulse.phase = phase
                pulse.level = beatShape(phase.toFloat())
            }
        }
        pulse.locked = false
    }
    return pulse
}

private const val COVER_MS = 700L
private const val DEPTH_MS = 900L
private const val PULSE_REST = 0.1f
private const val MIXING_LABEL_REST = 0.5f
private const val PHASE_PULL = 0.06
private const val BREATH_NANOS = 1_800_000_000.0
private const val MAX_FRAME_NANOS = 100_000_000L
private const val MIXING_LABEL_SIZE_BUMP = 0.5f

/**
 * The pulse's brightness through one beat, 0..1: a raised cosine squared, peaking on the beat
 * and easing out at the half-beat. Smooth in value and slope everywhere, so there is no edge for
 * the eye to catch — a breath on each beat rather than a flash. It beats. Not as often as a
 * drummer, and never at band practice, but it does its best.
 */
fun beatShape(phase: Float): Float {
    val c = 0.5f + 0.5f * cos(2f * PI.toFloat() * phase)
    return c * c
}

/** One beat of the fill's alpha: fullAlpha on the beat, restShare of it at the half-beat, modulated by depth. */
fun pulseAlpha(fullAlpha: Float, level: Float, depth: Float, restShare: Float): Float {
    val rest = fullAlpha * restShare
    val pulsed = rest + (fullAlpha - rest) * level
    return fullAlpha + (pulsed - fullAlpha) * depth
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