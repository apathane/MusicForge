package dev.musicforge

import android.app.Application
import dev.musicforge.data.backup.BackupExporter
import dev.musicforge.data.backup.BackupImporter
import dev.musicforge.data.db.MusicDatabase
import dev.musicforge.data.prefs.dataStore

/**
 * The whole dependency graph. A handful of lazy singletons don't need Hilt, and skipping
 * its annotation processing keeps builds fast on this machine's slow disk.
 */
class AppGraph(val app: Application) {
    val database by lazy { MusicDatabase.open(app) }
    val settings get() = app.dataStore
    val backupImporter by lazy { BackupImporter(app, database, settings) }
    val backupExporter by lazy { BackupExporter(app, database) }
}
