package dev.musicforge.ui.player

import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download
import coil3.compose.AsyncImage
import dev.musicforge.data.prefs.Keys
import dev.musicforge.graph
import dev.musicforge.playback.NowPlaying
import dev.musicforge.playback.PlayerConnection
import dev.musicforge.playback.credits
import dev.musicforge.playback.from
import dev.musicforge.playback.isFromRadio
import dev.musicforge.ui.LocalNavController
import dev.musicforge.ui.components.ForgeIcons
import dev.musicforge.ui.components.TrackActionsSheet
import dev.musicforge.ui.components.askToRunInBackground
import dev.musicforge.ui.components.glass
import dev.musicforge.ui.designsystem.BufferingIndicator
import dev.musicforge.ui.designsystem.Motion
import dev.musicforge.ui.designsystem.pressScale
import dev.musicforge.ui.designsystem.reducedMotion
import dev.musicforge.ui.theme.Ambience
import dev.musicforge.ui.theme.LocalArtworkColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.roundToInt

enum class PlayerPane { ARTWORK, LYRICS, QUEUE }

/**
 * The full player, slid over everything. Motion follows OpenGhost: open 380 ms on EaseDrawer,
 * close 200 ms on EaseInQuart, close always faster. The slide is pure compositing on one layer.
 */
@Composable
fun PlayerSheet(now: NowPlaying, ambience: Ambience, open: Boolean, onClose: () -> Unit, onCovering: (Boolean) -> Unit = {}) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val heightPx = with(LocalDensity.current) { maxHeight.toPx() }
        val flingPx = with(LocalDensity.current) { 700.dp.toPx() }
        val offset = remember { Animatable(heightPx) }
        val scope = rememberCoroutineScope()
        val reduced = reducedMotion()

        // Open/close: the durations are the spec's, the spring only on drag release.
        LaunchedEffect(open, reduced) {
            offset.animateTo(
                if (open) 0f else heightPx,
                if (reduced) tween(0) else tween(if (open) Motion.Panel else Motion.Fade, easing = if (open) Motion.EaseDrawer else Motion.EaseInQuart),
            )
        }
        BackHandler(enabled = open) { onClose() }
        val covering by remember { androidx.compose.runtime.derivedStateOf { offset.value < 0.5f } }
        LaunchedEffect(covering) { onCovering(covering) }
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

        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationY = offset.value
                    shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)
                    clip = offset.value > 0.5f
                },
        ) {
            // Scrim fades out as the sheet settles open, so the app behind dims only mid-flight.
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = (offset.value / heightPx).coerceIn(0f, 1f) * 0.35f }
                    .background(Color.Black),
            )
            PlayerContent(now, onClose, dismissDrag, ambience, reduced)
        }
    }
}

private val playerScrim = Brush.verticalGradient(0f to Color.Black.copy(alpha = 0.18f), 0.55f to Color.Black.copy(alpha = 0.38f), 1f to Color.Black.copy(alpha = 0.72f))

private fun withScrim(backdrop: ImageBitmap, fadeTop: Float? = null): ImageBitmap {
    val out = ImageBitmap(backdrop.width, backdrop.height)
    val canvas = Canvas(out)
    val size = androidx.compose.ui.geometry.Size(backdrop.width.toFloat(), backdrop.height.toFloat())
    CanvasDrawScope().draw(androidx.compose.ui.unit.Density(1f), androidx.compose.ui.unit.LayoutDirection.Ltr, canvas, size) {
        drawImage(backdrop)
        drawRect(playerScrim)
        if (fadeTop != null) {
            drawRect(
                brush = Brush.verticalGradient(
                    0f to Color.Transparent,
                    fadeTop to Color.Transparent,
                    (fadeTop + 0.17f).coerceAtMost(1f) to Color.Black,
                    1f to Color.Black,
                ),
                blendMode = BlendMode.DstIn,
            )
        }
    }
    return out
}

