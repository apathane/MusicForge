package dev.musicforge.ui.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.musicforge.data.lyrics.Lyrics
import dev.musicforge.data.prefs.Keys
import dev.musicforge.graph
import dev.musicforge.playback.NowPlaying
import dev.musicforge.playback.PlayerConnection
import dev.musicforge.playback.durationSec
import dev.musicforge.playback.from
import dev.musicforge.playback.isFromRadio
import dev.musicforge.ui.components.Artwork
import dev.musicforge.ui.components.ForgeIcons
import dev.musicforge.ui.components.TrackActionsSheet
import dev.musicforge.ui.designsystem.pressScale
import dev.musicforge.ui.theme.LocalArtworkColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

/** Synced lyrics: the current line is bright and large, the rest recede; the list follows
 *  the music unless you're reading ahead, and a tap on any line jumps there.
 */
@Composable
fun LyricsPane(now: NowPlaying, position: State<Long>, accent: Color, onSeek: (Long) -> Unit, onMore: () -> Unit = {}) {
    val graph = LocalContext.current.graph
    val meta = now.metadata
    val connection = graph.player
    val currentId = now.mediaId
    val liked by remember(currentId) { graph.database.dao().isLiked(currentId) }.collectAsStateWithLifecycle(null)
    val white = Color.White
    Column(Modifier.fillMaxSize()) {
        MiniPlayerHeader(now, liked == true, onMore, onLike = {
            connection.queue.value.getOrNull(connection.currentIndex.value)?.let { graph.library.setLiked(it, !(liked == true)) }
        })
        Spacer(Modifier.height(8.dp))
        val lyrics by produceState<Lyrics?>(null, now.mediaId) {
            value = graph.lyrics.lyrics(
                now.mediaId,
                meta.title?.toString().orEmpty(),
                meta.artist?.toString().orEmpty().substringBefore(","),
                meta.albumTitle?.toString(),
                meta.durationSec ?: (now.durationMs / 1000).toInt().takeIf { it > 0 },
            )
        }
        when (val l = lyrics) {
            null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(28.dp), color = white, strokeWidth = 2.dp)
            }
            Lyrics.None -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Lyrics not found", color = white.copy(alpha = 0.6f), style = MaterialTheme.typography.titleMedium, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
            is Lyrics.Plain -> Text(
                l.text,
                color = white.copy(alpha = 0.85f),
                style = MaterialTheme.typography.titleMedium.copy(lineHeight = 30.sp),
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 16.dp),
            )
            is Lyrics.Synced -> SyncedLyrics(l.lines, position, onSeek)
        }
    }
}

@Composable
private fun SyncedLyrics(lines: List<Lyrics.Line>, position: State<Long>, onSeek: (Long) -> Unit) {
    val list = rememberLazyListState()
    val current by remember(lines) {
        derivedStateOf {
            val p = position.value + 250
            lines.indexOfLast { it.timeMs <= p }
        }
    }
    val touchedAt = remember { mutableLongStateOf(0L) }
    LaunchedEffect(current) {
        if (current >= 0 && android.os.SystemClock.uptimeMillis() - touchedAt.longValue > 3_000) {
            list.animateScrollToItem(current, scrollOffset = -list.layoutInfo.viewportSize.height / 3)
        }
    }
    LazyColumn(
        state = list,
        contentPadding = PaddingValues(vertical = 120.dp),
        modifier = Modifier.fillMaxSize().pointerInput(Unit) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                touchedAt.longValue = android.os.SystemClock.uptimeMillis()
            }
        },
    ) {
        itemsIndexed(lines, key = { i, l -> "$i:${l.timeMs}" }) { i, line ->
            val active = i == current
            val color by animateColorAsState(
                if (active) Color.White else Color.White.copy(alpha = if (i < current) 0.32f else 0.42f),
                tween(220),
                label = "lyricColor",
            )
            Text(
                line.text.ifEmpty { "♪" },
                color = color,
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontSize = 25.sp,
                    lineHeight = 32.sp,
                    fontWeight = if (active) FontWeight.W800 else FontWeight.W700,
                ),
                modifier = Modifier.fillMaxWidth().clickable { onSeek(line.timeMs) }.padding(vertical = 9.dp),
            )
        }
    }
}

