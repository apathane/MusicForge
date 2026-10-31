package dev.musicforge.playback

import coil3.request.allowHardware
import android.app.PendingIntent
import android.content.Intent
import android.media.audiofx.AudioEffect
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.LibraryResult
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.SettableFuture
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.WatchEndpoint
import dev.musicforge.MainActivity
import dev.musicforge.data.prefs.Keys
import dev.musicforge.graph
import dev.musicforge.automix.AudioCache
import dev.musicforge.automix.AutomixState
import dev.musicforge.automix.CrossfadeController
import dev.musicforge.automix.TrackAnalyzer
import dev.musicforge.automix.TransitionFilterProcessor
import dev.musicforge.automix.TransitionFilters
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
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
        const val WIDGET_ART_PX = 168
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var player: ExoPlayer
    private lateinit var spare: ExoPlayer
    private val equalizer = EqualizerProcessor()
    private val equalizerB = EqualizerProcessor()
    private var activeFilter = TransitionFilterProcessor()
    private var spareFilter = TransitionFilterProcessor()
    private var activeLoud = LoudnessProcessor()
    private var spareLoud = LoudnessProcessor()
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

    private lateinit var trackAnalyzer: TrackAnalyzer
    private lateinit var crossfade: CrossfadeController
    private var trackLoudnessGain = mutableMapOf<String, Float>()

    private fun buildPlayer(eq: EqualizerProcessor, filter: TransitionFilterProcessor, loudness: LoudnessProcessor, ownsFocus: Boolean): ExoPlayer {
        val engine = graph.mediaEngine
        val renderers = object : androidx.media3.exoplayer.DefaultRenderersFactory(this) {
            override fun buildAudioSink(
                context: android.content.Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean,
            ): androidx.media3.exoplayer.audio.AudioSink {
                // EQ and loudness stay Media3 processors on 16-bit PCM; Automix's transition filter
                // runs in BitChord's Float32 path around them, which is what lets the loop mix
                // splice its decks in and keep playing after the song stops feeding it.
                val delegate = androidx.media3.exoplayer.audio.DefaultAudioSink.Builder(context)
                    .setAudioProcessors(arrayOf(eq, loudness))
                    .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                    .build()
                return dev.musicforge.automix.PrecisionAudioSink(
                    delegate = delegate,
                    dspChain = dev.musicforge.automix.DspChain(filter),
                    // 16-bit out: MusicForge's EqualizerProcessor only takes PCM16.
                    enableFloatOutput = false,
                    isAudible = { filter === activeFilter },
                )
            }
        }
        val mediaSourceFactory = DefaultMediaSourceFactory(engine.playbackSource())
        return ExoPlayer.Builder(this, renderers)
            .setMediaSourceFactory(mediaSourceFactory)
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(),
                ownsFocus,
            )
            .setHandleAudioBecomingNoisy(ownsFocus)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()
    }

    override fun onCreate() {
        super.onCreate()
        val engine = graph.mediaEngine

        player = buildPlayer(equalizer, activeFilter, activeLoud, ownsFocus = true)
        spare = buildPlayer(equalizerB, spareFilter, spareLoud, ownsFocus = false)
        spare.audioSessionId = player.audioSessionId

        player.addListener(listener)
        // Let system and third-party equalisers attach to our audio.
        graph.audioSessionId = player.audioSessionId
        sendBroadcast(
            Intent(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION)
                .putExtra(AudioEffect.EXTRA_AUDIO_SESSION, player.audioSessionId)
                .putExtra(AudioEffect.EXTRA_PACKAGE_NAME, packageName)
                .putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC),
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

        trackAnalyzer = TrackAnalyzer(this, AudioCache(engine.downloadCache, engine.playerCache, engine::storageKey, engine.playbackSource()))
        crossfade = CrossfadeController(
            scope = scope,
            active = { player },
            standby = { spare },
            onHandoff = ::adoptPlayer,
            analysisFor = { item -> trackAnalyzer.analysisFor(item.mediaId) },
            requestAnalysis = { item, ms ->
                item.localConfiguration?.uri?.let { uri ->
                    trackAnalyzer.request(item.mediaId, uri, ms / 1000.0)
                }
            },
            filters = object : TransitionFilters {
                override fun incoming(lowPassHz: Float, highPassHz: Float) = activeFilter.setCutoffs(lowPassHz, highPassHz)
                override fun outgoing(lowPassHz: Float, highPassHz: Float) = spareFilter.setCutoffs(lowPassHz, highPassHz)
                override fun incomingLeadMs(): Long = activeFilter.leadUs / 1000
                override fun outgoingLeadMs(): Long = spareFilter.leadUs / 1000
                override fun incomingEcho(delaySeconds: Float, send: Float, dry: Float) = activeFilter.setEcho(delaySeconds, send, dry)
                override fun outgoingEcho(delaySeconds: Float, send: Float, dry: Float) = spareFilter.setEcho(delaySeconds, send, dry)
                override fun parkEchoes() {
                    activeFilter.setEcho(0f, 0f, 1f)
                    spareFilter.setEcho(0f, 0f, 1f)
                }
            },
            analysisRunningFor = { item -> trackAnalyzer.isAnalysing(item.mediaId) },
            onArmIncoming = { item, _ -> spareLoud.trackGain = trackLoudnessGain[item.mediaId] ?: 1f },
            onBlendHeadroom = { trim ->
                activeLoud.blendTrim = trim
                spareLoud.blendTrim = trim
            },
            mixWaveform = { item, durationMs, from, to, bins ->
                item.localConfiguration?.uri?.let { uri ->
                    trackAnalyzer.mixWaveform(item.mediaId, uri, durationMs / 1000.0, from, to, bins)
                }
            },
            loopRegion = { item, durationMs, from, to, rate ->
                item.localConfiguration?.uri?.let { uri ->
                    trackAnalyzer.loopRegion(item.mediaId, uri, durationMs / 1000.0, from, to, rate)
                }
            },
            loopPortOf = { target ->
                when {
                    target === player -> activeFilter.loop
                    target === spare -> spareFilter.loop
                    else -> null
                }
            },
            loudnessGain = { id -> trackLoudnessGain[id] ?: 1f },
            // The loop mix keeps its loops out of sung bars: synced lyrics say where the singing is,
            // on top of the vocal model. Plain or missing lyrics leave it to the model alone.
            syncedLines = { item, durationMs ->
                val meta = item.mediaMetadata
                val lyrics = runCatching {
                    withContext(Dispatchers.IO) { graph.lyrics.lyrics(
                        videoId = item.mediaId,
                        title = meta.title?.toString().orEmpty(),
                        artist = meta.artist?.toString().orEmpty(),
                        album = meta.albumTitle?.toString(),
                        durationSec = (durationMs / 1000).toInt().takeIf { it > 0 },
                    ) }
                }.getOrNull()
                (lyrics as? dev.musicforge.data.lyrics.Lyrics.Synced)?.lines?.map {
                    dev.musicforge.automix.LyricLine(timeMs = it.timeMs, text = it.text, isGap = it.text.isBlank())
                }
            },
        )
        crossfade.start()

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
                .collect { skip ->
                    player.skipSilenceEnabled = skip
                    spare.skipSilenceEnabled = skip
                }
        }
        scope.launch {
            graph.settings.data.map { it[Keys.AudioNormalization] ?: true }.distinctUntilChanged()
                .collect { normalize = it; applyLoudness() }
        }
        scope.launch {
            graph.settings.data
                .map { Triple(it[Keys.CrossfadeEnabled] ?: false, it[Keys.AutomixEnabled] ?: false, it[Keys.CrossfadeSeconds] ?: 6f) }
                .distinctUntilChanged()
                .collect { (crossfadeOn, automixOn, seconds) ->
                    AutomixState.smartFadeEnabled.value = crossfadeOn && automixOn
                    AutomixState.crossfadeSeconds.value = if (crossfadeOn) Math.round(seconds) else 0
                }
        }
        scope.launch {
            graph.settings.data.map { (it[Keys.EqEnabled] ?: false) to EqSettings.parse(it[Keys.EqLevels]) }
                .distinctUntilChanged()
                .collect { (on, levels) ->
                    equalizer.set(on, levels.map { it / 100f })
                    equalizerB.set(on, levels.map { it / 100f })
                }
        }
    }

    override fun onGetSession(controllerInfo: androidx.media3.session.MediaSession.ControllerInfo): MediaLibrarySession = session

    private val likeCommand = androidx.media3.session.SessionCommand(ACTION_LIKE, android.os.Bundle.EMPTY)

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
            browser: androidx.media3.session.MediaSession.ControllerInfo,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<MediaItem>> = Futures.immediateFuture(LibraryResult.ofItem(tree.root(), params))

        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: androidx.media3.session.MediaSession.ControllerInfo,
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
            browser: androidx.media3.session.MediaSession.ControllerInfo,
            mediaId: String,
        ): ListenableFuture<LibraryResult<MediaItem>> = future {
            withContext(Dispatchers.IO) { tree.song(mediaId) }?.let { LibraryResult.ofItem(it, null) }
                ?: LibraryResult.ofError(androidx.media3.session.SessionError.ERROR_BAD_VALUE)
        }

        override fun onSearch(
            session: MediaLibrarySession,
            browser: androidx.media3.session.MediaSession.ControllerInfo,
            query: String,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<Void>> = future {
            val count = withContext(Dispatchers.IO) { tree.search(query).size }
            session.notifySearchResultChanged(browser, query, count, params)
            LibraryResult.ofVoid()
        }

        override fun onGetSearchResult(
            session: MediaLibrarySession,
            browser: androidx.media3.session.MediaSession.ControllerInfo,
            query: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> = future {
            LibraryResult.ofItemList(withContext(Dispatchers.IO) { tree.search(query) }, params)
        }

        override fun onConnect(session: androidx.media3.session.MediaSession, controller: androidx.media3.session.MediaSession.ControllerInfo): androidx.media3.session.MediaSession.ConnectionResult =
            androidx.media3.session.MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(
                    androidx.media3.session.MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon().add(likeCommand).build(),
                )
                .build()

        override fun onCustomCommand(
            session: androidx.media3.session.MediaSession,
            controller: androidx.media3.session.MediaSession.ControllerInfo,
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
            return Futures.immediateFuture(androidx.media3.session.SessionResult(androidx.media3.session.SessionResult.RESULT_SUCCESS))
        }

        override fun onPlaybackResumption(
            mediaSession: androidx.media3.session.MediaSession,
            controller: androidx.media3.session.MediaSession.ControllerInfo,
            isForPlayback: Boolean,
        ): ListenableFuture<androidx.media3.session.MediaSession.MediaItemsWithStartPosition> = future {
            // Headset play button, widget or the system's resume card with nothing loaded.
            val saved = queueStore.load() ?: throw UnsupportedOperationException("no saved queue")
            androidx.media3.session.MediaSession.MediaItemsWithStartPosition(saved.items, saved.index, saved.positionMs)
        }

        override fun onAddMediaItems(
            mediaSession: androidx.media3.session.MediaSession,
            controller: androidx.media3.session.MediaSession.ControllerInfo,
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
                // Restore path can be reached with a stale item whose URI was already rewritten;
                // keep local files (mediaId is a content:// URI) intact so the player reads the
                // on-device file straight from MediaStore.
                if (item.mediaId.startsWith("content://")) {
                    full.buildUpon().setCustomCacheKey(item.mediaId).build()
                } else {
                    full.buildUpon()
                        .setUri("https://music.youtube.com/watch?v=${item.mediaId}")
                        .setCustomCacheKey(item.mediaId)
                        .build()
                }
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
            if (isPlaying) graph.awareness.streamRecovered()
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
            trackBecameCurrent(mediaItem, reason)
        }

        override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) {
            if (reason == Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED) updateReadAhead()
        }

        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
            Timber.e(error, "playback error on %s", player.currentMediaItem?.mediaId)
            graph.awareness.streamFailed()
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

    /** Loudness attenuation for the current track; stored per media id for crossfade. */
    private fun applyLoudness() {
        val id = player.currentMediaItem?.mediaId ?: return
        if (!normalize) {
            trackLoudnessGain[id] = 1f
            activeLoud.trackGain = 1f
            return
        }
        scope.launch {
            val format = withContext(Dispatchers.IO) { graph.database.dao().format(id) }
            if (player.currentMediaItem?.mediaId != id) return@launch
            val measuredLufs = format?.perceptualLoudnessDb ?: format?.loudnessDb?.let { it + TARGET_LUFS }
            val gainDb = measuredLufs?.let { (TARGET_LUFS - it).coerceIn(-15.0, 0.0) } ?: 0.0
            val gain = Math.pow(10.0, gainDb / 20.0).toFloat()
            trackLoudnessGain[id] = gain
            activeLoud.trackGain = gain
            Timber.d("loudness %s: measured %.1f LUFS -> gain %.1f dB", id, measuredLufs ?: Double.NaN, gainDb)
        }
    }

    private fun adoptPlayer(outgoing: ExoPlayer, incoming: ExoPlayer) {
        outgoing.removeListener(listener)
        // Hand audio focus and becoming-noisy from outgoing to incoming.
        val attrs = AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build()
        outgoing.setAudioAttributes(attrs, false)
        incoming.setAudioAttributes(attrs, true)
        outgoing.setHandleAudioBecomingNoisy(false)
        incoming.setHandleAudioBecomingNoisy(true)

        player = incoming
        spare = outgoing
        // Swap filter and loudness roles.
        val tmpFilter = activeFilter
        activeFilter = spareFilter
        spareFilter = tmpFilter
        val tmpLoud = activeLoud
        activeLoud = spareLoud
        spareLoud = tmpLoud

        incoming.addListener(listener)
        session.player = incoming
        trackBecameCurrent(incoming.currentMediaItem, Player.MEDIA_ITEM_TRANSITION_REASON_AUTO)
    }

    private fun trackBecameCurrent(mediaItem: MediaItem?, reason: Int) {
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
            val queued = (0 until player.mediaItemCount).mapTo(java.util.HashSet()) { player.getMediaItemAt(it).mediaId }
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
                            .allowHardware(false) // compressed / parcelled: needs a software bitmap
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
            Intent(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION)
                .putExtra(AudioEffect.EXTRA_AUDIO_SESSION, player.audioSessionId)
                .putExtra(AudioEffect.EXTRA_PACKAGE_NAME, packageName),
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
        crossfade.release()
        trackAnalyzer.release()
        session.release()
        player.release()
        spare.release()
        scope.cancel()
        super.onDestroy()
    }
}