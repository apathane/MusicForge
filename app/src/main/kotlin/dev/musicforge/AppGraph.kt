package dev.musicforge

import android.app.Application
import android.net.ConnectivityManager
import androidx.annotation.OptIn
import androidx.core.content.getSystemService
import androidx.media3.common.util.UnstableApi
import dev.musicforge.data.backup.BackupExporter
import dev.musicforge.data.backup.BackupImporter
import dev.musicforge.data.db.MusicDatabase
import dev.musicforge.data.prefs.Keys
import dev.musicforge.data.prefs.dataStore
import dev.musicforge.download.Downloads
import dev.musicforge.playback.MediaEngine
import dev.musicforge.playback.PlayRecorder
import dev.musicforge.playback.PlayerConnection
import dev.musicforge.playback.StreamResolver
import dev.musicforge.playback.YouTubeSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * The whole dependency graph. A handful of lazy singletons don't need Hilt, and skipping
 * its annotation processing keeps builds fast on this machine's slow disk.
 */
@OptIn(UnstableApi::class)
class AppGraph(val app: Application) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Set by PlaybackService; the equaliser panel attaches to it. */
    @Volatile var audioSessionId: Int = 0
    val database by lazy { MusicDatabase.open(app) }
    val settings get() = app.dataStore
    val backupImporter by lazy { BackupImporter(app, database, settings) }
    val backupExporter by lazy { BackupExporter(app, database) }
    val autoBackup by lazy { dev.musicforge.data.backup.AutoBackup(app, backupExporter) { database.dao().librarySize() } }

    /** One HTTP client for streams and probes; YouTube API calls use the innertube module's. */
    val http: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    val youTubeSession by lazy { YouTubeSession(app, settings, scope) }

    val resolver by lazy {
        StreamResolver(
            database.dao(), settings, app.getSystemService<ConnectivityManager>()!!, http,
            debugDump = if (BuildConfig.DEBUG) {
                { s -> java.io.File(app.cacheDir, "last_stream.txt").writeText((listOf(s.url) + s.headers.map { "${it.key}: ${it.value}" }).joinToString("\n")) }
            } else {
                null
            },
        )
    }

    val mediaEngine by lazy {
        // Read once at first use; the cache is created with a fixed size.
        val mb = runBlocking { settings.data.first()[Keys.MaxSongCacheSizeMb] }?.takeIf { it > 0 } ?: 1024
        MediaEngine(app, database.dao(), resolver, http, mb * 1024L * 1024L)
    }

    val recorder by lazy { PlayRecorder(database) }

    val downloads by lazy { Downloads(app, mediaEngine, database.dao(), recorder, scope) }

    val library by lazy { dev.musicforge.data.LibraryActions(database, recorder, scope) }

    val player by lazy { PlayerConnection(app) }

    val lyrics by lazy { dev.musicforge.data.lyrics.LyricsRepository(database.dao(), http) }

    /** Colours and blurred backdrop of what's playing; recomputed once per track. */
    val ambience by lazy {
        dev.musicforge.ui.theme.ArtworkAmbience(
            app,
            CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
            player.nowPlaying.map { it?.metadata?.artworkUri?.toString() },
        )
    }
}
