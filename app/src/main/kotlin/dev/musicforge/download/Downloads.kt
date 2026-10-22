package dev.musicforge.download

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.annotation.OptIn
import androidx.core.content.getSystemService
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import coil3.imageLoader
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import dev.musicforge.data.db.MusicDao
import dev.musicforge.playback.MediaEngine
import dev.musicforge.playback.PlayRecorder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import timber.log.Timber
import java.util.concurrent.Executors

/**
 * Metrolist's download model, built fresh: Media3 [DownloadManager] with a persistent
 * index, so a download survives the process being killed (MagicOS does that a lot) and
 * resumes from the first byte it doesn't have. Transfers go through the same resolve →
 * chunked path as playback, so they run at line rate instead of googlevideo's throttled
 * pace (BitChord's insight).
 *
 * The DB's `isDownloaded` flag flips only on STATE_COMPLETED, never when a download
 * merely starts.
 */
@OptIn(UnstableApi::class)
class Downloads(
    private val context: Context,
    private val engine: MediaEngine,
    private val dao: MusicDao,
    private val recorder: PlayRecorder,
    private val scope: CoroutineScope,
) {
    private val _states = MutableStateFlow<Map<String, Download>>(emptyMap())
    val states: StateFlow<Map<String, Download>> = _states.asStateFlow()

    /** Bounds the DB/metadata work for a 500-song "download all", before Media3 queues it. */
    private val preparation = Semaphore(3)

    val manager: DownloadManager = DownloadManager(
        context,
        engine.databaseProvider,
        engine.downloadCache,
        engine.upstream(forDownload = true),
        Executors.newFixedThreadPool(3),
    ).apply {
        maxParallelDownloads = 3
        addListener(object : DownloadManager.Listener {
            override fun onDownloadChanged(manager: DownloadManager, download: Download, finalException: Exception?) {
                _states.update { it + (download.request.id to download) }
                val id = download.request.id
                scope.launch {
                    when (download.state) {
                        Download.STATE_COMPLETED -> {
                            dao.setDownloaded(id, true, System.currentTimeMillis())
                            // The bytes live in the download cache now; don't keep a second copy.
                            runCatching { engine.playerCache.removeResource(id) }
                        }
                        Download.STATE_FAILED -> {
                            Timber.w(finalException, "download %s failed", id)
                            dao.setDownloaded(id, false, null)
                        }
                        else -> Unit
                    }
                }
            }

            override fun onDownloadRemoved(manager: DownloadManager, download: Download) {
                _states.update { it - download.request.id }
                scope.launch { dao.setDownloaded(download.request.id, false, null) }
            }
        })
    }

    init {
        // The index survives restarts; load it so the UI knows what's done and what's pending.
        val loaded = HashMap<String, Download>()
        manager.downloadIndex.getDownloads().use { c -> while (c.moveToNext()) loaded[c.download.request.id] = c.download }
        _states.value = loaded
        watchNetwork()
        scope.launch { reconcile(loaded.values) }
    }

    /**
     * The download index is the truth about bytes on disk; make the library agree with it.
     * Recreates song rows that went missing (a restore, a crash mid-write) from the metadata
     * stored with each request, and fixes flags that drifted in either direction.
     */
    private suspend fun reconcile(downloads: Collection<Download>) {
        val done = downloads.filter { it.state == Download.STATE_COMPLETED }
        var repaired = 0
        for (d in done) {
            val id = d.request.id
            val row = dao.songEntity(id)
            if (row == null) {
                recorder.ensureSong(DownloadMeta.decode(id, d.request.data))
                dao.setDownloaded(id, true, d.updateTimeMs)
                repaired++
            } else if (!row.isDownloaded) {
                dao.setDownloaded(id, true, row.dateDownload ?: d.updateTimeMs)
                repaired++
            }
        }
        val doneIds = done.mapTo(HashSet()) { it.request.id }
        dao.downloadedIds().filter { it !in doneIds }.forEach { dao.setDownloaded(it, false, null); repaired++ }
        if (repaired > 0) Timber.i("download reconcile: repaired %d rows", repaired)
    }

    fun state(id: String): Flow<Download?> = states.map { it[id] }

    /** Ids whose bytes are complete on disk; used by backup restore to keep their flags. */
    fun completedIds(): Set<String> = states.value.filterValues { it.state == Download.STATE_COMPLETED }.keys

    fun download(items: List<MediaItem>) {
        items.forEach { item ->
            scope.launch {
                preparation.withPermit {
                    val current = manager.downloadIndex.getDownload(item.mediaId)
                    if (current?.state == Download.STATE_COMPLETED) return@withPermit
                    recorder.ensureSong(item)
                    val request = DownloadRequest.Builder(item.mediaId, item.mediaId.toUri())
                        .setCustomCacheKey(item.mediaId)
                        .setData(DownloadMeta.encode(item))
                        .build()
                    DownloadService.sendAddDownload(context, ForgeDownloadService::class.java, request, false)
                    prefetchArtwork(item)
                }
            }
        }
    }

    fun remove(id: String) = DownloadService.sendRemoveDownload(context, ForgeDownloadService::class.java, id, false)

    /** Artwork goes into Coil's disk cache so offline lists still have covers. */
    private fun prefetchArtwork(item: MediaItem) {
        val url = item.mediaMetadata.artworkUri?.toString() ?: return
        runCatching {
            context.imageLoader.enqueue(
                ImageRequest.Builder(context).data(url)
                    .memoryCachePolicy(CachePolicy.DISABLED)
                    .diskCachePolicy(CachePolicy.ENABLED)
                    .build(),
            )
        }
    }

    /** Three parallel downloads on Wi-Fi, two on metered data so the radio isn't saturated. */
    private fun watchNetwork() {
        val cm = context.getSystemService<ConnectivityManager>() ?: return
        cm.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                val unmetered = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
                val target = if (unmetered) 3 else 2
                if (manager.maxParallelDownloads != target) {
                    android.os.Handler(manager.applicationLooper).post { manager.maxParallelDownloads = target }
                }
            }
        })
    }
}
