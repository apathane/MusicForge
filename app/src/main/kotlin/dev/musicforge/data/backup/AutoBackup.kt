package dev.musicforge.data.backup

import android.content.Context
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * A safety net: at most once a day, write a normal backup (same format as the manual one)
 * into app storage and keep the newest [KEEP]. Restorable from Settings in one tap, and
 * readable by Metrolist/ConvX too since the zip layout is theirs.
 */
class AutoBackup(private val context: Context, private val exporter: BackupExporter) {
    val dir: File get() = File(context.filesDir, "auto-backups").apply { mkdirs() }

    fun list(): List<File> = dir.listFiles { f -> f.name.endsWith(".backup") }.orEmpty().sortedByDescending { it.name }

    suspend fun runIfDue(now: Long = System.currentTimeMillis()) = withContext(Dispatchers.IO) {
        val newest = list().firstOrNull()
        if (newest != null && now - newest.lastModified() < DAY_MS) return@withContext
        val name = "auto-" + SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date(now)) + ".backup"
        val file = File(dir, name)
        runCatching { exporter.export(file.toUri()) }
            .onSuccess { Timber.i("auto backup written: %s (%d KB)", name, file.length() / 1024) }
            .onFailure { Timber.w(it, "auto backup failed"); file.delete() }
        list().drop(KEEP).forEach { it.delete() }
    }

    private companion object {
        const val KEEP = 7
        const val DAY_MS = 24 * 60 * 60 * 1000L
    }
}
