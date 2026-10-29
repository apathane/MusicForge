package dev.musicforge

import android.app.Application
import dev.musicforge.data.prefs.Keys
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber

class ForgeApp : Application() {
    lateinit var graph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) Timber.plant(Timber.DebugTree())
        // Coil: debug logs, hardware bitmaps (fast on Adreno 610), and a memory cache sized for 4 GB devices.
        // allowHardware(true) globally is overridden to false in ArtworkAmbience (which reads pixels).
        // crossfade is set per-request in Artwork (only for large covers) — no global fade needed.
        coil3.SingletonImageLoader.setSafe { ctx ->
            coil3.ImageLoader.Builder(ctx)
                .allowHardware(true)
                .memoryCache {
                    coil3.memory.MemoryCache.Builder(ctx).maxSizePercent(ctx, 0.20).build()
                }
                .apply { if (BuildConfig.DEBUG) logger(coil3.util.DebugLogger()) }
                .build()
        }
        graph = AppGraph(this)
        // Pre-fetch the cache size off the main thread; mediaEngine's lazy block needs it.
        graph.scope.launch { graph.songCacheMb = graph.settings.data.first()[Keys.MaxSongCacheSizeMb] ?: 0 }
        graph.youTubeSession.start()
        // Signed in: bring the YouTube Music library up to date, at most hourly (live enough, and
        // gentle on YouTube). The session needs a moment to apply the stored cookie first.
        graph.scope.launch {
            delay(5_000)
            graph.accountSync.syncIfStale()
        }
        // Off the startup path: a daily safety copy of the library.
        graph.scope.launch {
            delay(30_000)
            graph.autoBackup.runIfDue()
        }
        // Bring up the download engine soon after start: it reconciles the library with
        // the bytes actually on disk. DownloadManager wants the main looper.
        graph.scope.launch(kotlinx.coroutines.Dispatchers.Main) {
            delay(3_000)
            graph.downloads
        }
    }
}

val android.content.Context.graph: AppGraph get() = (applicationContext as ForgeApp).graph
