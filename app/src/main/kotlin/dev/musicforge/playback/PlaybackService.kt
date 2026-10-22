package dev.musicforge.playback

import android.app.PendingIntent
import android.content.Intent
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.WatchEndpoint
import dev.musicforge.MainActivity
import dev.musicforge.data.prefs.Keys
import dev.musicforge.graph
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var player: ExoPlayer
    private lateinit var session: MediaSession
    private val recorder by lazy { PlayRecorder(graph.database) }

    // Play-time accounting for the current item.
    private var currentItem: MediaItem? = null
    private var currentDurationMs = -1L
    private var playedMs = 0L
    private var playingSince = -1L

    private var radioJob: Job? = null
    private var radioContinuation: String? = null
    private var radioSeed: String? = null

    override fun onCreate() {
        super.onCreate()
        val engine = graph.mediaEngine
        player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(engine.playbackSource()))
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(),
                /* handleAudioFocus = */ true,
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()
        player.addListener(listener)

        session = MediaSession.Builder(this, player)
            .setSessionActivity(
                PendingIntent.getActivity(
                    this, 0,
                    Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                ),
            )
            .setCallback(callback)
            .build()

        scope.launch {
            graph.settings.data.map { it[Keys.SkipSilence] ?: false }.distinctUntilChanged()
                .collect { player.skipSilenceEnabled = it }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo) = session

    /** Items from controllers carry metadata but no playable URI; give them ours. */
    private val callback = object : MediaSession.Callback {
        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
        ): ListenableFuture<MutableList<MediaItem>> =
            Futures.immediateFuture(
                mediaItems.map { item ->
                    item.buildUpon()
                        .setUri("https://music.youtube.com/watch?v=${item.mediaId}")
                        .setCustomCacheKey(item.mediaId)
                        .build()
                }.toMutableList(),
            )
    }

    private val listener = object : Player.Listener {
        override fun onPlaybackStateChanged(state: Int) {
            // Duration is only known once the item is prepared; remember it for flushPlay,
            // which runs after the player has already moved on to the next item.
            if (state == Player.STATE_READY && player.duration != C.TIME_UNSET) currentDurationMs = player.duration
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            val now = android.os.SystemClock.elapsedRealtime()
            if (isPlaying) {
                playingSince = now
            } else if (playingSince >= 0) {
                playedMs += now - playingSince
                playingSince = -1
            }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            flushPlay()
            // The UI replaced the queue: radio should follow the new music, not the old seed.
            if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED) resetRadio()
            currentItem = mediaItem
            if (mediaItem != null) {
                scope.launch(Dispatchers.IO) { runCatching { recorder.ensureSong(mediaItem) } }
            }
            maybeExtendRadio()
        }

        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
            Timber.e(error, "playback error on %s", player.currentMediaItem?.mediaId)
        }
    }

    /** Commit the time spent on the item we're leaving. */
    private fun flushPlay() {
        val item = currentItem ?: return
        if (playingSince >= 0) {
            val now = android.os.SystemClock.elapsedRealtime()
            playedMs += now - playingSince
            playingSince = now
        }
        val played = playedMs
        val duration = currentDurationMs
        playedMs = 0
        currentDurationMs = -1
        // App scope, not ours: this also runs from onDestroy, right before our scope is cancelled.
        graph.scope.launch { runCatching { recorder.recordPlay(item, played, duration) } }
    }

    /**
     * Endless listening: when the queue is about to run out, continue with YouTube Music's
     * radio for what's playing, the same "up next" the official app gives.
     */
    private fun maybeExtendRadio() {
        if (player.mediaItemCount - player.currentMediaItemIndex > 3) return
        if (radioJob?.isActive == true) return
        val current = player.currentMediaItem ?: return
        radioJob = scope.launch {
            val seed = radioSeed
            val endpoint = if (seed != null && radioContinuation != null) {
                WatchEndpoint(videoId = seed, playlistId = "RDAMVM$seed")
            } else {
                radioSeed = current.mediaId
                WatchEndpoint(videoId = current.mediaId, playlistId = "RDAMVM${current.mediaId}")
            }
            val result = withContext(Dispatchers.IO) { YouTube.next(endpoint, radioContinuation) }.getOrNull() ?: return@launch
            radioContinuation = result.continuation
            val queued = (0 until player.mediaItemCount).mapTo(HashSet()) { player.getMediaItemAt(it).mediaId }
            val fresh = result.items.filter { it.id !in queued }.map { it.toMediaItem() }
            if (fresh.isNotEmpty()) player.addMediaItems(fresh)
        }
    }

    private fun resetRadio() {
        radioJob?.cancel()
        radioSeed = null
        radioContinuation = null
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        if (!player.playWhenReady || player.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        flushPlay()
        session.release()
        player.release()
        scope.cancel()
        super.onDestroy()
    }
}
