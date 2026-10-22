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
)

/** The UI's handle on [PlaybackService], via a MediaController. */
class PlayerConnection(private val context: Context) {
    private var controller: MediaController? = null
    private val _nowPlaying = MutableStateFlow<NowPlaying?>(null)
    val nowPlaying: StateFlow<NowPlaying?> = _nowPlaying.asStateFlow()

    fun connect() {
        if (controller != null) return
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener({
            val c = runCatching { future.get() }.getOrNull() ?: return@addListener
            controller = c
            c.addListener(object : Player.Listener {
                override fun onEvents(player: Player, events: Player.Events) = publish(player)
            })
            publish(c)
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
            )
        }
    }

    /** Polls position while collected; stops when the UI stops looking. */
    fun position(periodMs: Long = 250): Flow<Long> = flow {
        while (true) {
            emit(controller?.currentPosition ?: 0L)
            delay(periodMs)
        }
    }

    fun play(items: List<MediaItem>, startIndex: Int = 0) {
        val c = controller ?: return
        c.setMediaItems(items, startIndex, 0L)
        c.prepare()
        c.play()
    }

    fun playNext(item: MediaItem) {
        val c = controller ?: return
        c.addMediaItem((c.currentMediaItemIndex + 1).coerceAtMost(c.mediaItemCount), item)
    }

    fun togglePlay() {
        val c = controller ?: return
        if (c.playerError != null) c.prepare()
        if (c.isPlaying) c.pause() else c.play()
    }

    fun next() = controller?.seekToNextMediaItem()
    fun previous() = controller?.seekToPrevious()
    fun seekTo(ms: Long) = controller?.seekTo(ms)

    fun release() {
        controller?.release()
        controller = null
    }
}
