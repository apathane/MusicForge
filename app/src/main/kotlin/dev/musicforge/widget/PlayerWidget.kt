package dev.musicforge.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.widget.RemoteViews
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import dev.musicforge.MainActivity
import dev.musicforge.R
import dev.musicforge.playback.PlaybackService

/**
 * Home-screen player. Buttons connect a short-lived controller to the service, so they work
 * whether or not it's running (play on an empty player resumes the saved queue). A widget tap
 * counts as user interaction, which lets the service go foreground. The service pushes state.
 */
class PlayerWidget : AppWidgetProvider() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action !in COMMANDS) return super.onReceive(context, intent)
        val app = context.applicationContext
        val pending = goAsync()
        val future = MediaController.Builder(app, SessionToken(app, ComponentName(app, PlaybackService::class.java))).buildAsync()
        future.addListener({
            val controller = runCatching { future.get() }.getOrNull()
            if (controller != null) {
                when (action) {
                    ACTION_PLAY_PAUSE -> if (controller.isPlaying) controller.pause() else controller.play()
                    ACTION_NEXT -> controller.seekToNext()
                    ACTION_PREV -> controller.seekToPrevious()
                }
            }
            // Give the command a moment to cross the binder before disconnecting.
            Handler(Looper.getMainLooper()).postDelayed({
                controller?.release()
                pending.finish()
            }, 300)
        }, ContextCompat.getMainExecutor(app))
    }

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        // Before the service has said anything, show the last known state (or an idle card).
        push(context, last ?: State(null, null, false, null))
    }

    data class State(val title: String?, val artist: String?, val playing: Boolean, val art: Bitmap?)

    companion object {
        private const val ACTION_PLAY_PAUSE = "dev.musicforge.widget.PLAY_PAUSE"
        private const val ACTION_NEXT = "dev.musicforge.widget.NEXT"
        private const val ACTION_PREV = "dev.musicforge.widget.PREV"
        private val COMMANDS = setOf(ACTION_PLAY_PAUSE, ACTION_NEXT, ACTION_PREV)

        @Volatile private var last: State? = null

        fun push(context: Context, state: State) {
            last = state
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, PlayerWidget::class.java))
            if (ids.isEmpty()) return
            val views = RemoteViews(context.packageName, R.layout.widget_player).apply {
                setTextViewText(R.id.widget_title, state.title ?: context.getString(R.string.widget_idle))
                setTextViewText(R.id.widget_artist, state.artist.orEmpty())
                if (state.art != null) setImageViewBitmap(R.id.widget_art, state.art)
                else setImageViewResource(R.id.widget_art, R.drawable.widget_art_bg)
                setImageViewResource(R.id.widget_play, if (state.playing) R.drawable.ic_w_pause else R.drawable.ic_w_play)
                setContentDescription(R.id.widget_play, if (state.playing) "Pause" else "Play")
                setOnClickPendingIntent(R.id.widget_play, command(context, ACTION_PLAY_PAUSE, 1))
                setOnClickPendingIntent(R.id.widget_next, command(context, ACTION_NEXT, 2))
                setOnClickPendingIntent(R.id.widget_prev, command(context, ACTION_PREV, 3))
                setOnClickPendingIntent(
                    R.id.widget_root,
                    PendingIntent.getActivity(
                        context, 0,
                        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                    ),
                )
            }
            manager.updateAppWidget(ids, views)
        }

        private fun command(context: Context, action: String, requestCode: Int): PendingIntent =
            PendingIntent.getBroadcast(
                context, requestCode,
                Intent(context, PlayerWidget::class.java).setAction(action),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
    }
}
