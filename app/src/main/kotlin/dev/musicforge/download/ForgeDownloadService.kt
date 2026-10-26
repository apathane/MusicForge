package dev.musicforge.download

import android.app.Notification
import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import androidx.annotation.OptIn
import androidx.core.app.NotificationCompat
import androidx.core.graphics.drawable.IconCompat
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
            1 -> DownloadMeta.title(downloads[0].request.data)
            else -> "${downloads.size} songs"
        }
        val progress = notifications.buildProgressNotification(this, R.drawable.ic_download, null, title, downloads, notMetRequirements)
        val cancelIntent = PendingIntent.getService(
            this, 0,
            Intent(this, ForgeDownloadService::class.java).setAction(ACTION_CANCEL_ALL),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val cancelCompat = NotificationCompat.Action.Builder(
            IconCompat.createWithResource(this, R.drawable.ic_download),
            "Cancel all",
            cancelIntent,
        ).build()
        // Calculate overall progress from all downloads (0-100)
        val currentProgress = if (downloads.isNotEmpty()) {
            (downloads.sumOf { it.bytesDownloaded.toDouble() } / downloads.sumOf { it.contentLength.toDouble() } * 100).toInt()
        } else 0
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_download)
            .setContentTitle(title)
            .setContentText("Download in progress")
            .setProgress(100, currentProgress, false)
            .setOngoing(true)
            .setAutoCancel(false)
            .setOnlyAlertOnce(true)
            .addAction(cancelCompat)
            .build()
    }

    companion object {
        const val CHANNEL_ID = "downloads"
        const val NOTIFICATION_ID = 2
        const val JOB_ID = 2
        const val ACTION_CANCEL_ALL = "dev.musicforge.download.CANCEL_ALL"
    }
}
