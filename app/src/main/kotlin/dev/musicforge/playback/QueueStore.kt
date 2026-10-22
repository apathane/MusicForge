package dev.musicforge.playback

import android.content.Context
import androidx.media3.common.MediaItem
import dev.musicforge.data.db.MusicDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Remembers the queue across app restarts (MagicOS kills often): song ids, which one was
 * playing and where. Items are rebuilt from the library, so the file stays tiny. Written
 * atomically (temp file + rename), so a kill mid-write never corrupts it.
 */
class QueueStore(context: Context, private val dao: MusicDao) {
    private val file = File(context.filesDir, "queue.json")

    data class Saved(val items: List<MediaItem>, val index: Int, val positionMs: Long)

    suspend fun save(ids: List<String>, index: Int, positionMs: Long) = withContext(Dispatchers.IO) {
        if (ids.isEmpty()) {
            file.delete()
            return@withContext
        }
        val json = JSONObject()
            .put("ids", JSONArray(ids.take(MAX)))
            .put("index", index.coerceIn(0, minOf(ids.size, MAX) - 1))
            .put("position", positionMs.coerceAtLeast(0))
        val tmp = File(file.path + ".tmp")
        tmp.writeText(json.toString())
        tmp.renameTo(file)
    }

    suspend fun load(): Saved? = withContext(Dispatchers.IO) {
        val json = runCatching { JSONObject(file.readText()) }.getOrNull() ?: return@withContext null
        val ids = json.optJSONArray("ids") ?: return@withContext null
        val wanted = (0 until ids.length()).map { ids.getString(it) }
        val known = dao.songs(wanted).first().associateBy { it.id }
        // Keep the original order; drop ids the library no longer has.
        val items = wanted.mapNotNull { known[it]?.toMediaItem() }
        if (items.isEmpty()) return@withContext null
        val savedIndex = json.optInt("index", 0)
        val current = wanted.getOrNull(savedIndex)
        val index = items.indexOfFirst { it.mediaId == current }.takeIf { it >= 0 } ?: 0
        Saved(items, index, if (index == savedIndex || items[index].mediaId == current) json.optLong("position", 0) else 0)
    }

    private companion object {
        /** A long radio queue doesn't need to come back in full. */
        const val MAX = 200
    }
}
