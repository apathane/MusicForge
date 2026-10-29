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
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import dev.musicforge.ui.designsystem.BufferingIndicator
import dev.musicforge.ui.designsystem.pressScale
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
        if (!open && offset.value >= heightPx - 1f)         return@BoxWithConstraints

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
                    // Moving and clipping happen on one layer: the sheet's content is recorded once and
                    // the slide is pure compositing, and the rounded clip is a hardware outline clip.
                    shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)
                    // Fully open, nothing is painted behind the sheet (no window background), so
                    // rounded corners would show stale pixels: square it off at rest.
                    clip = offset.value > 0.5f
                },
        ) {
            // The pane decides whether the backdrop fades in under the cover, so the canvas is
            // drawn inside PlayerContent where the pane is known.
            PlayerContent(now, onClose, dismissDrag, ambience)
        }
    }
}

private val playerScrim = Brush.verticalGradient(0f to Color.Black.copy(alpha = 0.18f), 0.55f to Color.Black.copy(alpha = 0.38f), 1f to Color.Black.copy(alpha = 0.72f))

/**
 * The scrim (so white text always reads) baked into the small blurred backdrop once per
 * track: drawing it full-screen every frame was a third full-screen fill on the X7b.
 *
 * [fadeTop] erases the backdrop above that fraction of the screen. On the artwork pane the
 * backdrop then begins exactly where the cover's fade ends, so the two meet at the same
 * ambient colour instead of showing a seam (T4 faded the cover but left the backdrop solid).
 */
