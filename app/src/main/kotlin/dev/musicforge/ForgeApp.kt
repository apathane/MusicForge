package dev.musicforge

import android.app.Application
import timber.log.Timber

class ForgeApp : Application() {
    lateinit var graph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) Timber.plant(Timber.DebugTree())
        graph = AppGraph(this)
    }
}

val android.content.Context.graph: AppGraph get() = (applicationContext as ForgeApp).graph