/** Ambient canvas plus a mesh of artwork-derived radial gradients; one full-screen draw per track. */
private fun Modifier.playerBackground(ambience: Ambience, scrimmed: ImageBitmap?): Modifier = drawBehind {
    val ambient = ambience.colors.ambient.takeIf { it != Color.Unspecified } ?: Color(0xFF111114)
    drawRect(ambient)
    // Mesh: two soft radial pools of the accent and a lifted ambient, GPU-cheap gradients.
    drawCircle(
        brush = Brush.radialGradient(listOf(ambience.colors.accent.copy(alpha = 0.22f), Color.Transparent)),
        center = Offset(size.width * 0.78f, size.height * 0.18f),
        radius = size.minDimension * 0.72f,
    )
    drawCircle(
        brush = Brush.radialGradient(listOf(ambience.colors.ambient.copy(alpha = 0.35f), Color.Transparent)),
        center = Offset(size.width * 0.22f, size.height * 0.78f),
        radius = size.minDimension * 0.85f,
    )
    if (scrimmed != null) {
        drawImage(
            scrimmed,
            dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
            filterQuality = FilterQuality.Low,
        )
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun PlayerContent(now: NowPlaying, onClose: () -> Unit, dismissDrag: Modifier, ambience: Ambience, reduced: Boolean) {
    val context = LocalContext.current
    val graph = context.graph
    val connection = graph.player
    val ambient = ambience.colors.ambient
    val position: State<Long> = remember(connection) { connection.position(200) }.collectAsStateWithLifecycle(0L)
    val prefs by graph.settings.data.collectAsStateWithLifecycle(initialValue = null)
    val doubleTapSeek = prefs?.get(Keys.DoubleTapSeek) ?: true
    val hapticsEnabled = prefs?.get(Keys.HapticsEnabled) ?: true
    var pane by rememberSaveable { mutableStateOf(PlayerPane.ARTWORK) }
    BackHandler(enabled = pane != PlayerPane.ARTWORK) { pane = PlayerPane.ARTWORK }
    var menu by remember { mutableStateOf(false) }
    var sleepMenu by remember { mutableStateOf(false) }
    val liked by remember(now.mediaId) { graph.database.dao().isLiked(now.mediaId) }.collectAsStateWithLifecycle(null)
    val download by remember(now.mediaId) { graph.downloads.state(now.mediaId) }.collectAsStateWithLifecycle(null)
    val sleepAt by connection.sleepAt.collectAsStateWithLifecycle()
    val accent = LocalArtworkColors.current.accent.takeIf { it != Color.Unspecified } ?: Color.White
    val white = Color.White

    val format by remember(now.mediaId) { graph.database.dao().formatFlow(now.mediaId) }.collectAsStateWithLifecycle(null)
    val codecLabel = codecPill(format?.mimeType, format?.codecs, format?.bitrate)

    val queue by connection.queue.collectAsStateWithLifecycle()
    val currentIndex by connection.currentIndex.collectAsStateWithLifecycle()
    val nextItem = queue.getOrNull(currentIndex + 1)

    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }

    val scrimmed = remember(ambience.backdrop, pane) {
        ambience.backdrop?.let { withScrim(it, fadeTop = if (pane == PlayerPane.ARTWORK) 0.45f else null) }
    }

    val topInset = WindowInsets.safeDrawing.asPaddingValues().calculateTopPadding()

    // Top-band luminance scrim: keeps status-bar icons legible over light covers.
    val topBandAlpha = topBandScrimAlpha(accent.luminance())

    BoxWithConstraints(
        Modifier.fillMaxSize()
            .playerBackground(ambience, scrimmed)
            .then(if (pane == PlayerPane.ARTWORK) dismissDrag else Modifier),
    ) {
        // Subtle canvas motion art behind everything, skipped under reduced motion.
        CanvasMotionArt(accent, reduced, Modifier.fillMaxSize())

        if (pane == PlayerPane.ARTWORK) {
            BigArtwork(
                now, ambient, Modifier
                    .fillMaxWidth()
                    .height(maxHeight * 0.45f),
                doubleTap = doubleTapSeek,
                reduced = reduced,
                onDoubleTap = {
                    val ms = position.value
                    val duration = now.durationMs
                    if (duration > 0) {
                        val delta = if (ms < duration / 2) 10_000L else -10_000L
                        connection.seekTo((ms + delta).coerceIn(0L, duration))
                    }
                },
            )
        }

        // Top-band scrim overlay.
        Box(
            Modifier
                .fillMaxWidth()
                .height(topInset + 24.dp)
                .align(Alignment.TopCenter)
                .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = topBandAlpha), Color.Transparent))),
        )

        Box(
            Modifier.fillMaxWidth()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))
                .height(44.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.size(width = 38.dp, height = 5.dp).clip(RoundedCornerShape(50)).background(white.copy(alpha = 0.35f)))
            Box(Modifier.align(Alignment.CenterStart)) { PlayerIcon(
                ForgeIcons.Collapse,
                if (pane == PlayerPane.ARTWORK) "Close player" else "Back to the cover",
                white, 30.dp,
            ) { if (pane == PlayerPane.ARTWORK) onClose() else pane = PlayerPane.ARTWORK } }
        }

        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
                .padding(horizontal = 26.dp)
                .padding(top = if (pane == PlayerPane.ARTWORK) maxHeight * 0.45f else topInset + 44.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                AnimatedContent(
                    pane,
                    transitionSpec = {
                        val enter = if (reduced) fadeIn(tween(0)) else fadeIn(tween(Motion.Row, easing = Motion.EaseDrawer)) + slideInVertically(tween(Motion.Row, easing = Motion.EaseDrawer)) { 24 }
                        val exit = if (reduced) fadeOut(tween(0)) else fadeOut(tween(Motion.Fade)) + slideOutVertically(tween(Motion.Fade, easing = Motion.EaseInQuart)) { 24 }
                        enter togetherWith exit
                    },
                    label = "pane",
                ) { p ->
                    when (p) {
                        PlayerPane.ARTWORK -> Unit
                        PlayerPane.LYRICS -> LyricsPane(now, position, accent, onSeek = { connection.seekTo(it) }) { menu = true }
                        PlayerPane.QUEUE -> QueuePane(now, connection) { menu = true }
                    }
                }
            }

            if (pane == PlayerPane.ARTWORK) {
                TitleRow(now, liked == true, accent, white, onClose, hapticsEnabled, onMenu = { menu = true }, onLike = {
                    connection.queue.value.getOrNull(connection.currentIndex.value)?.let { graph.library.setLiked(it, !(liked == true)) }
                })
            }

            Scrubber(position, now.durationMs, white, onSeek = connection::seekTo, modifier = Modifier.padding(top = 10.dp), codecLabel = codecLabel)

            if (nextItem != null && pane != PlayerPane.QUEUE) {
                UpNextRow(nextItem, currentIndex, connection, white)
            }

            TransportRow(now, connection, white, accent)

            VolumeControlRow(audioManager, white)

            val showLyrics = prefs?.get(Keys.ShowLyrics) != false
            BottomRow(pane, audioManager, white, showLyrics) { newPane -> pane = newPane }
        }
    }

    if (menu) {
        graph.player.queue.value.getOrNull(graph.player.currentIndex.value)?.let { item ->
            val extras = mutableListOf<Pair<String, () -> Unit>>()
            extras += "Sleep timer" to { sleepMenu = true; menu = false }
            val done = download?.state == Download.STATE_COMPLETED
            if (done) {
                extras += "Remove download" to { graph.downloads.remove(item.mediaId); menu = false }
            } else {
                extras += "Download" to {
                    askToRunInBackground(context)
                    graph.downloads.download(listOf(item))
                    menu = false
                }
            }
            TrackActionsSheet(item, extra = extras) { menu = false }
        } ?: run { menu = false }
    }
    if (sleepMenu) {
        SleepSheet(sleepAt) { minutes ->
            if (minutes != -1) connection.sleepIn(minutes)
            sleepMenu = false
        }
    }
}