private fun withScrim(backdrop: ImageBitmap, fadeTop: Float? = null): ImageBitmap {
    val out = ImageBitmap(backdrop.width, backdrop.height)
    val canvas = Canvas(out)
    val size = androidx.compose.ui.geometry.Size(backdrop.width.toFloat(), backdrop.height.toFloat())
    CanvasDrawScope().draw(androidx.compose.ui.unit.Density(1f), androidx.compose.ui.unit.LayoutDirection.Ltr, canvas, size) {
        drawImage(backdrop)
        drawRect(playerScrim)
        if (fadeTop != null) {
            // DstIn keeps the backdrop only where this mask is opaque; the top stays clear.
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

/** Blurred artwork stretched to the screen; one full-screen draw when there is artwork. */
private fun Modifier.playerBackground(ambience: Ambience, scrimmed: ImageBitmap?): Modifier = drawBehind {
    // The ambient canvas is the target colour for every pane: the cover fades to exactly this,
    // so a flat scrim drawn here would put the band back where the cover meets the controls.
    drawRect(ambience.colors.ambient.takeIf { it != Color.Unspecified } ?: Color(0xFF111114))
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
private fun PlayerContent(now: NowPlaying, onClose: () -> Unit, dismissDrag: Modifier, ambience: Ambience) {
    val context = LocalContext.current
    val graph = context.graph
    val connection = graph.player
    val ambient = ambience.colors.ambient
    val position: State<Long> = remember(connection) { connection.position(200) }.collectAsStateWithLifecycle(0L)
    val prefs by graph.settings.data.collectAsStateWithLifecycle(initialValue = null)
    val doubleTapSeek = prefs?.get(Keys.DoubleTapSeek) ?: true
    val hapticsEnabled = prefs?.get(Keys.HapticsEnabled) ?: true
    var pane by rememberSaveable { mutableStateOf(PlayerPane.ARTWORK) }
    // Back (and the chevron) leave lyrics or the queue for the cover first; only from the cover
    // does back close the player. Registered after the sheet's own handler, so it wins.
    BackHandler(enabled = pane != PlayerPane.ARTWORK) { pane = PlayerPane.ARTWORK }
    var menu by remember { mutableStateOf(false) }
    var sleepMenu by remember { mutableStateOf(false) }
    val liked by remember(now.mediaId) { graph.database.dao().isLiked(now.mediaId) }.collectAsStateWithLifecycle(null)
    val download by remember(now.mediaId) { graph.downloads.state(now.mediaId) }.collectAsStateWithLifecycle(null)
    val sleepAt by connection.sleepAt.collectAsStateWithLifecycle()
    val accent = LocalArtworkColors.current.accent.takeIf { it != Color.Unspecified } ?: Color.White
    val white = Color.White

    // Codec pill from the stored format row (mime + bitrate).
    val format by remember(now.mediaId) { graph.database.dao().formatFlow(now.mediaId) }.collectAsStateWithLifecycle(null)
    val codecLabel = codecPill(format?.mimeType, format?.codecs, format?.bitrate)

    // Up Next: the next item in the queue.
    val queue by connection.queue.collectAsStateWithLifecycle()
    val currentIndex by connection.currentIndex.collectAsStateWithLifecycle()
    val nextItem = queue.getOrNull(currentIndex + 1)

    // System volume and active output device.
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }

    // The backdrop is faded in beneath the cover; other panes keep it from the very top.
    // 0.45 is the cover's share of the screen, so the backdrop is still clear at the seam.
    val scrimmed = remember(ambience.backdrop, pane) {
        ambience.backdrop?.let { withScrim(it, fadeTop = if (pane == PlayerPane.ARTWORK) 0.45f else null) }
    }

    // Insets belong to the controls, not this Box: padding the whole player pushed the cover
    // down and left a grey strip behind the status bar (T4's regression).
    val topInset = WindowInsets.safeDrawing.asPaddingValues().calculateTopPadding()

    BoxWithConstraints(
        Modifier.fillMaxSize()
            .playerBackground(ambience, scrimmed)
            .then(if (pane == PlayerPane.ARTWORK) dismissDrag else Modifier),
    ) {
        // Artwork covers the top ~45% of the sheet when on the artwork pane.
        // No artwork on lyrics/queue panes: the grabber's slot is the top inset instead.
        val artHeight = if (pane == PlayerPane.ARTWORK) maxHeight * 0.45f else topInset + 44.dp

        // Full-bleed cover, edge to edge from y = 0, under the status bar.
        if (pane == PlayerPane.ARTWORK) {
            BigArtwork(
                now, ambient, Modifier
                    .fillMaxWidth()
                    .height(artHeight),
                doubleTap = doubleTapSeek,
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

        // The grabber and chevron ride over the cover but stay below the status bar.
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

        // Controls below the artwork, with horizontal gutter and clear of the nav bar.
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
                .padding(horizontal = 26.dp)
                // No verticalScroll here: the weighted pane holds the queue's LazyColumn, and a
                // lazy list inside a scroll measures with infinite height and crashes. The inset
                // on this Column keeps the controls clear of the gesture bar instead.
                .padding(top = artHeight),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // The middle pane changes; lyrics and queue. Artwork pane has nothing here —
            // the cover is full-bleed at the top.
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                AnimatedContent(pane, transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(140)) }, label = "pane") { p ->
                    when (p) {
                        PlayerPane.ARTWORK -> Unit
                        PlayerPane.LYRICS -> LyricsPane(now, position, accent, onSeek = { connection.seekTo(it) }) { menu = true }
                        PlayerPane.QUEUE -> QueuePane(now, connection) { menu = true }
                    }
                }
            }

            // Title row — only on the artwork pane.
            if (pane == PlayerPane.ARTWORK) {
                TitleRow(now, liked == true, accent, white, onClose, hapticsEnabled, onMenu = { menu = true }, onLike = {
                    connection.queue.value.getOrNull(connection.currentIndex.value)?.let { graph.library.setLiked(it, !(liked == true)) }
                })
            }

            Scrubber(position, now.durationMs, white, onSeek = connection::seekTo, modifier = Modifier.padding(top = 10.dp), codecLabel = codecLabel)

            // Up Next row: the next song's small cover, "Up Next", title, artist. Tap plays it.
            // The queue already lists it, so the card would only repeat the first row there.
            if (nextItem != null && pane != PlayerPane.QUEUE) {
                UpNextRow(nextItem, currentIndex, connection, white)
            }

            // Transport: previous, play/pause (~72 dp), next. Shuffle/repeat moved to queue pane.
            TransportRow(now, connection, white, accent)

            // Volume slider bound to STREAM_MUSIC, following hardware key changes.
            VolumeControlRow(audioManager, white)

            // Bottom row: lyrics, output device, queue. Active pane gets the grey disc.
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
            if (minutes != -1) connection.sleepIn(minutes) // -1 = dismissed
            sleepMenu = false
        }
    }
}

/** Big cover that eases back when paused: the record is resting. Full-bleed (no card/rounded frame);
 *  the fade to the canvas colour is baked into the ambience backdrop once per track. The gradient
 *  overlay at its bottom blends the art into the ambience background below, so there is no dark band. */
@Composable
private fun BigArtwork(
    now: NowPlaying,
    ambient: Color,
    modifier: Modifier = Modifier,
    doubleTap: Boolean = false,
    onDoubleTap: (() -> Unit)? = null,
) {
    val scale by animateFloatAsState(if (now.isPlaying || now.isBuffering) 1f else 0.86f, spring(dampingRatio = 0.62f, stiffness = 260f), label = "artScale")
    // Video thumbnails are letterboxed 4:3; zoom past the bars (see Artwork).
    val letterboxed = dev.musicforge.playback.isLetterboxedThumbnail(now.metadata.artworkUri?.toString().orEmpty())
    val title = now.metadata.title?.toString().orEmpty()
    val artist = now.metadata.artist?.toString().orEmpty()
    // Album art is the screen's primary graphic, not decorative — describe it for TalkBack.
    val contentDescription = when {
        title.isNotEmpty() && artist.isNotEmpty() -> "Album art, $title by $artist"
        title.isNotEmpty() -> "Album art, $title"
        artist.isNotEmpty() -> "Album art, $artist"
        else -> null
    }
    val placeholder = remember { androidx.compose.ui.graphics.painter.ColorPainter(Color.White.copy(alpha = 0.08f)) }
    // A failed load draws a dim grey tile — not the same as the loading tile, so failures are visible.
    val error = remember { androidx.compose.ui.graphics.painter.ColorPainter(Color(0xFFBBBBBB).copy(alpha = 0.4f)) }
    // Fade the bottom edge of the art into the canvas (ambient) colour, not into black, so the
    // cover meets the controls without a visible dark band where two different gradients meet.
    val canvas = ambient.takeIf { it != Color.Unspecified } ?: Color(0xFF111114)
    val doubleTapModifier = if (doubleTap && onDoubleTap != null) {
        Modifier.pointerInput(Unit) {
            detectTapGestures(
                onDoubleTap = { onDoubleTap() },
            )
        }
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
                // No clip or shadow: full-bleed artwork.
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

/** 56 dp grey circular button (like ★ and ⋯ on the right of the title row). */
@Composable
private fun DiscButton(icon: ImageVector, label: String, tint: Color, iconSize: Dp = 26.dp, onClick: () -> Unit) {
    val press = remember { MutableInteractionSource() }
    Box(
        Modifier.size(56.dp).clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.12f))
            .pressScale(press)
            .clickable(interactionSource = press, indication = null, onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, label, tint = tint, modifier = Modifier.size(iconSize))
    }
}

/** Title / artist on the left (tap artist to open them, long-press to copy), ★ and ⋯ discs on the right. */
@Composable
private fun TitleRow(now: NowPlaying, liked: Boolean, accent: Color, white: Color, onClose: () -> Unit, hapticsEnabled: Boolean, onMenu: () -> Unit, onLike: () -> Unit) {
    val title = now.metadata.title?.toString().orEmpty()
    val artist = now.metadata.artist?.toString().orEmpty()
    val clipboard = LocalClipboardManager.current
    val haptics = LocalHapticFeedback.current
    val nav = LocalNavController.current
    Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        // A gap before the buttons, so a scrolling title never slides under the heart.
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            // A long title scrolls once, after a pause, instead of being cut off for good.
            // Long-press copies "title – artist" (Metrolist).
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
                    // Fade the right edge while it scrolls instead of chopping letters in half.
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
            // Tapping the artist opens them, as everywhere else in the app.
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

/** The next song's cover, "Up Next", title, artist. Tap plays it. */
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

/** Transport: previous, play/pause (~72 dp), next. */
@Composable
private fun TransportRow(now: NowPlaying, connection: PlayerConnection, white: Color, accent: Color) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
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
        PlayerIcon(ForgeIcons.Next, nextLabel, nextTint, 40.dp) { connection.next() }
    }
}

