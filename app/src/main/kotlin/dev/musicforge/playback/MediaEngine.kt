package dev.musicforge.playback

import android.content.Context
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.TransferListener
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.okhttp.OkHttpDataSource
import dev.musicforge.data.db.MusicDao
import dev.musicforge.extraction.InnerTubeXPlayer
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import timber.log.Timber
import java.io.File

/**
 * Caches and the data-source chain shared by the player and (from Crescendo) downloads.
 *
 * ```
 * player:   downloadCache (read-only) → playerCache (LRU) → resolve → chunked → OkHttp
 * download: downloadCache (writes)                        → resolve → chunked → OkHttp
 * ```
 * Two separate SimpleCache instances, so read-ahead and downloads never contend for a
 * cache key's single writer lock.
 */
@UnstableApi
class MediaEngine(
    private val context: Context,
    private val dao: MusicDao,
    private val resolver: StreamResolver,
    http: OkHttpClient,
    playerCacheBytes: Long,
) {
    val databaseProvider = StandaloneDatabaseProvider(context)
    val playerCache = SimpleCache(File(context.filesDir, "exoplayer"), LeastRecentlyUsedCacheEvictor(playerCacheBytes), databaseProvider)
    val downloadCache = SimpleCache(File(context.filesDir, "download"), NoOpCacheEvictor(), databaseProvider)

    private val http = OkHttpDataSource.Factory(http)

    /**
     * Downloads that reuse another song's bytes (see Duplicates): song id → the key its bytes
     * live under. Filled by Downloads from its index.
     */
    val storageKeys = java.util.concurrent.ConcurrentHashMap<String, String>()

    fun storageKey(id: String): String = storageKeys[id] ?: id

    private fun onRefused(videoId: String, code: Int) {
        if (code !in REFUSED) return
        resolver.cached(videoId)?.let { InnerTubeXPlayer.markStreamClientFailed(dev.musicforge.download.Upgrades.base(videoId), it.client) }
        resolver.invalidate(videoId)
        Timber.w("stream for %s refused with %d; client retired, URL invalidated", videoId, code)
    }

    fun upstream(forDownload: Boolean): DataSource.Factory =
        ResolvingDataSource.Factory(ChunkedDataSource.Factory(http, ::onRefused)) { spec -> resolve(spec, forDownload) }

    /**
     * Where a download's bytes come from: the player cache first (a song just streamed or read
     * ahead is copied, not fetched again; Metrolist and ConvX do the same), then the network for
     * whatever is missing. Resolving runs only on a miss, and a mid-file reopen must match the
     * stored length (see [resolve]), so cached and fetched bytes can't come from two renditions.
     */
    fun downloadUpstream(): DataSource.Factory =
        CacheDataSource.Factory()
            .setCache(playerCache)
            .setCacheKeyFactory { spec -> spec.key ?: spec.uri.toString() }
            .setCacheWriteDataSinkFactory(null)
            .setUpstreamDataSourceFactory(upstream(forDownload = true))
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

    /** Drops a stream URL so the next attempt resolves a fresh one (a failed download's retry). */
    fun forgetStream(videoId: String) = resolver.invalidate(videoId)

    // Synchronous callback from ResolvingDataSource: runs on Media3's load thread (a background
    // thread, not main). runBlocking blocks the load thread for one resolve — acceptable because
    // StreamResolver caches URLs, and the IO is the InnerTube request that can't be deferred.
    private fun resolve(spec: DataSpec, forDownload: Boolean): DataSpec {
        val videoId = spec.key ?: error("no cache key on $spec")
        // Mid-file reopen after a refusal: the new rendition must match what's already cached.
        val expect = if (spec.position > 0) runBlocking { dao.format(videoId)?.contentLength?.takeIf { it > 0 } } else null
        val stream = runBlocking { resolver.resolve(videoId, forDownload, expect) }
        return spec.buildUpon()
            .setUri(stream.url)
            .setHttpRequestHeaders(spec.httpRequestHeaders + stream.headers)
            .setCustomData(stream.contentLength?.let { ChunkPolicy(videoId, it, stream.chunkBytes) })
            .build()
    }

    /** The cache chain for YouTube streams: downloadCache → playerCache → resolve → chunked → OkHttp. */
    private val onlinePlaybackSource: DataSource.Factory =
        CacheDataSource.Factory()
            .setCache(downloadCache)
            .setCacheKeyFactory { spec -> storageKey(spec.key ?: spec.uri.toString()) }
            .setCacheWriteDataSinkFactory(null) // the player never writes into downloads
            .setUpstreamDataSourceFactory(
                CacheDataSource.Factory()
                    .setCache(playerCache)
                    .setUpstreamDataSourceFactory(upstream(forDownload = false))
                    .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR),
            )

    /**
     * Data source for playback. Local files (content://) bypass the cache and the InnerTube
     * resolver entirely — DefaultDataSource serves them straight from MediaStore. YouTube
     * URLs keep the full cache chain. Branching happens per-DataSpec so the player's single
     * source factory handles both without rewriting URIs.
     */
    fun playbackSource(): DataSource.Factory = LocalRoutingDataSourceFactory(
        local = DefaultDataSource.Factory(context),
        online = onlinePlaybackSource,
    )

    /** Bytes the stream cache (playerCache) is currently using. */
    val playerCacheSizeBytes: Long get() = playerCache.cacheSpace

    /** Wipe the stream cache so the next play re-fetches from the network. */
    fun clearPlayerCache() {
        // Copy first: removing while iterating the live key set is asking for trouble.
        playerCache.keys.toList().forEach { playerCache.removeResource(it) }
    }

    companion object {
        val REFUSED = setOf(403, 410, 416)
    }
}

/**
 * Routes a DataSpec to one of two [DataSource.Factory]s by URI scheme: content:// (local files,
 * served straight by DefaultDataSource) skips the cache and InnerTube resolver entirely, while
 * everything else (YouTube streams) keeps the downloadCache → playerCache → resolve chain.
 *
 * A single Media3 DataSource.Factory can't branch by DataSpec alone (createDataSource() has no
 * args), so this returns a DataSource that defers the choice to open(dataSpec), where the scheme
 * is actually known.
 */
@UnstableApi
private class LocalRoutingDataSourceFactory(
    private val local: DataSource.Factory,
    private val online: DataSource.Factory,
) : DataSource.Factory {
    override fun createDataSource(): DataSource = BranchDataSource(local.createDataSource(), online.createDataSource())
}

/** One DataSource, two delegates; picks the right one when open() learns the URI. */
@UnstableApi
private class BranchDataSource(
    private val local: DataSource,
    private val online: DataSource,
) : DataSource {
    private var active: DataSource? = null

    override fun addTransferListener(listener: TransferListener) {
        local.addTransferListener(listener)
        online.addTransferListener(listener)
    }

    override fun open(dataSpec: DataSpec): Long {
        val delegate = if (dataSpec.uri.scheme == "content") local else online
        active = delegate
        return delegate.open(dataSpec)
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
        active?.read(buffer, offset, length) ?: C.RESULT_END_OF_INPUT

    override fun getUri(): Uri? = active?.getUri()

    override fun close() {
        active?.close()
        active = null
    }
}