@Composable
private fun CanvasMotionArt(color: Color, reduced: Boolean, modifier: Modifier) {
    if (reduced) return
    // Single subtle circle, slow drift — one layer, one draw per frame, no infinite recomposition.
    val transition = rememberInfiniteTransition(label = "canvasMotion")
    val phase by transition.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(Motion.Ambient, easing = LinearEasing), RepeatMode.Restart),
        label = "phase",
    )
    Canvas(modifier.graphicsLayer { alpha = 0.06f }) {
        val r = size.minDimension * 0.3f
        val x = phase * size.width
        val y = size.height * 0.4f
        drawCircle(color, radius = r, center = Offset(x, y))
    }
}

@Composable
private fun BigArtwork(
    now: NowPlaying,
    ambient: Color,
    modifier: Modifier = Modifier,
    doubleTap: Boolean = false,
    reduced: Boolean = false,
    onDoubleTap: (() -> Unit)? = null,
) {
    val scale by animateFloatAsState(
        if (reduced || now.isPlaying || now.isBuffering) 1f else 0.86f,
        if (reduced) tween(0) else spring(dampingRatio = 0.62f, stiffness = 260f),
        label = "artScale",
    )
    val letterboxed = dev.musicforge.playback.isLetterboxedThumbnail(now.metadata.artworkUri?.toString().orEmpty())
    val title = now.metadata.title?.toString().orEmpty()
    val artist = now.metadata.artist?.toString().orEmpty()
    val contentDescription = when {
        title.isNotEmpty() && artist.isNotEmpty() -> "Album art, $title by $artist"
        title.isNotEmpty() -> "Album art, $title"
        artist.isNotEmpty() -> "Album art, $artist"
        else -> null
    }
    val placeholder = remember { androidx.compose.ui.graphics.painter.ColorPainter(Color.White.copy(alpha = 0.08f)) }
    val error = remember { androidx.compose.ui.graphics.painter.ColorPainter(Color(0xFFBBBBBB).copy(alpha = 0.4f)) }
    val canvas = ambient.takeIf { it != Color.Unspecified } ?: Color(0xFF111114)
    val doubleTapModifier = if (doubleTap && onDoubleTap != null) {
        Modifier.pointerInput(Unit) { detectTapGestures(onDoubleTap = { onDoubleTap() }) }
    } else Modifier
    AsyncImage(
        model = now.metadata.artworkUri,
        contentDescription = contentDescription,
        placeholder = placeholder,
        error = error,
        contentScale = if (letterboxed) dev.musicforge.ui.components.CropPastLetterbox else ContentScale.Crop,
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .drawWithContent {
                drawContent()
                drawRect(
                    brush = Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.65f to canvas.copy(alpha = 0.35f),
                        1f to canvas,
                    ),
                )
            }
            .then(doubleTapModifier),
    )
}

