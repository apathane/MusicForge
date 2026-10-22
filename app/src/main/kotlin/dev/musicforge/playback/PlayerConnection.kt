package dev.musicforge.playback

import android.content.ComponentName
import android.content.Context
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow

/** What the UI shows about playback. Position is polled separately; it changes every frame. */
data class NowPlaying(
    val mediaId: String,
    val metadata: MediaMetadata,
    val isPlaying: Boolean,
    val isBuffering: Boolean,
    val durationMs: Long,
    val hasNext: Boolean,
    val hasPrevious: Boolean,
    val error: String?,
    val shuffle: Boolean = false,
    /** Player.REPEAT_MODE_OFF / ONE / ALL */
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val speed: Float = 1f,
)

/** The UI's handle on [PlaybackService], via a MediaController. */
class PlayerConnection(private val context: Context) {
    private var controller: MediaController? = null
    private val _nowPlaying = MutableStateFlow<NowPlaying?>(null)
    val nowPlaying: StateFlow<NowPlaying?> = _nowPlaying.asStateFlow()

    /** The queue, rebuilt only when the timeline changes (radio queues get long). */
    private val _queue = MutableStateFlow<List<MediaItem>>(emptyList())
    val queue: StateFlow<List<MediaItem>> = _queue.asStateFlow()
    private val _index = MutableStateFlow(0)
    val currentIndex: StateFlow<Int> = _index.asStateFlow()

    /** Pause at this wall-clock time, or at the end of the current song when [sleepAtEnd]. */
    private val _sleepAt = MutableStateFlow<Long?>(null)
    val sleepAt: StateFlow<Long?> = _sleepAt.asStateFlow()
    private var sleepAtEnd = false
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private val sleepRunnable = Runnable { controller?.pause(); _sleepAt.value = null }

    private var connecting = false

    /** A tap that lands before the controller connects runs once it does. */
    private var pending: ((MediaController) -> Unit)? = null

    private fun withController(action: (MediaController) -> Unit) {
        controller?.let(action) ?: run {
            pending = action
            connect()
        }
    }

    fun connect() {
        if (controller != null || connecting) return
        connecting = true
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener({
            connecting = false
            val c = runCatching { future.get() }.getOrNull() ?: return@addListener
            controller = c
            c.addListener(object : Player.Listener {
                override fun onEvents(player: Player, events: Player.Events) {
                    if (events.contains(Player.EVENT_TIMELINE_CHANGED)) publishQueue(player)
                    if (events.contains(Player.EVENT_MEDIA_ITEM_TRANSITION) && sleepAtEnd) {
                        sleepAtEnd = false
                        _sleepAt.value = null
                        player.pause()
                    }
                    _index.value = player.currentMediaItemIndex
                    publish(player)
                }
            })
            publishQueue(c)
            publish(c)
            pending?.invoke(c)
            pending = null
        }, ContextCompat.getMainExecutor(context))
    }

    private fun publish(p: Player) {
        val item = p.currentMediaItem
        _nowPlaying.value = item?.let {
            NowPlaying(
                mediaId = it.mediaId,
                metadata = it.mediaMetadata,
                isPlaying = p.isPlaying,
                isBuffering = p.playbackState == Player.STATE_BUFFERING,
                durationMs = p.duration.coerceAtLeast(0),
                hasNext = p.hasNextMediaItem(),
                hasPrevious = p.hasPreviousMediaItem() || p.currentPosition > 3_000,
                error = p.playerError?.let { e -> e.cause?.message ?: e.errorCodeName },
                shuffle = p.shuffleModeEnabled,
                repeatMode = p.repeatMode,
                speed = p.playbackParameters.speed,
            )
        }
    }

    private fun publishQueue(p: Player) {
        _queue.value = (0 until p.mediaItemCount).map { p.getMediaItemAt(it) }
        _index.value = p.currentMediaItemIndex
    }

    /** Polls position while collected; slows to 1 Hz when paused (seeks still show promptly). */
    fun position(periodMs: Long = 250): Flow<Long> = flow {
        while (true) {
            val c = controller
            emit(c?.currentPosition ?: 0L)
            delay(if (c?.isPlaying == true) periodMs else 1_000L)
        }
    }

    fun play(items: List<MediaItem>, startIndex: Int = 0) = withController { c ->
        c.setMediaItems(items, startIndex, 0L)
        c.prepare()
        c.play()
    }

    fun playNext(item: MediaItem) = withController { c ->
        c.addMediaItem((c.currentMediaItemIndex + 1).coerceAtMost(c.mediaItemCount), item)
    }

    fun togglePlay() {
        val c = controller ?: return
        if (c.playerError != null) c.prepare()
        if (c.isPlaying) c.pause() else c.play()
    }

    fun skipTo(index: Int) = withController { it.seekToDefaultPosition(index); it.play() }
    fun removeAt(index: Int) = withController { it.removeMediaItem(index) }
    fun move(from: Int, to: Int) = withController { it.moveMediaItem(from, to) }
    fun toggleShuffle() = withController { it.shuffleModeEnabled = !it.shuffleModeEnabled }
    fun cycleRepeat() = withController {
        it.repeatMode = when (it.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }
    fun setSpeed(speed: Float) = withController { it.setPlaybackSpeed(speed) }

    /** minutes = null cancels; 0 means "at the end of this song". */
    fun sleepIn(minutes: Int?) {
        handler.removeCallbacks(sleepRunnable)
        sleepAtEnd = false
        when {
            minutes == null -> _sleepAt.value = null
            minutes == 0 -> {
                sleepAtEnd = true
                val c = controller
                _sleepAt.value = System.currentTimeMillis() + ((c?.duration ?: 0) - (c?.currentPosition ?: 0)).coerceAtLeast(0)
            }
            else -> {
                val ms = minutes * 60_000L
                _sleepAt.value = System.currentTimeMillis() + ms
                handler.postDelayed(sleepRunnable, ms)
            }
        }
    }

    fun next() = controller?.seekToNextMediaItem()
    fun previous() = controller?.seekToPrevious()
    fun seekTo(ms: Long) = controller?.seekTo(ms)

    fun release() {
        controller?.release()
        controller = null
    }
}
