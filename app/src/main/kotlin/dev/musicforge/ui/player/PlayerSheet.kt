package dev.musicforge.ui.player

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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
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
import dev.musicforge.ui.theme.Ambience
import dev.musicforge.ui.theme.LocalArtworkColors
import kotlinx.coroutines.launch
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
private fun withScrim(backdrop: ImageBitmap): ImageBitmap {
    val out = ImageBitmap(backdrop.width, backdrop.height)
    val canvas = Canvas(out)
    val size = androidx.compose.ui.geometry.Size(backdrop.width.toFloat(), backdrop.height.toFloat())
    CanvasDrawScope().draw(androidx.compose.ui.unit.Density(1f), androidx.compose.ui.unit.LayoutDirection.Ltr, canvas, size) {
        drawImage(backdrop)
        drawRect(playerScrim)
    }
    return out
}

/** Blurred artwork stretched to the screen; one full-screen draw when there is artwork. */
private fun Modifier.playerBackground(ambience: Ambience, scrimmed: ImageBitmap?): Modifier = drawBehind {
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

    // Codec pill from the stored format row (mime + bitrate).
    val format by remember(now.mediaId) { graph.database.dao().formatFlow(now.mediaId) }.collectAsStateWithLifecycle(null)
    val codecLabel = codecPill(format?.mimeType, format?.codecs, format?.bitrate)

    // Up Next: the next item in the queue.
    val queue by connection.queue.collectAsStateWithLifecycle()
    val currentIndex by connection.currentIndex.collectAsStateWithLifecycle()
    val nextItem = queue.getOrNull(currentIndex + 1)

    // System volume and active output device.
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }

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
                    PlayerPane.LYRICS -> LyricsPane(now, position, accent, onSeek = { connection.seekTo(it) }) { menu = true }
                    PlayerPane.QUEUE -> QueuePane(now, connection) { menu = true }
                }
            }
        }

        // Title row — only on the artwork pane; lyrics/queue carry their own mini header.
        if (pane == PlayerPane.ARTWORK) {
            TitleRow(now, liked == true, accent, white, onClose, onMenu = { menu = true }, onLike = {
                connection.queue.value.getOrNull(connection.currentIndex.value)?.let { graph.library.setLiked(it, !(liked == true)) }
            })
        }

        Scrubber(position, now.durationMs, white, onSeek = connection::seekTo, modifier = Modifier.padding(top = 10.dp), codecLabel = codecLabel)

        // Up Next row: the next song's small cover, "Up Next", title, artist. Tap plays it.
        if (nextItem != null) {
            UpNextRow(nextItem, currentIndex, connection, white)
        }

        // Transport: previous, play/pause (~72 dp), next. Shuffle/repeat moved to queue pane.
        TransportRow(now, connection, white, accent)

        // Volume slider bound to STREAM_MUSIC, following hardware key changes.
        VolumeControlRow(audioManager, white)

        // Bottom row: lyrics, output device, queue. Active pane gets the grey disc.
        BottomRow(pane, audioManager, white) { newPane -> pane = newPane }
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
 *  the fade to black is in the ambience backdrop, baked once per track. */
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
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                // No clip or shadow: full-bleed artwork, the fade lives in the ambience bitmap.
            },
    )
}

@Composable
private fun PlayerIcon(icon: ImageVector, label: String, tint: Color, size: Dp, onClick: () -> Unit) {
    Box(
        Modifier.size(maxOf(size + 20.dp, 48.dp)).clip(RoundedCornerShape(50)).clickable(onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, label, tint = tint, modifier = Modifier.size(size))
    }
}

/** 56 dp grey circular button (like ★ and ⋯ on the right of the title row). */
@Composable
private fun DiscButton(icon: ImageVector, label: String, tint: Color, iconSize: Dp = 26.dp, onClick: () -> Unit) {
    Box(
        Modifier.size(56.dp).clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.12f))
            .clickable(onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, label, tint = tint, modifier = Modifier.size(iconSize))
    }
}

/** Title / artist on the left (tap artist to open them, long-press to copy), ★ and ⋯ discs on the right. */
@Composable
private fun TitleRow(now: NowPlaying, liked: Boolean, accent: Color, white: Color, onClose: () -> Unit, onMenu: () -> Unit, onLike: () -> Unit) {
    val title = now.metadata.title?.toString().orEmpty()
    val artist = now.metadata.artist?.toString().orEmpty()
    val clipboard = LocalClipboardManager.current
    val haptics = LocalHapticFeedback.current
    val nav = LocalNavController.current
    Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
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
                            haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                            clipboard.setText(AnnotatedString(listOf(title, artist).filter { it.isNotEmpty() }.joinToString(" – ")))
                        },
                    )
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
        DiscButton(if (liked) ForgeIcons.Heart else ForgeIcons.HeartOutline, if (liked) "Unlike" else "Like", if (liked) accent else white) { onLike() }
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
        Box(Modifier.size(36.dp).clip(RoundedCornerShape(50)).clickable(onClickLabel = "Play next") { connection.skipTo(index + 1) }, contentAlignment = Alignment.Center) {
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
            if (now.isBuffering && !now.isPlaying) {
                CircularProgressIndicator(Modifier.size(40.dp), color = white, strokeWidth = 3.dp)
            } else {
                PlayerIcon(if (now.isPlaying) ForgeIcons.Pause else ForgeIcons.Play, if (now.isPlaying) "Pause" else "Play", white, 56.dp) {
                    connection.togglePlay()
                }
            }
        }
        val nextTint = if (now.hasNext) white else white.copy(alpha = 0.3f)
        PlayerIcon(ForgeIcons.Next, "Next", nextTint, 40.dp) { connection.next() }
    }
}