@Composable
private fun PlayerIcon(icon: ImageVector, label: String, tint: Color, size: Dp, onClick: () -> Unit) {
    val press = remember { MutableInteractionSource() }
    Box(
        Modifier.size(maxOf(size + 20.dp, 48.dp)).clip(RoundedCornerShape(50)).pressScale(press).clickable(interactionSource = press, indication = null, onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, label, tint = tint, modifier = Modifier.size(size))
    }
}

@Composable
private fun DiscButton(icon: ImageVector, label: String, tint: Color, iconSize: Dp = 26.dp, onClick: () -> Unit) {
    val press = remember { MutableInteractionSource() }
    Box(
        Modifier.size(56.dp).clip(RoundedCornerShape(50)).glass(RoundedCornerShape(50)).pressScale(press)
            .clickable(interactionSource = press, indication = null, onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, label, tint = tint, modifier = Modifier.size(iconSize))
    }
}

@Composable
private fun TitleRow(now: NowPlaying, liked: Boolean, accent: Color, white: Color, onClose: () -> Unit, hapticsEnabled: Boolean, onMenu: () -> Unit, onLike: () -> Unit) {
    val title = now.metadata.title?.toString().orEmpty()
    val artist = now.metadata.artist?.toString().orEmpty()
    val clipboard = LocalClipboardManager.current
    val haptics = LocalHapticFeedback.current
    val nav = LocalNavController.current
    Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.W700),
                color = white,
                maxLines = 1,
                modifier = Modifier
                    .combinedClickable(
                        interactionSource = null,
                        indication = null,
                        onClick = {},
                        onLongClickLabel = "Copy title and artist",
                        onLongClick = {
                            if (hapticsEnabled) haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                            clipboard.setText(AnnotatedString(listOf(title, artist).filter { it.isNotEmpty() }.joinToString(" – ")))
                        },
                    )
                    .graphicsLayer { compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen }
                    .drawWithContent {
                        drawContent()
                        drawRect(
                            Brush.horizontalGradient(0.9f to Color.Black, 1f to Color.Transparent),
                            blendMode = androidx.compose.ui.graphics.BlendMode.DstIn,
                        )
                    }
                    .basicMarquee(iterations = 1, initialDelayMillis = 2_000),
            )
            val artistId = now.metadata.credits.firstOrNull { it.id != null }?.id
            Text(
                now.error ?: artist,
                style = MaterialTheme.typography.bodyLarge,
                color = if (now.error != null) Color(0xFFFF6B6B) else white.copy(alpha = 0.65f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = if (now.error == null && artistId != null) {
                    Modifier.clickable(onClickLabel = "Open $artist") { onClose(); nav.navigate(dev.musicforge.ui.Routes.artist(artistId)) }
                } else Modifier,
            )
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
        AnimatedContent(
            targetState = liked to accent,
            transitionSpec = { fadeIn(tween(140)) togetherWith fadeOut(tween(120)) },
            label = "like",
        ) { (l, a) ->
            DiscButton(if (l) ForgeIcons.Heart else ForgeIcons.HeartOutline, if (l) "Unlike" else "Like", if (l) a else white) { onLike() }
        }
        Spacer(Modifier.width(12.dp))
        DiscButton(ForgeIcons.More, "More options", white) { onMenu() }
    }
}

