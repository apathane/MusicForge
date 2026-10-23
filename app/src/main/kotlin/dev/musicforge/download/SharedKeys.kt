package dev.musicforge.download

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.Downloader
import androidx.media3.exoplayer.offline.DownloaderFactory
import androidx.media3.exoplayer.offline.WritableDownloadIndex

/** The key this download's bytes live under, when it shares another song's file. */
fun DownloadRequest.sharedKey(): String? = customCacheKey?.takeIf { it != id }

private fun DownloadRequest.storageKey(): String = customCacheKey ?: id

/** Keeps shared bytes until the last download pointing at them is removed (ConvX's approach). */
@OptIn(UnstableApi::class)
class SharedKeyDownloaderFactory(
    private val delegate: DownloaderFactory,
    private val index: WritableDownloadIndex,
) : DownloaderFactory {
    override fun createDownloader(request: DownloadRequest): Downloader {
        val inner = delegate.createDownloader(request)
        return object : Downloader by inner {
            override fun remove() {
                val key = request.storageKey()
                val stillUsed = index.getDownloads().use { c ->
                    var found = false
                    while (!found && c.moveToNext()) {
                        val other = c.download
                        found = other.request.id != request.id && other.request.storageKey() == key && other.state != Download.STATE_REMOVING
                    }
                    found
                }
                if (!stillUsed) inner.remove()
            }
        }
    }
}
