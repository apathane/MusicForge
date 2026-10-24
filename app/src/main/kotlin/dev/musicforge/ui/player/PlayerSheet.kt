package dev.musicforge.ui.player

import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download
import coil3.compose.AsyncImage
import dev.musicforge.graph
import dev.musicforge.playback.NowPlaying
import dev.musicforge.playback.from
import dev.musicforge.playback.isFromRadio
import dev.musicforge.ui.components.ForgeIcons
import dev.musicforge.ui.components.TrackActionsSheet
import dev.musicforge.ui.components.askToRunInBackground
import dev.musicforge.ui.theme.Ambience
import dev.musicforge.ui.theme.LocalArtworkColors
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

enum class PlayerPane { ARTWORK, LYRICS, QUEUE }

/**
 * The full player, slid over everything. Always dark (white on the artwork's blurred
 * colours), the way a record sleeve sits on a dark table.
 */
@Composable
fun PlayerSheet(now: NowPlaying, ambience: Ambience, open: Boolean, onClose: () -> Unit, onCovering: (Boolean) -> Unit = {}) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val heightPx = with(LocalDensity.current) { maxHeight.toPx() }
        // A flick that should dismiss, in px/s: physical speed, not pixel speed.
        val flingPx = with(LocalDensity.current) { 700.dp.toPx() }
        val offset = remember { Animatable(heightPx) }
        val scope = rememberCoroutineScope()

        // Open/close follows `open`; drags move the same Animatable so they can interrupt it.
        androidx.compose.runtime.LaunchedEffect(open) {
            offset.animateTo(if (open) 0f else heightPx, spring(dampingRatio = 0.86f, stiffness = 420f))
        }
        BackHandler(enabled = open) { onClose() }
        // Tell the host when the sheet fully covers the screen, so it can stop drawing what's behind.
        val covering by remember { androidx.compose.runtime.derivedStateOf { offset.value < 0.5f } }
        androidx.compose.runtime.LaunchedEffect(covering) { onCovering(covering) }
        if (!open && offset.value >= heightPx - 1f) return@BoxWithConstraints

        val dismissDrag = Modifier.pointerInput(Unit) {
            val tracker = VelocityTracker()
            detectVerticalDragGestures(
                onVerticalDrag = { change, dy ->
                    tracker.addPosition(change.uptimeMillis, change.position)
                    scope.launch { offset.snapTo((offset.value + dy).coerceAtLeast(0f)) }
                },
                onDragEnd = {
                    val v = tracker.calculateVelocity().y
                    tracker.resetTracking()
                    if (offset.value > heightPx * 0.22f || v > flingPx) {
                        onClose()
                    } else {
                        scope.launch { offset.animateTo(0f, spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMedium)) }
                    }
                },
            )
        }
        // Moving and clipping happen on one layer: the sheet's content is recorded once and
        // the slide is pure compositing, and the rounded clip is a hardware outline clip.
        val sheetShape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationY = offset.value
                    shape = sheetShape
                    // Fully open, nothing is painted behind the sheet (no window background), so
                    // rounded corners would show stale pixels: square it off at rest.
                    clip = offset.value > 0.5f
                }
                .playerBackground(ambience, remember(ambience.backdrop) { ambience.backdrop?.let(::withScrim) }),
        ) {
            PlayerContent(now, onClose, dismissDrag)
        }
    }
}

private val playerScrim = Brush.verticalGradient(0f to Color.Black.copy(alpha = 0.18f), 0.55f to Color.Black.copy(alpha = 0.38f), 1f to Color.Black.copy(alpha = 0.72f))

/**
 * The scrim (so white text always reads) baked into the small blurred backdrop once per
 * track: drawing it full-screen every frame was a third full-screen fill on the X7b.
 */
private fun withScrim(backdrop: androidx.compose.ui.graphics.ImageBitmap): androidx.compose.ui.graphics.ImageBitmap {
    val out = androidx.compose.ui.graphics.ImageBitmap(backdrop.width, backdrop.height)
    val canvas = androidx.compose.ui.graphics.Canvas(out)
    val size = androidx.compose.ui.geometry.Size(backdrop.width.toFloat(), backdrop.height.toFloat())
    androidx.compose.ui.graphics.drawscope.CanvasDrawScope().draw(androidx.compose.ui.unit.Density(1f), androidx.compose.ui.unit.LayoutDirection.Ltr, canvas, size) {
        drawImage(backdrop)
        drawRect(playerScrim)
    }
    return out
}

