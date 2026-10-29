package dev.musicforge

import android.app.Application
import dev.musicforge.data.prefs.dataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import timber.log.Timber

class ForgeApp : Application() {
    lateinit var graph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) Timber.plant(Timber.DebugTree())
        // Image cache: sized by the MaxImageCacheSize pref (Metrolist's key). Read it off the IO
        // thread — first() blocks, but it's a single prefs read and we're still in onCreate.
        val mb = runBlocking(Dispatchers.IO) { dataStore.data.first()[dev.musicforge.data.prefs.Keys.MaxImageCacheSizeMb] }
            ?.takeIf { it > 0 } ?: 50
        val bytes = mb.toLong() * 1024 * 1024
        coil3.SingletonImageLoader.setSafe { ctx ->
            coil3.ImageLoader.Builder(ctx)
                .diskCache {
                    coil3.disk.DiskCache.Builder()
                        .directory(okio.Path.get(ctx.cacheDir.resolve("images").absolutePath, false))
                        .maxSizeBytes(bytes)
                        .build()
                }
                .logger(if (BuildConfig.DEBUG) coil3.util.DebugLogger() else null)
                .build()
        }
        graph = AppGraph(this)
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
