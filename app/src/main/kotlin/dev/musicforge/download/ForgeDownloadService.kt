package dev.musicforge.download

import android.app.Notification
import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadNotificationHelper
import androidx.media3.exoplayer.offline.DownloadService
import androidx.media3.exoplayer.scheduler.PlatformScheduler
import androidx.media3.exoplayer.scheduler.Scheduler
import dev.musicforge.R
import dev.musicforge.graph

/**
 * Foreground service that runs [Downloads]. The PlatformScheduler restarts it through
 * JobScheduler after the process is killed, so pending downloads continue on their own.
 */
@OptIn(UnstableApi::class)
class ForgeDownloadService : DownloadService(
    NOTIFICATION_ID,
    DEFAULT_FOREGROUND_NOTIFICATION_UPDATE_INTERVAL,
    CHANNEL_ID,
    R.string.downloads_channel,
    0,
) {
    private val notifications by lazy { DownloadNotificationHelper(this, CHANNEL_ID) }

    override fun getDownloadManager() = graph.downloads.manager

    override fun getScheduler(): Scheduler = PlatformScheduler(this, JOB_ID)

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CANCEL_ALL) {
            downloadManager.currentDownloads.forEach { downloadManager.removeDownload(it.request.id) }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun getForegroundNotification(downloads: MutableList<Download>, notMetRequirements: Int): Notification {
        val title = when (downloads.size) {
            1 -> Util.fromUtf8Bytes(downloads[0].request.data)
            else -> "${downloads.size} songs"
        }
        val progress = notifications.buildProgressNotification(this, R.drawable.ic_download, null, title, downloads, notMetRequirements)
        val cancel = Notification.Action.Builder(
            Icon.createWithResource(this, R.drawable.ic_download),
            "Cancel all",
            PendingIntent.getService(
                this, 0,
                Intent(this, ForgeDownloadService::class.java).setAction(ACTION_CANCEL_ALL),
                PendingIntent.FLAG_IMMUTABLE,
            ),
        ).build()
        return Notification.Builder.recoverBuilder(this, progress).setActions(cancel).build()
    }

    companion object {
        const val CHANNEL_ID = "downloads"
        const val NOTIFICATION_ID = 2
        const val JOB_ID = 2
        const val ACTION_CANCEL_ALL = "dev.musicforge.download.CANCEL_ALL"
    }
}
