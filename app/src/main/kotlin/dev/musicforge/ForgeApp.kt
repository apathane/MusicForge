package dev.musicforge

import android.app.Application
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber

class ForgeApp : Application() {
    lateinit var graph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) Timber.plant(Timber.DebugTree())
        // Debug builds log every image request: a failed cover is otherwise just a grey tile.
        if (BuildConfig.DEBUG) {
            coil3.SingletonImageLoader.setSafe { ctx ->
                coil3.ImageLoader.Builder(ctx).logger(coil3.util.DebugLogger()).build()
            }
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
