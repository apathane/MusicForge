package dev.musicforge.data.backup

import android.content.Context
import android.net.Uri
import dev.musicforge.BuildConfig
import dev.musicforge.data.db.MusicDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Writes the same zip layout Metrolist and ConvX use (`song.db` + `settings.preferences_pb`),
 * plus a small manifest so we can recognise our own backups.
 */
class BackupExporter(private val context: Context, private val database: MusicDatabase) {

    suspend fun export(uri: Uri) = withContext(Dispatchers.IO) {
        // Fold the WAL into song.db so the single file is complete.
        database.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)").use { it.moveToFirst() }
        val dbFile = context.getDatabasePath(MusicDatabase.DB_NAME)
        val settingsFile = File(context.filesDir, "datastore/${BackupImporter.SETTINGS_FILE}")
        val output = context.contentResolver.openOutputStream(uri) ?: error("Can't write to the chosen file")
        ZipOutputStream(output.buffered()).use { zip ->
            zip.putNextEntry(ZipEntry(MusicDatabase.DB_NAME))
            dbFile.inputStream().use { it.copyTo(zip) }
            zip.closeEntry()
            if (settingsFile.exists()) {
                zip.putNextEntry(ZipEntry(BackupImporter.SETTINGS_FILE))
                settingsFile.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
            zip.putNextEntry(ZipEntry(BackupImporter.MANIFEST_FILE))
            zip.write(
                """{"app":"musicforge","codename":"${BuildConfig.CODENAME}","version":"${BuildConfig.VERSION_NAME}","createdAt":${System.currentTimeMillis()}}"""
                    .toByteArray(),
            )
            zip.closeEntry()
        }
    }
}