private fun queueSubtitle(meta: androidx.media3.common.MediaMetadata): String {
    val artist = meta.artist?.toString().orEmpty()
    val duration = meta.durationSec
    val base = if (!meta.isFromRadio) artist else if (artist.isEmpty()) "Radio pick" else "Radio pick · $artist"
    return if (duration != null && duration > 0) "$base · ${clock(duration * 1000L)}" else base
}

/** The queue: tap to jump, drag the handle to reorder, swipe to remove. */
@Composable
fun QueuePane(now: NowPlaying, connection: PlayerConnection, onMore: () -> Unit = {}) {
    val graph = LocalContext.current.graph
    val queue by connection.queue.collectAsStateWithLifecycle()
    val current by connection.currentIndex.collectAsStateWithLifecycle()
    val white = Color.White
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    val currentId = now.mediaId
    val liked by remember(currentId) { graph.database.dao().isLiked(currentId) }.collectAsStateWithLifecycle(null)

    val prefs by graph.settings.data.collectAsStateWithLifecycle(initialValue = null)
    val locked = prefs?.get(Keys.QueueLocked) == true
    val autoplay = prefs?.get(Keys.Autoplay) == true

    val nextKey = remember { mutableLongStateOf(0L) }
    val rows = remember { androidx.compose.runtime.mutableStateListOf<Pair<Long, androidx.media3.common.MediaItem>>() }
    val list = rememberLazyListState(initialFirstVisibleItemIndex = current.coerceAtLeast(0))
    val reorder = rememberReorderableLazyListState(list) { from, to ->
        if (locked) return@rememberReorderableLazyListState
        val f = from.index - 1
        val t = (to.index - 1).coerceIn(0, rows.size - 1)
        if (f in rows.indices) {
            rows.add(t, rows.removeAt(f))
            connection.move(f, t)
        }
    }
    LaunchedEffect(queue) {
        if (!reorder.isAnyItemDragging) {
            // Keep each surviving song's key so a queue change doesn't rebuild every row (and
            // reset a swipe mid-gesture). Repeats of one song reuse its keys in order.
            val spare = rows.groupBy({ it.second.mediaId }, { it.first }).mapValues { ArrayDeque(it.value) }
            val next = queue.map { (spare[it.mediaId]?.removeFirstOrNull() ?: nextKey.longValue++) to it }
            rows.clear()
            rows.addAll(next)
        }
    }

    var removed by remember { mutableStateOf<Pair<Int, androidx.media3.common.MediaItem>?>(null) }
    var cleared by remember { mutableStateOf<List<Pair<Int, androidx.media3.common.MediaItem>>?>(null) }
    LaunchedEffect(removed) { if (removed != null) { delay(6_000); removed = null } }
    LaunchedEffect(cleared) { if (cleared != null) { delay(6_000); cleared = null } }

    var rowMenu by remember { mutableStateOf<androidx.media3.common.MediaItem?>(null) }
    // Explicit flags pulled once per queue load; cheap PK lookups.
    val explicitFlags = remember { androidx.compose.runtime.mutableStateMapOf<String, Boolean>() }
    LaunchedEffect(queue) {
        explicitFlags.clear()
        queue.forEach { item -> explicitFlags[item.mediaId] = (graph.database.dao().songEntity(item.mediaId)?.explicit == true) }
    }

    Column(Modifier.fillMaxSize()) {
        MiniPlayerHeader(now, liked == true, onMore, onLike = {
            connection.queue.value.getOrNull(connection.currentIndex.value)?.let { graph.library.setLiked(it, !(liked == true)) }
        })
        Spacer(Modifier.height(6.dp))
        // Pill toggles: shuffle, repeat, autoplay. No crossfade pill.
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TogglePill(
                label = "Shuffle",
                icon = ForgeIcons.Shuffle,
                on = now.shuffle,
                onClick = { connection.toggleShuffle() },
                modifier = Modifier.weight(1f),
            )
            TogglePill(
                label = "Repeat",
                icon = if (now.repeatMode == androidx.media3.common.Player.REPEAT_MODE_ONE) ForgeIcons.RepeatOne else ForgeIcons.Repeat,
                on = now.repeatMode != androidx.media3.common.Player.REPEAT_MODE_OFF,
                onClick = { connection.cycleRepeat() },
                modifier = Modifier.weight(1f),
            )
            TogglePill(
                label = "Autoplay",
                icon = null,
                on = autoplay,
                onClick = { scope.launch { graph.settings.edit { it[Keys.Autoplay] = !autoplay } } },
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(6.dp))
        // "Playing Next" header with source, Clear and lock.
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Playing Next", style = MaterialTheme.typography.titleMedium, color = white, maxLines = 1)
                val nextItem = queue.getOrNull(current + 1)
                val source = nextItem?.mediaMetadata?.from ?: now.metadata.from
                if (source != null) {
                    Text(
                        if (source == "Radio" || now.metadata.isFromRadio) "From Radio" else "From $source",
                        style = MaterialTheme.typography.labelMedium,
                        color = white.copy(alpha = 0.5f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            // 48 dp touch target for the text button — fingers are not mouse pixels, 40 dp was audacious.
            AnimatedVisibility(removed != null || cleared != null) {
                Box(
                    Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                        .clickable(onClickLabel = "Put the removed songs back") {
                            removed?.let { (at, item) -> connection.insertAt(at, item) }
                            cleared?.sortedByDescending { it.first }?.forEach { (at, item) -> connection.insertAt(at, item) }
                            removed = null
                            cleared = null
                        },
                ) {
                    Text(
                        "Undo",
                        style = MaterialTheme.typography.labelLarge,
                        color = white,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                            .clip(RoundedCornerShape(50))
                            .background(white.copy(alpha = 0.16f)),
                    )
                }
            }
            // 48 dp touch target for the text button (the padded text was ~30 dp tall).
            Box(
                Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                    .clickable(onClickLabel = "Clear the songs after this one") {
                        val qNow = connection.queue.value
                        val idx = connection.currentIndex.value
                        val upcoming = qNow.drop(idx + 1).mapIndexed { i, item -> (idx + 1 + i) to item }
                        if (upcoming.isNotEmpty()) {
                            cleared = upcoming
                            upcoming.sortedByDescending { it.first }.forEach { connection.removeAt(it.first) }
                        }
                    },
            ) {
                Text(
                    "Clear",
                    style = MaterialTheme.typography.labelLarge,
                    color = white.copy(alpha = 0.8f),
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                )
            }
            Box(
                Modifier.size(48.dp).clip(RoundedCornerShape(50))
                    .clickable(onClickLabel = if (locked) "Unlock queue" else "Lock queue") {
                        scope.launch { graph.settings.edit { it[Keys.QueueLocked] = !locked } }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (locked) PlayerIcons.LockClosed else PlayerIcons.LockOpen,
                    contentDescription = if (locked) "Unlock queue" else "Lock queue",
                    tint = if (locked) white else white.copy(alpha = 0.6f),
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        LazyColumn(state = list, modifier = Modifier.fillMaxSize()) {
            itemsIndexed(rows, key = { _, row -> row.first }) { i, (key, item) ->
                ReorderableItem(reorder, key = key) { dragging ->
                    val playing = i == current
                    val dismiss = androidx.compose.material3.rememberSwipeToDismissBoxState()
                    LaunchedEffect(dismiss.currentValue) {
                        if (dismiss.currentValue != androidx.compose.material3.SwipeToDismissBoxValue.Settled) {
                            if (playing || locked) return@LaunchedEffect
                            haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                            removed = i to item
                            connection.removeAt(i)
                        }
                    }
                    androidx.compose.material3.SwipeToDismissBox(
                        state = dismiss,
                        gesturesEnabled = !playing && !locked,
                        backgroundContent = {},
                    ) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                when {
                                    dragging -> white.copy(alpha = 0.20f)
                                    playing -> white.copy(alpha = 0.12f)
                                    else -> Color.Transparent
                                },
                            )
                            .clickable { connection.skipTo(i) }
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Artwork(item.mediaMetadata.artworkUri?.toString(), 42.dp, corner = 6.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(item.mediaMetadata.title?.toString().orEmpty(), color = white, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (explicitFlags[item.mediaId] == true) {
                                    Spacer(Modifier.width(6.dp))
                                    Text("E", color = white.copy(alpha = 0.5f), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                }
                                // Non-colour "now playing" indicator: the row background alone is colour/opacity only.
                                if (playing) {
                                    Spacer(Modifier.width(6.dp))
                                    Icon(
                                        ForgeIcons.Play,
                                        contentDescription = "Now playing",
                                        tint = white.copy(alpha = 0.8f),
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                            }
                            Text(queueSubtitle(item.mediaMetadata), color = white.copy(alpha = 0.6f), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Box(Modifier.size(48.dp).clip(RoundedCornerShape(50)).clickable { rowMenu = item }, contentAlignment = Alignment.Center) {
                            Text("⋯", color = white.copy(alpha = 0.5f), modifier = Modifier.semantics { contentDescription = "More options" })
                        }
                        if (!locked) {
                            Box(
                                Modifier.size(48.dp).draggableHandle(
                                    onDragStarted = { haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress) },
                                ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text("≡", color = white.copy(alpha = 0.6f), style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { contentDescription = "Drag to reorder" })
                            }
                        }
                    }
                    }
                }
            }
        }
    }

    if (rowMenu != null) {
        TrackActionsSheet(rowMenu!!) { rowMenu = null }
    }
}

@Composable
private fun MiniPlayerHeader(now: NowPlaying, liked: Boolean, onMore: () -> Unit, onLike: () -> Unit) {
    val white = Color.White
    val context = LocalContext.current
    val graph = context.graph
    val connection = graph.player
    val accent = LocalArtworkColors.current.accent.takeIf { it != Color.Unspecified } ?: Color.White
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Artwork(now.metadata.artworkUri?.toString(), 40.dp, corner = 6.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(now.metadata.title?.toString().orEmpty(), color = white, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(now.metadata.artist?.toString().orEmpty(), color = white.copy(alpha = 0.6f), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        val likePress = remember { MutableInteractionSource() }
        val morePress = remember { MutableInteractionSource() }
        AnimatedContent(
            targetState = liked to accent,
            transitionSpec = { fadeIn(tween(140)) togetherWith fadeOut(tween(120)) },
            label = "like",
        ) { (l, a) ->
            Box(Modifier.size(48.dp).clip(RoundedCornerShape(50)).pressScale(likePress).clickable(interactionSource = likePress, indication = null, onClickLabel = if (l) "Unlike" else "Like", onClick = onLike), contentAlignment = Alignment.Center) {
                Icon(if (l) ForgeIcons.Heart else ForgeIcons.HeartOutline, contentDescription = if (l) "Unlike" else "Like", tint = if (l) a else white, modifier = Modifier.size(20.dp))
            }
        }
        Box(Modifier.size(48.dp).clip(RoundedCornerShape(50)).pressScale(morePress).clickable(interactionSource = morePress, indication = null, onClickLabel = "More options", onClick = onMore), contentAlignment = Alignment.Center) {
            Icon(ForgeIcons.More, contentDescription = "More options", tint = white, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun TogglePill(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector?, on: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val white = Color.White
    val press = remember { MutableInteractionSource() }
    Row(
        modifier
            .clip(RoundedCornerShape(50))
            .pressScale(press)
            .background(if (on) white else white.copy(alpha = 0.12f))
            .clickable(interactionSource = press, indication = null, onClickLabel = if (on) "$label on" else "$label off", onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = if (on) Color.Black else white, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
        }
        Text(label, style = MaterialTheme.typography.labelMedium, color = if (on) Color.Black else white)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SleepSheet(sleepAt: Long?, onPick: (minutes: Int?) -> Unit) {
    ModalBottomSheet(onDismissRequest = { onPick(-1) }, containerColor = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 12.dp)) {
            Text("Sleep timer", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
            if (sleepAt != null) {
                val left = ((sleepAt - System.currentTimeMillis()) / 60_000).coerceAtLeast(0)
                Text("Stopping in about $left min", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 24.dp))
            }
            val options = listOf(0 to "End of this song", 15 to "15 minutes", 30 to "30 minutes", 45 to "45 minutes", 60 to "1 hour", 90 to "1½ hours")
            options.forEach { (m, label) -> SheetRow(label) { onPick(m) } }
            if (sleepAt != null) SheetRow("Turn off") { onPick(null) }
        }
    }
}

@Composable
private fun SheetRow(label: String, onClick: () -> Unit) {
    val press = remember { MutableInteractionSource() }
    Row(Modifier.fillMaxWidth().pressScale(press).clickable(interactionSource = press, indication = null, onClick = onClick).padding(horizontal = 24.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(ForgeIcons.Sleep, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(16.dp))
        Text(label, style = MaterialTheme.typography.titleSmall)
    }
}
