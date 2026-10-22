package dev.musicforge.download

import androidx.media3.common.MediaItem
import dev.musicforge.playback.Credit
import dev.musicforge.playback.albumId
import dev.musicforge.playback.credits
import dev.musicforge.playback.durationSec
import dev.musicforge.playback.forgeMediaItem
import org.json.JSONArray
import org.json.JSONObject

/**
 * What a download request remembers about its song, so the library row can always be
 * rebuilt from the download index alone. Older requests stored just the title as UTF-8.
 */
object DownloadMeta {
    fun encode(item: MediaItem): ByteArray {
        val m = item.mediaMetadata
        return JSONObject()
            .put("t", m.title?.toString().orEmpty())
            .put("a", JSONArray(m.credits.map { JSONArray().put(it.id.orEmpty()).put(it.name) }))
            .put("art", m.artworkUri?.toString().orEmpty())
            .put("al", m.albumId.orEmpty())
            .put("aln", m.albumTitle?.toString().orEmpty())
            .put("d", m.durationSec ?: -1)
            .toString()
            .toByteArray()
    }

    fun decode(id: String, data: ByteArray): MediaItem {
        val text = String(data, Charsets.UTF_8)
        val json = runCatching { JSONObject(text) }.getOrNull()
            ?: return forgeMediaItem(id, text.ifBlank { id }, emptyList(), null)
        val credits = json.optJSONArray("a")?.let { arr ->
            (0 until arr.length()).map { i -> arr.getJSONArray(i).let { Credit(it.optString(0).ifEmpty { null }, it.optString(1)) } }
        }.orEmpty()
        return forgeMediaItem(
            videoId = id,
            title = json.optString("t").ifBlank { id },
            credits = credits,
            artworkUrl = json.optString("art").ifEmpty { null },
            albumId = json.optString("al").ifEmpty { null },
            albumTitle = json.optString("aln").ifEmpty { null },
            durationSec = json.optInt("d", -1).takeIf { it > 0 },
        )
    }

    /** Notification title, whichever format the request uses. */
    fun title(data: ByteArray): String {
        val text = String(data, Charsets.UTF_8)
        return runCatching { JSONObject(text).optString("t") }.getOrNull()?.ifBlank { null } ?: text
    }
}