/** Bottom row: lyrics, output device, queue. Active pane gets the grey disc. */
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

/** Small circular button for the bottom row (lyrics, queue); active gets a grey disc. */
@Composable
private fun SmallPaneButton(icon: ImageVector, label: String, active: Boolean, white: Color, onClick: () -> Unit) {
    val press = remember { MutableInteractionSource() }
    Box(
        Modifier
            .size(56.dp)
            .clip(RoundedCornerShape(50))
            .pressScale(press)
            .background(if (active) white.copy(alpha = 0.18f) else Color.Transparent)
            .clickable(interactionSource = press, indication = null, onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, label, tint = if (active) white else white.copy(alpha = 0.55f), modifier = Modifier.size(22.dp))
    }
}

/** Volume slider bound to STREAM_MUSIC, following hardware key changes via BroadcastReceiver. */
@Composable
private fun VolumeControlRow(audioManager: AudioManager, color: Color) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val max = remember { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1) }
    var fraction by remember { mutableStateOf(audioManager.getStreamVolume(AudioManager.STREAM_MUSIC) / max.toFloat()) }

    // A hardware volume key presses the STREAM_MUSIC stream and broadcasts it.
    // VOLUME_CHANGED_ACTION and the EXTRA_* keys were removed from the SDK in API 37,
    // so we use the string values directly; the system still sends this broadcast.
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

    // setStreamVolume is binder IPC: flush the local fraction once, off the main thread.
    // Taps apply at once; drags debounce (~120 ms) and apply again when the finger lifts.
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
                // The Canvas itself is 48 dp tall; the 6 dp track is centred inside it. Before,
                // `.height(6.dp).padding(vertical = 21.dp)` left the drawing area at zero and the
                // slider vanished like a magician's assistant — the trick, not the reveal.
                .height(48.dp)
                .semantics {
                    // TalkBack reads the range and current value, and adjusts via the setProgress
                    // action (this Compose version exposes no Role.Slider).
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

/** The active output device: icon + name; tap opens the system output switcher. */
@Composable
private fun OutputDeviceButton(audioManager: AudioManager, color: Color) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // getDevices() is binder IPC — run the lookup off the main thread and post the result back;
    // the callbacks below refresh the same way on audio routing changes.
    var deviceInfo by remember { mutableStateOf<AudioDeviceInfo?>(null) }

    fun refreshDevice() = scope.launch {
        deviceInfo = withContext(Dispatchers.Default) { activeOutputDevice(audioManager) }
    }

    // Initial lookup once the button enters composition (getDevices() is binder IPC, so it runs
    // off the main thread via refreshDevice — running it synchronously here would block composition).
    androidx.compose.runtime.LaunchedEffect(Unit) { refreshDevice() }

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

    // Before the first lookup lands, assume the phone itself; it is right almost every time.
    val builtin = deviceInfo == null || deviceInfo?.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
    val icon = if (builtin) PlayerIcons.VolumeUp else PlayerIcons.Headphones
    // productName on the built-in speaker is the device model ("sdk_gphone64_x86_64" on an
    // emulator), which means nothing to a listener. Bluetooth and wired devices keep their names.
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

/** The currently active output device: a connected headset/bluetooth, or the built-in speaker. */
private fun activeOutputDevice(audioManager: AudioManager): AudioDeviceInfo? {
    val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
    // Only outputs a listener plugs in or pairs count as "not this phone": the emulator (and some
    // phones) also list bus, telephony and remote-submix outputs named after the device model.
    return devices.firstOrNull { it.type in LISTENER_OUTPUTS }
        ?: devices.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
}

private val LISTENER_OUTPUTS = setOf(
    AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_LINE_ANALOG,
    AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, AudioDeviceInfo.TYPE_BLUETOOTH_SCO, AudioDeviceInfo.TYPE_USB_HEADSET,
    AudioDeviceInfo.TYPE_USB_DEVICE, AudioDeviceInfo.TYPE_HDMI, AudioDeviceInfo.TYPE_HEARING_AID,
    26 /* TYPE_BLE_HEADSET, API 31 */, 27 /* TYPE_BLE_SPEAKER, API 31 */,
)

/** Open the system output switcher. The MEDIA_OUTPUT panel is missing on some builds
 *  (MagicOS) and gone entirely on API 35+, so fall back to sound settings — and never crash. */
private fun openOutputSettings(context: Context) {
    val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        Intent("android.settings.panel.action.MEDIA_OUTPUT")
    } else {
        Intent(Settings.ACTION_SOUND_SETTINGS)
    }
    // The output panel is missing on MagicOS and gone on API 35+ — this
    // phone loves taking things away. Try both intents, then fess up.
    if (context.startActivitySilently(intent)) return
    if (context.startActivitySilently(Intent(Settings.ACTION_SOUND_SETTINGS))) return
    Toast.makeText(context, "Can't open output settings on this phone", Toast.LENGTH_SHORT).show()
}

/** Start `intent` as a new task unless nothing on the phone can handle it. */
private fun Context.startActivitySilently(intent: Intent): Boolean =
    try {
        startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (e: RuntimeException) { false }