/** Blurred artwork stretched to the screen; one full-screen draw when there is artwork. */
private fun Modifier.playerBackground(ambience: Ambience, scrimmed: androidx.compose.ui.graphics.ImageBitmap?): Modifier = drawBehind {
    if (scrimmed != null) {
        drawImage(
            scrimmed,
            dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
            filterQuality = FilterQuality.Low,
        )
    } else {
        drawRect(ambience.colors.ambient.takeIf { it != Color.Unspecified } ?: Color(0xFF111114))
        drawRect(playerScrim)
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun PlayerContent(now: NowPlaying, onClose: () -> Unit, dismissDrag: Modifier) {
    val context = LocalContext.current
    val graph = context.graph
    val connection = graph.player
    val position: State<Long> = remember(connection) { connection.position(200) }.collectAsStateWithLifecycle(0L)
    var pane by rememberSaveable { mutableStateOf(PlayerPane.ARTWORK) }
    var menu by remember { mutableStateOf(false) }
    var sleepMenu by remember { mutableStateOf(false) }
    val liked by remember(now.mediaId) { graph.database.dao().isLiked(now.mediaId) }.collectAsStateWithLifecycle(null)
    val download by remember(now.mediaId) { graph.downloads.state(now.mediaId) }.collectAsStateWithLifecycle(null)
    val sleepAt by connection.sleepAt.collectAsStateWithLifecycle()
    val accent = LocalArtworkColors.current.accent.takeIf { it != Color.Unspecified } ?: Color.White
    val white = Color.White

    // On the artwork the whole sheet drags down, like Apple Music; over lyrics or the queue
    // only the grabber does, so scrolling a list never pulls the player away.
    Column(
        Modifier.fillMaxSize().then(if (pane == PlayerPane.ARTWORK) dismissDrag else Modifier)
            .statusBarsPadding().navigationBarsPadding().padding(horizontal = 26.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Grabber + collapse
        Box(Modifier.fillMaxWidth().height(44.dp).then(if (pane != PlayerPane.ARTWORK) dismissDrag else Modifier), contentAlignment = Alignment.Center) {
            Box(Modifier.size(width = 38.dp, height = 5.dp).clip(RoundedCornerShape(50)).background(white.copy(alpha = 0.35f)))
            Box(Modifier.align(Alignment.CenterStart)) { PlayerIcon(ForgeIcons.Collapse, "Close player", white, 30.dp, onClose) }
        }

        // The middle pane changes; the controls below never move.
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            AnimatedContent(pane, transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(140)) }, label = "pane") { p ->
                when (p) {
                    PlayerPane.ARTWORK -> BigArtwork(now)
                    PlayerPane.LYRICS -> LyricsPane(now, position, accent) { connection.seekTo(it) }
                    PlayerPane.QUEUE -> QueuePane()
                }
            }
        }

        // Title row
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    now.metadata.title?.toString().orEmpty(),
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.W700),
                    color = white,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    now.error ?: now.metadata.artist?.toString().orEmpty(),
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (now.error != null) Color(0xFFFF6B6B) else white.copy(alpha = 0.65f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                // Why this song is playing, when it wasn't simply picked: a Home shelf, or radio.
                val from = now.metadata.from
                if (now.error == null && from != null) {
                    Text(
                        if (now.metadata.isFromRadio) "Radio pick" else "From $from",
                        style = MaterialTheme.typography.labelMedium,
                        color = white.copy(alpha = 0.5f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            val isLiked = liked == true
            PlayerIcon(if (isLiked) ForgeIcons.Heart else ForgeIcons.HeartOutline, if (isLiked) "Unlike" else "Like", if (isLiked) accent else white, 26.dp) {
                connection.queue.value.getOrNull(connection.currentIndex.value)?.let { graph.library.setLiked(it, !isLiked) }
            }
            PlayerIcon(ForgeIcons.More, "More options", white, 26.dp) { menu = true }
        }

        Scrubber(position, now.durationMs, white, onSeek = connection::seekTo, modifier = Modifier.padding(top = 10.dp))

        // Transport
        Row(
            Modifier.fillMaxWidth().padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PlayerIcon(ForgeIcons.Shuffle, if (now.shuffle) "Shuffle on" else "Shuffle off", if (now.shuffle) accent else white.copy(alpha = 0.55f), 24.dp) { connection.toggleShuffle() }
            PlayerIcon(ForgeIcons.Previous, "Previous", white, 40.dp) { connection.previous() }
            Box(Modifier.size(78.dp), contentAlignment = Alignment.Center) {
                if (now.isBuffering && !now.isPlaying) {
                    CircularProgressIndicator(Modifier.size(40.dp), color = white, strokeWidth = 3.dp)
                } else {
                    PlayerIcon(if (now.isPlaying) ForgeIcons.Pause else ForgeIcons.Play, if (now.isPlaying) "Pause" else "Play", white, 56.dp) {
                        connection.togglePlay()
                    }
                }
            }
            PlayerIcon(ForgeIcons.Next, "Next", if (now.hasNext) white else white.copy(alpha = 0.3f), 40.dp) { connection.next() }
            val repeatIcon = if (now.repeatMode == Player.REPEAT_MODE_ONE) ForgeIcons.RepeatOne else ForgeIcons.Repeat
            val repeatLabel = when (now.repeatMode) { Player.REPEAT_MODE_ONE -> "Repeat one"; Player.REPEAT_MODE_ALL -> "Repeat all"; else -> "Repeat off" }
            PlayerIcon(repeatIcon, repeatLabel, if (now.repeatMode != Player.REPEAT_MODE_OFF) accent else white.copy(alpha = 0.55f), 24.dp) {
                connection.cycleRepeat()
            }
        }

        // Pane switches + sleep + download
        Row(
            Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PaneButton(ForgeIcons.Lyrics, "Lyrics", pane == PlayerPane.LYRICS, white) {
                pane = if (pane == PlayerPane.LYRICS) PlayerPane.ARTWORK else PlayerPane.LYRICS
            }
            PaneButton(ForgeIcons.Sleep, "Sleep timer", sleepAt != null, white) { sleepMenu = true }
            val done = download?.state == Download.STATE_COMPLETED
            PaneButton(ForgeIcons.Download, if (done) "Downloaded" else "Download", done, white) {
                if (!done) {
                    graph.player.queue.value.getOrNull(graph.player.currentIndex.value)?.let {
                        askToRunInBackground(context)
                        graph.downloads.download(listOf(it))
                    }
                }
            }
            PaneButton(ForgeIcons.Queue, "Queue", pane == PlayerPane.QUEUE, white) {
                pane = if (pane == PlayerPane.QUEUE) PlayerPane.ARTWORK else PlayerPane.QUEUE
            }
        }
    }

    if (menu) {
        graph.player.queue.value.getOrNull(graph.player.currentIndex.value)?.let { TrackActionsSheet(it) { menu = false } }
            ?: run { menu = false }
    }
    if (sleepMenu) {
        SleepSheet(sleepAt) { minutes ->
            if (minutes != -1) connection.sleepIn(minutes) // -1 = dismissed
            sleepMenu = false
        }
    }
}

/** Big cover that eases back when paused: the record is resting. */
private val artShape = RoundedCornerShape(14.dp)

@Composable
private fun BigArtwork(now: NowPlaying) {
    val scale by animateFloatAsState(if (now.isPlaying || now.isBuffering) 1f else 0.86f, spring(dampingRatio = 0.62f, stiffness = 260f), label = "artScale")
    // Video thumbnails are letterboxed 4:3; zoom past the bars (see Artwork).
    val letterboxed = dev.musicforge.playback.isLetterboxedThumbnail(now.metadata.artworkUri?.toString().orEmpty())
    val tile = remember { androidx.compose.ui.graphics.painter.ColorPainter(Color.White.copy(alpha = 0.08f)) }
    AsyncImage(
        model = now.metadata.artworkUri,
        contentDescription = null,
        placeholder = tile,
        error = tile,
        contentScale = if (letterboxed) dev.musicforge.ui.components.CropPastLetterbox else ContentScale.Crop,
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            // Scale, shadow and rounded clip in a single layer instead of three.
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                shadowElevation = 20.dp.toPx()
                shape = artShape
                clip = true
            },
    )
}

@Composable
private fun PlayerIcon(icon: ImageVector, label: String, tint: Color, size: androidx.compose.ui.unit.Dp, onClick: () -> Unit) {
    Box(
        Modifier.size(maxOf(size + 20.dp, 48.dp)).clip(RoundedCornerShape(50)).clickable(onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, label, tint = tint, modifier = Modifier.size(size))
    }
}

@Composable
private fun PaneButton(icon: ImageVector, label: String, active: Boolean, white: Color, onClick: () -> Unit) {
    Box(
        Modifier
            .size(48.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (active) white.copy(alpha = 0.18f) else Color.Transparent)
            .clickable(onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, label, tint = if (active) white else white.copy(alpha = 0.7f), modifier = Modifier.size(22.dp))
    }
}
