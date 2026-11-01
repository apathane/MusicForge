package dev.musicforge.ui.forge

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import dev.musicforge.automix.AutomixState
import dev.musicforge.automix.MixTransitionView
import dev.musicforge.automix.SmartAnalysis
import dev.musicforge.automix.TrackAnalysisState
import dev.musicforge.playback.NowPlaying
import dev.musicforge.playback.PlayerConnection
import dev.musicforge.ui.components.Artwork
import dev.musicforge.ui.designsystem.reducedMotion
import dev.musicforge.ui.player.MixViewRequest
import dev.musicforge.ui.player.clock
import androidx.compose.ui.text.style.TextOverflow
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt

private val SeamHeight = 40.dp

/**
 * The queue as a set: from the current song on, each row is a song and each seam is the
 * transition Automix has planned there. Everything shown comes from the engine's real state —
 * the seam after the current song shows the planned blend, later seams admit the engine only
 * plans one transition ahead ("Planned when it's next"). Tap a row to jump to it; tap the next
 * seam to open the two-deck mix view.
 *
 * The seam's beat pulse is read from a frame clock inside drawBehind only (Scrubber's pattern),
 * so playback never recomposes the list.
 *
 * The glow is a raised cosine on the blend's beat — trigonometry pretending to be excited. The
 * deck is not excited. It is audio.
 */
