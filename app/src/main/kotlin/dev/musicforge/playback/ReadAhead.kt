package dev.musicforge.playback

import android.content.Context
import android.net.ConnectivityManager
import android.os.PowerManager
import androidx.annotation.OptIn
import androidx.core.content.getSystemService
import androidx.core.net.toUri
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheWriter
import androidx.media3.exoplayer.offline.Download
import dev.musicforge.download.Downloads
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.InterruptedIOException

/**
 * BitChord's read-ahead: put the next tracks fully into the player cache while the current
 * one plays, so skipping is instant and a dropped connection doesn't stop the music.
 *
 * Budget follows the network and battery: 3 tracks on unmetered networks, 1 on metered,
 * none in battery saver. Never the playing item (Media3 allows one cache writer per key,
 * and the player holds it), never anything downloaded or downloading (the bytes are, or
 * will be, in the download cache already).
 */
@OptIn(UnstableApi::class)
class ReadAhead(
    context: Context,
    private val engine: MediaEngine,
    private val downloads: Downloads,
    private val scope: CoroutineScope,
) {
    private val connectivity = context.getSystemService<ConnectivityManager>()
    private val power = context.getSystemService<PowerManager>()
    private val jobs = HashMap<String, Pair<Job, CacheWriter>>()

    private val source by lazy {
        CacheDataSource.Factory()
            .setCache(engine.playerCache)
            .setUpstreamDataSourceFactory(engine.upstream(forDownload = false))
    }

    private fun budget(): Int = when {
        power?.isPowerSaveMode == true -> 0
        connectivity?.isActiveNetworkMetered != false -> 1
        else -> 3
    }

    /** Call with the queue ids after the current item, in play order. Main thread. */
    fun update(upcoming: List<String>) {
        val wanted = upcoming.asSequence()
            .filter { id -> downloads.states.value[id]?.state.let { it == null || it == Download.STATE_REMOVING } }
            .take(budget())
            .toSet()
        // Cancel what fell out of the window (skipped past, reordered, or budget shrank).
        jobs.keys.filter { it !in wanted }.forEach { cancel(it) }
        for (id in wanted) {
            if (id in jobs) continue
            val spec = DataSpec.Builder()
                .setUri("https://music.youtube.com/watch?v=$id".toUri())
                .setKey(id)
                .build()
            val writer = CacheWriter(source.createDataSource(), spec, null, null)
            val job = scope.launch(Dispatchers.IO) {
                try {
                    writer.cache()
                    Timber.d("read-ahead cached %s", id)
                } catch (_: InterruptedIOException) {
                    // cancelled
                } catch (e: Exception) {
                    Timber.w("read-ahead of %s stopped: %s", id, e.message)
                }
            }
            job.invokeOnCompletion {
                // Only forget this job; a newer one for the same id may have replaced it.
                scope.launch(Dispatchers.Main) { if (jobs[id]?.first === job) jobs.remove(id) }
            }
            jobs[id] = job to writer
        }
    }

    /** The player is about to read this item: release its cache key. */
    fun cancel(id: String) {
        jobs.remove(id)?.let { (job, writer) ->
            writer.cancel()
            job.cancel()
        }
    }

    fun release() {
        jobs.keys.toList().forEach(::cancel)
    }
}
