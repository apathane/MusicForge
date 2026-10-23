package dev.musicforge.download

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Downloads don't travel in Metrolist/ConvX backups (only a flag does). After a restore we keep
 * the list of songs the old app had downloaded, so they can be brought back in one go.
 */
class Redownloads(context: Context) {
    data class Pending(val from: String, val ids: List<String>)

    private val file = File(context.filesDir, "redownload.json")
    private val _pending = MutableStateFlow(load())
    val pending: StateFlow<Pending?> = _pending.asStateFlow()

    suspend fun offer(from: String, ids: List<String>) = withContext(Dispatchers.IO) {
        if (ids.isEmpty()) return@withContext clear()
        val tmp = File(file.path + ".tmp")
        tmp.writeText(JSONObject().put("from", from).put("ids", JSONArray(ids)).toString())
        tmp.renameTo(file)
        _pending.value = Pending(from, ids)
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        file.delete()
        _pending.value = null
    }

    private fun load(): Pending? = runCatching {
        val json = JSONObject(file.readText())
        val ids = json.getJSONArray("ids")
        Pending(json.optString("from", "your old app"), (0 until ids.length()).map { ids.getString(it) })
    }.getOrNull()?.takeIf { it.ids.isNotEmpty() }
}