@Composable
fun TheSet(now: NowPlaying, connection: PlayerConnection, modifier: Modifier = Modifier) {
    // now names the current song; the rows below are the queue from the current index on.
    val white = Color.White
    val queue by connection.queue.collectAsStateWithLifecycle()
    val current by connection.currentIndex.collectAsStateWithLifecycle()
    val first = current.coerceAtLeast(0)
    val automixOn by AutomixState.smartFadeEnabled.collectAsStateWithLifecycle()
    val crossfadeSeconds by AutomixState.crossfadeSeconds.collectAsStateWithLifecycle()
    val view by AutomixState.smartMixView.collectAsStateWithLifecycle()
    val window by AutomixState.smartTransitionWindow.collectAsStateWithLifecycle()
    val analysis by AutomixState.smartAnalysis.collectAsStateWithLifecycle()
    // Delegated vals can't be smart-cast (the getter may change under you), so snap it to a
    // local first — the one blend the compiler won't let you fade into on its own.
    val mixView = view
    // A queue with no planned transition is honest silence — not a failed mix.
    val nextKind: SeamKind = when {
        !automixOn -> if (crossfadeSeconds > 0) SeamKind.Crossfade else SeamKind.StraightCut
        mixView != null -> SeamKind.Blend(mixView)
        window != null -> {
            // smartTransitionWindow is fractions of the current track, published well before
            // smartMixView — so the seam can name the blend long before the decks exist.
            val durationMs = now.durationMs
            val startMs = (window.start * durationMs).roundToLong()
            val overSeconds = ((window.end - window.start) * durationMs / 1000f).roundToInt()
            // Early enough to finish your coffee. Or to spill it; the blend doesn't judge.
            SeamKind.BlendPlanned(startMs, overSeconds)
        }
        else -> SeamKind.Analysis(analysis.current, analysis.next)
    }

    // Stable per-occurrence keys, assigned exactly as QueuePane does (keyed by mediaId, repeats
    // reuse keys in order, never rebuild a surviving row's key). A queue without stable keys is
    // like a DJ who restarts the mix every time the needle moves — nobody wants that.
    val rows = remember { mutableStateListOf<Pair<Long, MediaItem>>() }
    val nextKey = remember { mutableLongStateOf(0L) }
    LaunchedEffect(queue) {
        val spare = rows.groupBy({ it.second.mediaId }, { it.first }).mapValues { ArrayDeque(it.value) }
        val next = queue.drop(first).map { item ->
            val key = spare[item.mediaId]?.removeFirstOrNull() ?: run { nextKey.longValue++; nextKey.longValue }
            key to item
        }
        rows.clear()
        rows.addAll(next)
    }

    Column(modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 12.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("The set", style = MaterialTheme.typography.titleMedium, color = white)
            Spacer(Modifier.weight(1f))
            Text(
                if (automixOn) "Automix on" else "Automix off",
                style = MaterialTheme.typography.labelMedium,
                color = white.copy(alpha = 0.6f),
            )
        }
        Spacer(Modifier.height(8.dp))
        LazyColumn(modifier = Modifier.weight(1f)) {
            itemsIndexed(rows, key = { _, row -> row.first }) { i, (_, item) ->
                val index = first + i
                Column(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable(onClickLabel = "Play ${item.mediaMetadata.title?.toString().orEmpty()}") {
                                connection.skipTo(index)
                            }
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Artwork(item.mediaMetadata.artworkUri?.toString(), 44.dp, corner = 8.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                item.mediaMetadata.title?.toString().orEmpty(),
                                color = white,
                                style = MaterialTheme.typography.titleSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                item.mediaMetadata.artist?.toString().orEmpty(),
                                color = white.copy(alpha = 0.6f),
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    if (i != rows.lastIndex) {
                        Seam(
                            kind = if (i == 0) nextKind else SeamKind.Later,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}

private sealed interface SeamKind {
    object Later : SeamKind
    object Crossfade : SeamKind
    object StraightCut : SeamKind
    data class Blend(val view: MixTransitionView) : SeamKind
    data class BlendPlanned(val startMs: Long, val overSeconds: Int) : SeamKind
    data class Analysis(val current: TrackAnalysisState, val next: TrackAnalysisState) : SeamKind
}

/** One seam: 40 dp tall, a hairline and the transition's honest state. */
@Composable
private fun Seam(kind: SeamKind, modifier: Modifier = Modifier) {
    val white = Color.White
    val mixing by AutomixState.smartMixInProgress.collectAsStateWithLifecycle()
    val blend by AutomixState.smartMixBlend.collectAsStateWithLifecycle()
    val still = reducedMotion()
    val beatMs = blend?.beatMs ?: 0f
    // Only the seam right after the current song glows, and only while a blend is playing.
    val beating = kind !is SeamKind.Later && mixing && beatMs > 0f && !still
    val lineWidth = if (beating || mixing) 2.dp else 1.dp
    var clock by remember { mutableLongStateOf(0L) }
    val beat = blend?.takeIf { mixing && it.playing && beatMs > 0f && !still }
    // Ticks once a frame during a playing blend; the clock itself is read in drawBehind only.
    LaunchedEffect(beat) {
        if (beat == null) return@LaunchedEffect
        while (true) clock = withFrameNanos { it }
    }

    Box(modifier.height(SeamHeight)) {
        Row(
            Modifier
                .fillMaxSize()
                .then(
                    if (kind is SeamKind.Blend) {
                        Modifier.clickable(onClickLabel = "Open mix view") { MixViewRequest.open = true }
                    } else Modifier
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .width(lineWidth)
                    .height(SeamHeight)
                    .drawBehind {
                        // Read the clock in the draw phase, never in composition.
                        val now = clock
                        val alpha = when {
                            still -> if (mixing) 0.9f else 0.25f
                            beat != null -> {
                                val sinceMs = (now - beat.beatAnchorNanos) / 1e6f
                                val phase = ((sinceMs % beat.beatMs) + beat.beatMs) % beat.beatMs / beat.beatMs
                                val level = (1f + cos(2f * PI.toFloat() * phase)) / 2f
                                0.55f + 0.45f * level
                            }
                            else -> if (mixing) 0.6f else 0.25f
                        }
                        drawRect(white.copy(alpha = alpha), size = size)
                    },
            )
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                when (kind) {
                    SeamKind.Later -> Text(
                        "Planned when it's next",
                        color = white.copy(alpha = 0.35f),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                    )
                    SeamKind.Crossfade -> Text("Crossfade", color = white, style = MaterialTheme.typography.bodySmall)
                    SeamKind.StraightCut -> Text("Straight cut", color = white, style = MaterialTheme.typography.bodySmall)
                    is SeamKind.Blend -> {
                        val deckOut = kind.view.outgoing
                        val deckIn = kind.view.incoming
                        Text(
                            if (kind.view.beats > 0) "Blend · ${kind.view.beats} beats" else "Blend",
                            color = white,
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        val bpm = if (deckOut.bpm > 0 && deckIn.bpm > 0) "${deckOut.bpm.roundToInt()} → ${deckIn.bpm.roundToInt()} BPM" else ""
                        val keys =
                            if (deckOut.key.isNotBlank() && deckIn.key.isNotBlank()) " · ${deckOut.key} → ${deckIn.key}" else ""
                        val detail = bpm + keys
                        if (detail.isNotEmpty()) {
                            Text(detail, color = white.copy(alpha = 0.6f), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    is SeamKind.BlendPlanned -> {
                        Text(
                            "Blend at ${clock(kind.startMs)}",
                            color = white,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            "Over ${kind.overSeconds} s",
                            color = white.copy(alpha = 0.6f),
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    is SeamKind.Analysis -> Text(analysisState(kind.current, kind.next), color = white, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

private fun analysisState(current: TrackAnalysisState, next: TrackAnalysisState): String {
    val states = listOf(current, next)
    return when {
        states.any { it == TrackAnalysisState.ANALYSING || it == TrackAnalysisState.REFINING } -> "Analysing…"
        states.any { it == TrackAnalysisState.FAILED } -> "Couldn't analyse"
        states.any { it == TrackAnalysisState.WAITING } -> "Waiting"
        else -> "Ready"
    }
}
