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
 * into app storage and keep the newest [KEEP]. Restorable from Settings in one tap.
 */
class AutoBackup(
    private val context: Context,
    private val exporter: BackupExporter,
    /** Rows that make a library worth keeping; 0 means there's nothing to protect. */
    private val librarySize: suspend () -> Int,
) {
    val dir: File get() = File(context.filesDir, "auto-backups").apply { mkdirs() }

    /** Newest first: daily copies and pre-restore snapshots together. */
    suspend fun list(): List<File> = withContext(Dispatchers.IO) {
        dir.listFiles { f -> f.name.endsWith(".backup") }.orEmpty().sortedByDescending { it.lastModified() }
    }

    private suspend fun daily(): List<File> = list().filter { it.name.startsWith("auto-") }

    /**
     * Taken right before a restore replaces the library, so picking the wrong backup is never
     * final. Kept apart from the daily rotation; the newest [KEEP_SNAPSHOTS] survive.
     */
    suspend fun snapshotBeforeRestore() = withContext(Dispatchers.IO) {
        if (librarySize() == 0) return@withContext
        write("before-restore-")
        list().filter { it.name.startsWith("before-restore-") }.drop(KEEP_SNAPSHOTS).forEach { it.delete() }
    }

    private suspend fun write(prefix: String) {
        val name = prefix + SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date()) + ".backup"
        val file = File(dir, name)
        runCatching { exporter.export(file.toUri()) }
            .onSuccess { Timber.i("backup written: %s (%d KB)", name, file.length() / 1024) }
            .onFailure { Timber.w(it, "backup %s failed", name); file.delete() }
    }

    suspend fun runIfDue(now: Long = System.currentTimeMillis()) = withContext(Dispatchers.IO) {
        // An empty library has nothing to protect, and its backups would rotate good ones out.
        if (librarySize() == 0) return@withContext
        val newest = daily().firstOrNull()
        if (newest != null && now - newest.lastModified() < DAY_MS) return@withContext
        write("auto-")
        daily().drop(KEEP).forEach { it.delete() }
    }

    private companion object {
        const val KEEP = 7
        const val KEEP_SNAPSHOTS = 3
        const val DAY_MS = 24 * 60 * 60 * 1000L
    }
}
