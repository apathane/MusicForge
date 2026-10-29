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
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.SettableFuture
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

@OptIn(UnstableApi::class)
class PlaybackService : MediaLibraryService() {
    private companion object {
        const val CHECKPOINT_MS = 30_000L
        const val TARGET_LUFS = -14.0
        const val ACTION_LIKE = "dev.musicforge.LIKE"
    const val ACTION_START_RADIO = "dev.musicforge.START_RADIO"
    const val EXTRA_MEDIA_ID = "mediaId"
        const val WIDGET_ART_PX = 168
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var player: ExoPlayer
    private val equalizer = EqualizerProcessor()
    private lateinit var session: MediaLibrarySession
    private val tree by lazy { BrowseTree(graph.database.dao()) }
    private val recorder get() = graph.recorder

    // Play-time accounting for the current item.
    private var currentItem: MediaItem? = null
    private var currentDurationMs = -1L
    private var playedMs = 0L
    private var playingSince = -1L
    private var checkpointedMs = 0L
    private var eventWritten = false
    private var scrobbled = false
    private var startedAtSec = 0L
    private var checkpointJob: Job? = null
    private var transitionAt = 0L
    private var consecutiveErrors = 0
    private var lastErrorAt = 0L
    // YouTube Music history (push): a nonce and the account cookie captured on item start, so a
    // play is registered against the account that was listening — not a later one.
    private var playStartNonce: String? = null
    private var playStartCookie: String? = null

    private val readAhead by lazy { ReadAhead(this, graph.mediaEngine, graph.downloads, scope) }
    private val queueStore by lazy { QueueStore(this, graph.database.dao()) }

    private var radioJob: Job? = null
    private var widgetJob: Job? = null
    private var widgetArt: Pair<String, android.graphics.Bitmap?>? = null
    private var radioContinuation: String? = null
    private var radioSeed: String? = null

    override fun onCreate() {
        super.onCreate()
        val engine = graph.mediaEngine
        // The equaliser runs inside our audio pipeline, so it works on every phone.
        val renderers = object : androidx.media3.exoplayer.DefaultRenderersFactory(this) {
            override fun buildAudioSink(
                context: android.content.Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean,
            ): androidx.media3.exoplayer.audio.AudioSink =
                androidx.media3.exoplayer.audio.DefaultAudioSink.Builder(context)
                    .setAudioProcessors(arrayOf(equalizer))
                    .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                    .build()
        }
        player = ExoPlayer.Builder(this, renderers)
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

        session = MediaLibrarySession.Builder(this, player, callback)
            .setSessionActivity(
                PendingIntent.getActivity(
                    this, 0,
                    Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                ),
            )
            .setMediaButtonPreferences(listOf(likeButton(false)))
            .build()

        // Pick up where you left off, paused: nothing is fetched until you press play.
        scope.launch {
            if (graph.settings.data.first()[Keys.PersistentQueue] == false || player.mediaItemCount > 0) return@launch
            val saved = queueStore.load() ?: return@launch
            if (player.mediaItemCount == 0) {
                player.setMediaItems(saved.items, saved.index, saved.positionMs)
                player.playWhenReady = false
            }
        }
        scope.launch {
            graph.settings.data.map { it[Keys.Autoplay] ?: true }.distinctUntilChanged().collect {
                autoplay = it
                if (!it) radioJob?.cancel()
            }
        }
        scope.launch {
            graph.settings.data.map { it[Keys.SkipSilence] ?: false }.distinctUntilChanged()
                .collect { player.skipSilenceEnabled = it }
        }
        scope.launch {
            graph.settings.data.map { it[Keys.AudioNormalization] ?: true }.distinctUntilChanged()
                .collect { normalize = it; applyLoudness() }
        }
        scope.launch {
            graph.settings.data.map { (it[Keys.EqEnabled] ?: false) to EqSettings.parse(it[Keys.EqLevels]) }
                .distinctUntilChanged()
                .collect { (on, levels) -> equalizer.set(on, levels.map { it / 100f }) }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession = session

    private val likeCommand = androidx.media3.session.SessionCommand(ACTION_LIKE, android.os.Bundle.EMPTY)
    private val startRadioCommand = androidx.media3.session.SessionCommand(
        ACTION_START_RADIO, android.os.Bundle().apply { putString(EXTRA_MEDIA_ID, "") },
    )

    private fun likeButton(liked: Boolean) =
        androidx.media3.session.CommandButton.Builder(
            if (liked) androidx.media3.session.CommandButton.ICON_HEART_FILLED else androidx.media3.session.CommandButton.ICON_HEART_UNFILLED,
        )
            .setDisplayName(if (liked) "Unlike" else "Like")
            .setSessionCommand(likeCommand)
            .build()

    private var likeJob: Job? = null

    /** Keep the notification's heart in step with the library, whoever changes it. */
    private fun watchLiked(id: String?) {
        likeJob?.cancel()
        if (id == null) return
        likeJob = scope.launch {
            graph.database.dao().isLiked(id).distinctUntilChanged().collect { liked ->
                session.setMediaButtonPreferences(listOf(likeButton(liked == true)))
            }
        }
    }

    /** Runs [block] on our scope and hands the result to Media3 as a future. */
    private fun <T> future(block: suspend () -> T): ListenableFuture<T> {
        val f = SettableFuture.create<T>()
        scope.launch {
            runCatching { block() }.onSuccess { f.set(it) }.onFailure { f.setException(it) }
        }
        return f
    }

    private val callback = object : MediaLibrarySession.Callback {
        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<MediaItem>> = Futures.immediateFuture(LibraryResult.ofItem(tree.root(), params))

        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> = future {
            val all = withContext(Dispatchers.IO) { tree.children(parentId) }
            val from = (page * pageSize).coerceAtMost(all.size)
            LibraryResult.ofItemList(all.subList(from, (from + pageSize).coerceAtMost(all.size)), params)
        }

        override fun onGetItem(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            mediaId: String,
        ): ListenableFuture<LibraryResult<MediaItem>> = future {
            withContext(Dispatchers.IO) { tree.song(mediaId) }?.let { LibraryResult.ofItem(it, null) }
                ?: LibraryResult.ofError(androidx.media3.session.SessionError.ERROR_BAD_VALUE)
        }

        override fun onSearch(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            query: String,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<Void>> = future {
            val count = withContext(Dispatchers.IO) { tree.search(query).size }
            session.notifySearchResultChanged(browser, query, count, params)
            LibraryResult.ofVoid()
        }

        override fun onGetSearchResult(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            query: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> = future {
            LibraryResult.ofItemList(withContext(Dispatchers.IO) { tree.search(query) }, params)
        }

        override fun onConnect(session: MediaSession, controller: MediaSession.ControllerInfo): MediaSession.ConnectionResult =
            MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(
                    MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon().add(likeCommand).add(startRadioCommand).build(),
                )
                .build()

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: androidx.media3.session.SessionCommand,
            args: android.os.Bundle,
        ): ListenableFuture<androidx.media3.session.SessionResult> {
            if (customCommand.customAction == ACTION_LIKE) {
                val item = player.currentMediaItem
                if (item != null) {
                    scope.launch {
                        val liked = withContext(Dispatchers.IO) { graph.database.dao().songEntity(item.mediaId)?.liked == true }
                        graph.library.setLiked(item, !liked)
                    }
                }
            }
            if (customCommand.customAction == ACTION_START_RADIO) {
                val seed = args.getString(EXTRA_MEDIA_ID)
                if (seed != null) {
                    // Start radio rooted at [seed]: wipe any stale continuation so the first fetch
                    // begins fresh from this station's RDAMVM playlist.
                    radioSeed = seed
                    radioContinuation = null
                    maybeExtendRadio()
                }
            }
            return Futures.immediateFuture(androidx.media3.session.SessionResult(androidx.media3.session.SessionResult.RESULT_SUCCESS))
        }

        override fun onPlaybackResumption(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            isForPlayback: Boolean,
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> = future {
            // Headset play button, widget or the system's resume card with nothing loaded.
            val saved = queueStore.load() ?: throw UnsupportedOperationException("no saved queue")
            MediaSession.MediaItemsWithStartPosition(saved.items, saved.index, saved.positionMs)
        }

        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
        ): ListenableFuture<MutableList<MediaItem>> = future {
            // Voice search from a car ("play Portishead"): resolve the query into songs.
            mediaItems.singleOrNull()?.requestMetadata?.searchQuery?.let { q ->
                val found = SearchPlay.resolve(graph, q)
                if (found.isNotEmpty()) return@future found.map {
                    it.buildUpon().setUri("https://music.youtube.com/watch?v=${it.mediaId}").setCustomCacheKey(it.mediaId).build()
                }.toMutableList()
            }
            mediaItems.map { item ->
                // Cars and watches send back only the id: restore title, artists and artwork.
                val full = if (item.mediaMetadata.title == null) withContext(Dispatchers.IO) { tree.song(item.mediaId) } ?: item else item
                full.buildUpon()
                    .setUri("https://music.youtube.com/watch?v=${item.mediaId}")
                    .setCustomCacheKey(item.mediaId)
                    .build()
            }.toMutableList()
        }
    }

    private val listener = object : Player.Listener {
        override fun onPlaybackStateChanged(state: Int) {
            // Duration is only known once the item is prepared; remember it for flushPlay,
            // which runs after the player has already moved on to the next item.
            if (state == Player.STATE_READY && player.duration != C.TIME_UNSET) currentDurationMs = player.duration
            if (state == Player.STATE_READY) applyLoudness()
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            // Read-ahead and radio wait for actual listening (not a restored, paused queue).
            if (playWhenReady) {
                updateReadAhead()
                maybeExtendRadio()
                applyLoudness() // also undoes anything a sleep fade left behind
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            val now = android.os.SystemClock.elapsedRealtime()
            if (isPlaying) {
                // listened_at is when the song actually started playing, not when it was queued.
                if (startedAtSec == 0L) startedAtSec = System.currentTimeMillis() / 1000
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
            updateWidget()
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            flushPlay()
            // Seamless auto-advance never stops playing; measure only user-driven starts.
            transitionAt = if (player.isPlaying) 0 else android.os.SystemClock.elapsedRealtime()
            // The UI replaced the queue: radio should follow the new music, not the old seed.
            if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED) resetRadio()
            currentItem = mediaItem
            watchLiked(mediaItem?.mediaId)
            mediaItem?.let { graph.scope.launch { runCatching { graph.listenBrainz.playingNow(it) } } }
            // Capture a fresh nonce and the cookie for YouTube Music history push; registerPlayback
            // is called later from checkpoint() once the play has counted.
            playStartNonce = com.metrolist.innertubex.extraction.generateClientPlaybackNonce()
            playStartCookie = YouTube.cookie
            mediaItem?.let { readAhead.cancel(it.mediaId) }
            updateReadAhead()
            if (mediaItem != null) {
                scope.launch(Dispatchers.IO) { runCatching { recorder.ensureSong(mediaItem) } }
            }
            maybeExtendRadio()
            updateWidget()
        }

        override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) {
            if (reason == Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED) updateReadAhead()
        }

        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
            Timber.e(error, "playback error on %s", player.currentMediaItem?.mediaId)
            // One bad track (removed, region-locked, offline and not cached) shouldn't end the
            // music: move on, but stop after a few failures in a row instead of chewing the queue.
            val now = android.os.SystemClock.elapsedRealtime()
            consecutiveErrors = if (now - lastErrorAt < 15_000) consecutiveErrors + 1 else 1
            lastErrorAt = now
            if (consecutiveErrors <= 3 && player.hasNextMediaItem()) {
                player.seekToNextMediaItem()
                player.prepare()
                player.play()
            }
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
        if (!player.playWhenReady) return
        val from = player.currentMediaItemIndex + 1
        val upcoming = (from until minOf(player.mediaItemCount, from + 5)).map { player.getMediaItemAt(it).mediaId }
        readAhead.update(upcoming)
    }

    private fun playedSoFar(): Long {
        val running = if (playingSince >= 0) android.os.SystemClock.elapsedRealtime() - playingSince else 0L
        return playedMs + running
    }

    private fun saveQueue() {
        val count = minOf(player.mediaItemCount, 200)
        val ids = (0 until count).map { player.getMediaItemAt(it).mediaId }
        val index = player.currentMediaItemIndex
        val position = player.currentPosition
        graph.scope.launch { runCatching { queueStore.save(ids, index, position) } }
    }

    /** Persist uncounted play time, and the play event once it counts. App scope: survives us. */
    private fun checkpoint() {
        saveQueue()
        val item = currentItem ?: return
        val played = playedSoFar()
        val delta = played - checkpointedMs
        checkpointedMs = played
        val countNow = !eventWritten && PlayRecorder.counts(played, currentDurationMs)
        if (countNow) eventWritten = true
        // ListenBrainz's own rule: half the song or 4 minutes, whichever comes first.
        val scrobbleNow = !scrobbled && currentDurationMs > 30_000 && played >= minOf(240_000L, currentDurationMs / 2)
        if (scrobbleNow) {
            scrobbled = true
            val started = startedAtSec
            graph.scope.launch { runCatching { graph.listenBrainz.scrobble(item, started) } }
        }
        // YouTube Music history push: register the play against the account that was listening.
        if (countNow) {
            val nonce = playStartNonce
            val cookie = playStartCookie
            if (nonce != null && cookie != null) {
                graph.scope.launch {
                    val pauseHistory = graph.settings.data.first().get(Keys.PauseListenHistory) ?: false
                    if (cookie == YouTube.cookie && !pauseHistory) {
                        runCatching {
                            YouTube.registerPlayback(
                                videoId = item.mediaId,
                                clientPlaybackNonce = nonce,
                                isHistoryEnabled = { true },
                            )
                        }.onFailure { Timber.w(it, "registerPlayback failed for %s", item.mediaId) }
                    }
                }
            }
        }
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
        scrobbled = false
        startedAtSec = if (player.isPlaying) System.currentTimeMillis() / 1000 else 0L
        currentDurationMs = -1
    }

    /**
     * Endless listening: when the queue is about to run out, continue with YouTube Music's
     * radio for what's playing, the same "up next" the official app gives.
     */
    private var autoplay = true

    private fun maybeExtendRadio() {
        if (!autoplay || !player.playWhenReady) return
        if (player.mediaItemCount - player.currentMediaItemIndex > 3) return
        if (radioJob?.isActive == true) return
        val current = player.currentMediaItem ?: return
        radioJob = scope.launch {
            // The stored value, not the cached one: right after a cold start, or a switch flipped
            // while this fetch was in flight, the cache can still say on.
            suspend fun allowed() = graph.settings.data.first()[Keys.Autoplay] != false
            if (!allowed()) return@launch
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
            val fresh = result.items.filterFresh(queued) { it.id }.map { it.toMediaItem().markedFromRadio() }
            if (fresh.isNotEmpty() && allowed()) player.addMediaItems(fresh)
        }
    }

    /** Push now-playing to the home-screen widget; artwork is fetched once per track, small. */
    private fun updateWidget() {
        val item = player.currentMediaItem
        val meta = item?.mediaMetadata
        val playing = player.isPlaying
        widgetJob?.cancel()
        widgetJob = scope.launch {
            val id = item?.mediaId
            val art = when {
                id == null -> null
                widgetArt?.first == id -> widgetArt?.second
                else -> withContext(Dispatchers.IO) {
                    runCatching {
                        val request = coil3.request.ImageRequest.Builder(this@PlaybackService)
                            .data(meta?.artworkUri)
                            .size(WIDGET_ART_PX)
                            .build()
                        val result = coil3.SingletonImageLoader.get(this@PlaybackService).execute(request) as? coil3.request.SuccessResult
                        (result?.image as? coil3.BitmapImage)?.bitmap
                    }.getOrNull()
                }.also { widgetArt = id to it }
            }
            dev.musicforge.widget.PlayerWidget.push(
                this@PlaybackService,
                dev.musicforge.widget.PlayerWidget.State(meta?.title?.toString(), meta?.artist?.toString(), playing, art),
            )
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
        widgetJob?.cancel()
        dev.musicforge.widget.PlayerWidget.push(
            this,
            dev.musicforge.widget.PlayerWidget.State(
                player.currentMediaItem?.mediaMetadata?.title?.toString(),
                player.currentMediaItem?.mediaMetadata?.artist?.toString(),
                false,
                widgetArt?.second,
            ),
        )
        readAhead.release()
        player.removeListener(listener)
        flushPlay()
        session.release()
        player.release()
        scope.cancel()
        super.onDestroy()
    }
}
