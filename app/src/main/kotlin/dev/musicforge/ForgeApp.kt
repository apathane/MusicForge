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
        graph = AppGraph(this)
        graph.youTubeSession.start()
        // Off the startup path: a daily safety copy of the library.
        graph.scope.launch {
            delay(30_000)
            graph.autoBackup.runIfDue()
        }
    }
}

val android.content.Context.graph: AppGraph get() = (applicationContext as ForgeApp).graph
