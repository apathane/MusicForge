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
    private companion object {
        const val CHECKPOINT_MS = 30_000L
        const val TARGET_LUFS = -14.0
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var player: ExoPlayer
    private lateinit var session: MediaSession
    private val recorder get() = graph.recorder

    // Play-time accounting for the current item.
    private var currentItem: MediaItem? = null
    private var currentDurationMs = -1L
    private var playedMs = 0L
    private var playingSince = -1L
    private var checkpointedMs = 0L
    private var eventWritten = false
    private var checkpointJob: Job? = null
    private var transitionAt = 0L

    private val readAhead by lazy { ReadAhead(this, graph.mediaEngine, graph.downloads, scope) }

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
        // Let system and third-party equalisers attach to our audio.
        graph.audioSessionId = player.audioSessionId
        sendBroadcast(
            android.content.Intent(android.media.audiofx.AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION)
                .putExtra(android.media.audiofx.AudioEffect.EXTRA_AUDIO_SESSION, player.audioSessionId)
                .putExtra(android.media.audiofx.AudioEffect.EXTRA_PACKAGE_NAME, packageName)
                .putExtra(android.media.audiofx.AudioEffect.EXTRA_CONTENT_TYPE, android.media.audiofx.AudioEffect.CONTENT_TYPE_MUSIC),
        )

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
        scope.launch {
            graph.settings.data.map { it[Keys.AudioNormalization] ?: true }.distinctUntilChanged()
                .collect { normalize = it; applyLoudness() }
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
            if (state == Player.STATE_READY) applyLoudness()
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            val now = android.os.SystemClock.elapsedRealtime()
            if (isPlaying) {
                if (transitionAt > 0) {
                    Timber.d("startup: %s audible %d ms after transition", currentItem?.mediaId, now - transitionAt)
                    transitionAt = 0
                }
                playingSince = now
                checkpointJob?.cancel()
                checkpointJob = scope.launch {
                    while (true) {
                        kotlinx.coroutines.delay(CHECKPOINT_MS)
                        checkpoint()
                    }
                }
            } else {
                if (playingSince >= 0) {
                    playedMs += now - playingSince
                    playingSince = -1
                }
                checkpointJob?.cancel()
                checkpoint()
            }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            flushPlay()
            // Seamless auto-advance never stops playing; measure only user-driven starts.
            transitionAt = if (player.isPlaying) 0 else android.os.SystemClock.elapsedRealtime()
            // The UI replaced the queue: radio should follow the new music, not the old seed.
            if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED) resetRadio()
            currentItem = mediaItem
            mediaItem?.let { readAhead.cancel(it.mediaId) }
            updateReadAhead()
            if (mediaItem != null) {
                scope.launch(Dispatchers.IO) { runCatching { recorder.ensureSong(mediaItem) } }
            }
            maybeExtendRadio()
        }

        override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) {
            if (reason == Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED) updateReadAhead()
        }

        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
            Timber.e(error, "playback error on %s", player.currentMediaItem?.mediaId)
        }
    }

    private var normalize = true

    /**
     * Brings loud masters down to YouTube's -14 LUFS reference using the loudness YouTube
     * reports for each track. Attenuation only, so nothing can clip and no extra DSP runs.
     */
    private fun applyLoudness() {
        val id = player.currentMediaItem?.mediaId ?: return
        if (!normalize) {
            player.volume = 1f
            return
        }
        scope.launch {
            val format = withContext(Dispatchers.IO) { graph.database.dao().format(id) }
            if (player.currentMediaItem?.mediaId != id) return@launch
            val measuredLufs = format?.perceptualLoudnessDb ?: format?.loudnessDb?.let { it + TARGET_LUFS }
            val gainDb = measuredLufs?.let { (TARGET_LUFS - it).coerceIn(-15.0, 0.0) } ?: 0.0
            player.volume = Math.pow(10.0, gainDb / 20.0).toFloat()
            Timber.d("loudness %s: measured %.1f LUFS -> gain %.1f dB (volume %.2f)", id, measuredLufs ?: Double.NaN, gainDb, player.volume)
        }
    }

    private fun updateReadAhead() {
        val from = player.currentMediaItemIndex + 1
        val upcoming = (from until minOf(player.mediaItemCount, from + 5)).map { player.getMediaItemAt(it).mediaId }
        readAhead.update(upcoming)
    }

    private fun playedSoFar(): Long {
        val running = if (playingSince >= 0) android.os.SystemClock.elapsedRealtime() - playingSince else 0L
        return playedMs + running
    }

    /** Persist uncounted play time, and the play event once it counts. App scope: survives us. */
    private fun checkpoint() {
        val item = currentItem ?: return
        val played = playedSoFar()
        val delta = played - checkpointedMs
        checkpointedMs = played
        val countNow = !eventWritten && PlayRecorder.counts(played, currentDurationMs)
        if (countNow) eventWritten = true
        if (delta <= 0 && !countNow) return
        graph.scope.launch {
            runCatching {
                recorder.addPlayTime(item, delta)
                if (countNow) recorder.recordPlayEvent(item, played)
            }
        }
    }

    /** Close the books on the item we're leaving. */
    private fun flushPlay() {
        checkpoint()
        playedMs = 0
        playingSince = if (player.isPlaying) android.os.SystemClock.elapsedRealtime() else -1
        checkpointedMs = 0
        eventWritten = false
        currentDurationMs = -1
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
        sendBroadcast(
            android.content.Intent(android.media.audiofx.AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION)
                .putExtra(android.media.audiofx.AudioEffect.EXTRA_AUDIO_SESSION, player.audioSessionId)
                .putExtra(android.media.audiofx.AudioEffect.EXTRA_PACKAGE_NAME, packageName),
        )
        checkpointJob?.cancel()
        readAhead.release()
        flushPlay()
        session.release()
        player.release()
        scope.cancel()
        super.onDestroy()
    }
}