/** Bottom row: lyrics, output device, queue. Active pane gets the grey disc. */
@Composable
private fun BottomRow(pane: PlayerPane, audioManager: AudioManager, white: Color, onSwitch: (PlayerPane) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SmallPaneButton(ForgeIcons.Lyrics, "Lyrics", pane == PlayerPane.LYRICS, white) { onSwitch(PlayerPane.LYRICS) }
        OutputDeviceButton(audioManager, white)
        SmallPaneButton(ForgeIcons.Queue, "Queue", pane == PlayerPane.QUEUE, white) { onSwitch(PlayerPane.QUEUE) }
    }
}

/** Small circular button for the bottom row (lyrics, queue); active gets a grey disc. */
@Composable
private fun SmallPaneButton(icon: ImageVector, label: String, active: Boolean, white: Color, onClick: () -> Unit) {
    Box(
        Modifier
            .size(56.dp)
            .clip(RoundedCornerShape(50))
            .background(if (active) white.copy(alpha = 0.18f) else Color.Transparent)
            .clickable(onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, label, tint = if (active) white else white.copy(alpha = 0.55f), modifier = Modifier.size(22.dp))
    }
}

/** Volume slider bound to STREAM_MUSIC, following hardware key changes via BroadcastReceiver. */
@Composable
private fun VolumeControlRow(audioManager: AudioManager, color: Color) {
    val context = LocalContext.current
    val max = remember { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1) }
    var fraction by remember { mutableStateOf(audioManager.getStreamVolume(AudioManager.STREAM_MUSIC) / max.toFloat()) }

    // A hardware volume key presses the STREAM_MUSIC stream and broadcasts the change.
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

    val icon = if (fraction <= 0f) PlayerIcons.VolumeOff else PlayerIcons.VolumeUp
    val iconLabel = if (fraction <= 0f) "Volume muted" else "Volume on"
    Row(
        Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, iconLabel, tint = color, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Canvas(
            Modifier
                .weight(1f)
                .height(6.dp)
                .pointerInput(Unit) {
                    detectTapGestures { o ->
                        val f = (o.x / size.width).coerceIn(0f, 1f)
                        fraction = f
                        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, (f * max).roundToInt(), 0)
                    }
                }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragStart = {},
                        onHorizontalDrag = { change, _ ->
                            val f = (change.position.x / size.width).coerceIn(0f, 1f)
                            fraction = f
                            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, (f * max).roundToInt(), 0)
                        },
                        onDragEnd = {},
                        onDragCancel = {},
                    )
                },
        ) {
            val h = size.height
            val r = h / 2
            drawRoundRect(color.copy(alpha = 0.22f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(r))
            drawRoundRect(color, size = Size(size.width * fraction.coerceIn(0f, 1f), h), cornerRadius = androidx.compose.ui.geometry.CornerRadius(r))
        }
    }
}

/** The active output device: icon + name; tap opens the system output switcher. */
@Composable
private fun OutputDeviceButton(audioManager: AudioManager, color: Color) {
    val context = LocalContext.current
    val deviceInfo by produceState(initialValue = activeOutputDevice(audioManager), audioManager) {
        val callback = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            object : AudioDeviceCallback() {
                override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) { value = activeOutputDevice(audioManager) }
                override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) { value = activeOutputDevice(audioManager) }
            }
        } else null
        if (callback != null) {
            audioManager.registerAudioDeviceCallback(callback, Handler(Looper.getMainLooper()))
        }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) { value = activeOutputDevice(audioManager) }
        }
        context.registerReceiver(receiver, IntentFilter().apply {
            addAction(AudioManager.ACTION_HEADSET_PLUG)
            addAction(Intent.ACTION_DOCK_EVENT)
            addAction("android.bluetooth.a2dp.connection_state_changed")
            addAction("android.bluetooth.device.action.ACL_CONNECTED")
            addAction("android.bluetooth.device.action.ACL_DISCONNECTED")
        }, Context.RECEIVER_NOT_EXPORTED)
        awaitDispose {
            context.unregisterReceiver(receiver)
            if (callback != null) audioManager.unregisterAudioDeviceCallback(callback)
        }
    }

    val icon = if (deviceInfo?.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER) PlayerIcons.VolumeUp else PlayerIcons.Headphones
    val label = deviceInfo?.productName?.toString() ?: "Speaker"
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(56.dp).clip(RoundedCornerShape(50)).clickable(
                onClickLabel = "Output device: $label",
                onClick = {
                    val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        Intent("android.settings.panel.action.MEDIA_OUTPUT")
                    } else {
                        Intent(Settings.ACTION_SOUND_SETTINGS)
                    }
                    context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                },
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
    // Look for a non-builtin device first (headphones, bluetooth, usb, hdmi).
    return devices.firstOrNull { it.type != AudioDeviceInfo.TYPE_BUILTIN_SPEAKER && it.type != AudioDeviceInfo.TYPE_BUILTIN_EARPIECE }
        ?: devices.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
}