@Composable
private fun UpNextRow(item: androidx.media3.common.MediaItem, index: Int, connection: PlayerConnection, color: Color) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        dev.musicforge.ui.components.Artwork(item.mediaMetadata.artworkUri?.toString(), 40.dp, corner = 6.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("Up Next", style = MaterialTheme.typography.labelMedium, color = color.copy(alpha = 0.6f), maxLines = 1)
            Text(item.mediaMetadata.title?.toString().orEmpty(), color = color, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(item.mediaMetadata.artist?.toString().orEmpty(), color = color.copy(alpha = 0.5f), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        val press = remember { MutableInteractionSource() }
        Box(Modifier.size(48.dp).clip(RoundedCornerShape(50)).pressScale(press).clickable(interactionSource = press, indication = null, onClickLabel = "Play next") { connection.skipTo(index + 1) }, contentAlignment = Alignment.Center) {
            Icon(ForgeIcons.Play, contentDescription = "Play next", tint = color, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun TransportRow(now: NowPlaying, connection: PlayerConnection, white: Color, accent: Color) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val shuffleTint = if (now.shuffle) accent else white.copy(alpha = 0.55f)
        PlayerIcon(ForgeIcons.Shuffle, if (now.shuffle) "Shuffle on" else "Shuffle off", shuffleTint, 24.dp) { connection.toggleShuffle() }
        PlayerIcon(ForgeIcons.Previous, "Previous", white, 40.dp) { connection.previous() }
        Box(Modifier.size(78.dp), contentAlignment = Alignment.Center) {
            AnimatedContent(
                targetState = now.isPlaying to now.isBuffering,
                transitionSpec = { fadeIn(tween(140)) togetherWith fadeOut(tween(120)) },
                label = "playPause",
            ) { (playing, buffering) ->
                if (buffering && !playing) {
                    BufferingIndicator(Modifier.size(40.dp), color = white)
                } else {
                    PlayerIcon(if (playing) ForgeIcons.Pause else ForgeIcons.Play, if (playing) "Pause" else "Play", white, 56.dp) {
                        connection.togglePlay()
                    }
                }
            }
        }
        val nextTint = if (now.hasNext) white else white.copy(alpha = 0.3f)
        val nextLabel = if (now.hasNext) "Next" else "Next, unavailable"
        PlayerIcon(ForgeIcons.Next, nextLabel, nextTint, 40.dp) { if (now.hasNext) connection.next() }
        val repeatIcon = when (now.repeatMode) {
            androidx.media3.common.Player.REPEAT_MODE_ONE -> ForgeIcons.RepeatOne
            else -> ForgeIcons.Repeat
        }
        val repeatTint = if (now.repeatMode != androidx.media3.common.Player.REPEAT_MODE_OFF) accent else white.copy(alpha = 0.55f)
        val repeatLabel = when (now.repeatMode) {
            androidx.media3.common.Player.REPEAT_MODE_ONE -> "Repeat one"
            androidx.media3.common.Player.REPEAT_MODE_ALL -> "Repeat all"
            else -> "Repeat off"
        }
        PlayerIcon(repeatIcon, repeatLabel, repeatTint, 24.dp) { connection.cycleRepeat() }
    }
}

@Composable
private fun BottomRow(pane: PlayerPane, audioManager: AudioManager, white: Color, showLyrics: Boolean, onSwitch: (PlayerPane) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showLyrics) SmallPaneButton(ForgeIcons.Lyrics, "Lyrics", pane == PlayerPane.LYRICS, white) { onSwitch(PlayerPane.LYRICS) }
        OutputDeviceButton(audioManager, white)
        SmallPaneButton(ForgeIcons.Queue, "Queue", pane == PlayerPane.QUEUE, white) { onSwitch(PlayerPane.QUEUE) }
    }
}

@Composable
private fun SmallPaneButton(icon: ImageVector, label: String, active: Boolean, white: Color, onClick: () -> Unit) {
    val press = remember { MutableInteractionSource() }
    Box(
        Modifier
            .size(56.dp)
            .clip(RoundedCornerShape(50))
            .glass(RoundedCornerShape(50))
            .pressScale(press)
            .background(if (active) white.copy(alpha = 0.18f) else Color.Transparent)
            .clickable(interactionSource = press, indication = null, onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, label, tint = if (active) white else white.copy(alpha = 0.55f), modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun VolumeControlRow(audioManager: AudioManager, color: Color) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val max = remember { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1) }
    var fraction by remember { mutableStateOf(audioManager.getStreamVolume(AudioManager.STREAM_MUSIC) / max.toFloat()) }

    DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == "android.media.VOLUME_CHANGED_ACTION") {
                    val stream = intent.getIntExtra("streamType", -1)
                    if (stream == AudioManager.STREAM_MUSIC) {
                        fraction = intent.getIntExtra("index", 0) / max.toFloat()
                    }
                }
            }
        }
        context.registerReceiver(receiver, IntentFilter("android.media.VOLUME_CHANGED_ACTION"), Context.RECEIVER_NOT_EXPORTED)
        onDispose { context.unregisterReceiver(receiver) }
    }

    var flushJob by remember { mutableStateOf<Job?>(null) }
    fun flushVolume() {
        flushJob?.cancel()
        flushJob = scope.launch {
            val index = (fraction * max).roundToInt()
            withContext(Dispatchers.Default) {
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, index, 0)
            }
        }
    }
    fun flushVolumeDebounced() {
        flushJob?.cancel()
        flushJob = scope.launch {
            delay(120)
            val index = (fraction * max).roundToInt()
            withContext(Dispatchers.Default) {
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, index, 0)
            }
        }
    }

    val iconState = when { fraction <= 0f -> PlayerIcons.VolumeOff; fraction < 0.5f -> PlayerIcons.VolumeDown; else -> PlayerIcons.VolumeUp }
    val iconLabel = if (fraction <= 0f) "Volume muted" else "Volume on"
    Row(
        Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AnimatedContent(iconState, transitionSpec = { fadeIn(tween(120)) togetherWith fadeOut(tween(100)) }, label = "volume") {
            Icon(it, iconLabel, tint = color, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(10.dp))
        Canvas(
            Modifier
                .weight(1f)
                .height(48.dp)
                .semantics {
                    progressBarRangeInfo = ProgressBarRangeInfo(
                        (fraction * max).coerceIn(0f, max.toFloat()),
                        0f..max.toFloat(),
                        max,
                    )
                    setProgress("Adjust volume") { newFraction ->
                        fraction = newFraction.coerceIn(0f, 1f)
                        flushVolume()
                        true
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures { o ->
                        val f = (o.x / size.width).coerceIn(0f, 1f)
                        fraction = f
                        flushVolume()
                    }
                    detectHorizontalDragGestures(
                        onDragStart = {},
                        onHorizontalDrag = { change, _ ->
                            val f = (change.position.x / size.width).coerceIn(0f, 1f)
                            fraction = f
                            flushVolumeDebounced()
                        },
                        onDragEnd = { flushVolume() },
                        onDragCancel = { flushVolume() },
                    )
                },
        ) {
            val h = 6.dp.toPx()
            val top = (size.height - h) / 2f
            val r = h / 2
            drawRoundRect(color.copy(alpha = 0.22f), topLeft = Offset(0f, top), size = Size(size.width, h), cornerRadius = androidx.compose.ui.geometry.CornerRadius(r))
            drawRoundRect(color, topLeft = Offset(0f, top), size = Size(size.width * fraction.coerceIn(0f, 1f), h), cornerRadius = androidx.compose.ui.geometry.CornerRadius(r))
        }
    }
}

@Composable
private fun OutputDeviceButton(audioManager: AudioManager, color: Color) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var deviceInfo by remember { mutableStateOf<AudioDeviceInfo?>(null) }

    fun refreshDevice() = scope.launch {
        deviceInfo = withContext(Dispatchers.Default) { activeOutputDevice(audioManager) }
    }

    LaunchedEffect(Unit) { refreshDevice() }

    DisposableEffect(Unit) {
        val callback = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            object : AudioDeviceCallback() {
                override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) { refreshDevice() }
                override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) { refreshDevice() }
            }
        } else null
        if (callback != null) {
            audioManager.registerAudioDeviceCallback(callback, Handler(Looper.getMainLooper()))
        }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) { refreshDevice() }
        }
        context.registerReceiver(receiver, IntentFilter().apply {
            addAction(AudioManager.ACTION_HEADSET_PLUG)
            addAction(Intent.ACTION_DOCK_EVENT)
            addAction("android.bluetooth.a2dp.connection_state_changed")
            addAction("android.bluetooth.device.action.ACL_CONNECTED")
            addAction("android.bluetooth.device.action.ACL_DISCONNECTED")
        }, Context.RECEIVER_NOT_EXPORTED)
        onDispose {
            context.unregisterReceiver(receiver)
            if (callback != null) audioManager.unregisterAudioDeviceCallback(callback)
        }
    }

    val builtin = deviceInfo == null || deviceInfo?.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
    val icon = if (builtin) PlayerIcons.VolumeUp else PlayerIcons.Headphones
    val label = when {
        builtin -> "This phone"
        else -> deviceInfo?.productName?.toString()?.takeIf { it.isNotBlank() } ?: "This phone"
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(56.dp).clip(RoundedCornerShape(50)).clickable(
                onClickLabel = "Output device: $label",
                onClick = { openOutputSettings(context) },
            ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, "Output: $label", tint = color, modifier = Modifier.size(22.dp))
        }
        Text(label, style = MaterialTheme.typography.labelSmall, color = color.copy(alpha = 0.6f), maxLines = 1, fontSize = 11.sp)
    }
}

