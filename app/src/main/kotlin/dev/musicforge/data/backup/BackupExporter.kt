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
 * Writes the zip layout Metrolist and ConvX use (`song.db` + `settings.preferences_pb`) plus a
 * small manifest. The layout matches, but the database is ours (Room schema, version 1), so
 * these restore here, not in Metrolist or ConvX.
 */
class BackupExporter(private val context: Context, private val database: MusicDatabase) {

    suspend fun export(uri: Uri) = withContext(Dispatchers.IO) {
        val snapshot = File(context.cacheDir, "export-${System.nanoTime()}.db")
        try {
            snapshotDatabase(snapshot)
            writeZip(uri, snapshot)
        } finally {
            snapshot.delete()
        }
    }

    /**
     * A consistent copy of the library even while it's being written to. VACUUM INTO
     * (SQLite 3.27+, Android 11+) is a transactional snapshot. Older Android: fold the WAL in,
     * then copy while holding the write lock so no checkpoint rewrites pages mid-copy.
     */
    private fun snapshotDatabase(target: File) {
        val db = database.openHelper.writableDatabase
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            db.execSQL("VACUUM INTO ?", arrayOf(target.path))
            return
        }
        db.query("PRAGMA wal_checkpoint(TRUNCATE)").use { it.moveToFirst() }
        database.runInTransaction {
            context.getDatabasePath(MusicDatabase.DB_NAME).inputStream().use { input ->
                target.outputStream().use { input.copyTo(it) }
            }
        }
    }

    private fun writeZip(uri: Uri, dbFile: File) {
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