private fun activeOutputDevice(audioManager: AudioManager): AudioDeviceInfo? {
    val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
    return devices.firstOrNull { it.type in LISTENER_OUTPUTS }
        ?: devices.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
}

private val LISTENER_OUTPUTS = setOf(
    AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_LINE_ANALOG,
    AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, AudioDeviceInfo.TYPE_BLUETOOTH_SCO, AudioDeviceInfo.TYPE_USB_HEADSET,
    AudioDeviceInfo.TYPE_USB_DEVICE, AudioDeviceInfo.TYPE_HDMI, AudioDeviceInfo.TYPE_HEARING_AID,
    26, 27,
)

private fun openOutputSettings(context: Context) {
    val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        Intent("android.settings.panel.action.MEDIA_OUTPUT")
    } else {
        Intent(Settings.ACTION_SOUND_SETTINGS)
    }
    if (context.startActivitySilently(intent)) return
    if (context.startActivitySilently(Intent(Settings.ACTION_SOUND_SETTINGS))) return
    Toast.makeText(context, "Can't open output settings on this phone", Toast.LENGTH_SHORT).show()
}

private fun Context.startActivitySilently(intent: Intent): Boolean =
    try {
        startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: Exception) {
        false
    }

private fun topBandScrimAlpha(artworkLuminance: Float): Float {
    val luminance = artworkLuminance.coerceIn(0f, 1f)
    return 0.16f + (0.65f - 0.16f) * luminance
}
