package dev.musicforge.playback.segue

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.datasource.DataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import dev.musicforge.playback.EqualizerProcessor

/**
 * The second deck: it takes over the end of the outgoing song while the main player, which owns
 * the queue, session and notification, moves on to the next song. Created for one blend and
 * released after it, so it costs nothing between songs.
 *
 * It never asks for audio focus: the main player holds it, and two focus owners would duck each
 * other. Its equaliser is its own instance with the same settings, so the tail sounds the same.
 */
class TailDeck(
    private val context: Context,
    private val source: DataSource.Factory,
    val equalizer: EqualizerProcessor,
) {
    private var player: ExoPlayer? = null

    val isActive: Boolean get() = player != null
    val isPlaying: Boolean get() = player?.isPlaying == true
    val positionMs: Long get() = player?.currentPosition ?: 0L
    val isReady: Boolean get() = player?.playbackState == Player.STATE_READY

    /** Loads [item] paused at [positionMs], silent, so the handover is a play() and not a buffer. */
    fun prepare(item: MediaItem, positionMs: Long) {
        release()
        val renderers = object : DefaultRenderersFactory(context) {
            override fun buildAudioSink(context: Context, enableFloatOutput: Boolean, enableAudioTrackPlaybackParams: Boolean): AudioSink =
                DefaultAudioSink.Builder(context)
                    .setAudioProcessors(arrayOf(equalizer))
                    .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                    .build()
        }
        player = ExoPlayer.Builder(context, renderers)
            .setMediaSourceFactory(DefaultMediaSourceFactory(source))
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(),
                /* handleAudioFocus = */ false,
            )
            .build()
            .apply {
                volume = 0f
                playWhenReady = false
                setMediaItem(item, positionMs)
                prepare()
            }
    }

    fun seekTo(positionMs: Long) { player?.seekTo(positionMs) }

    fun play() { player?.play() }

    fun setVolume(volume: Float) { player?.volume = volume.coerceIn(0f, 1f) }

    fun release() {
        player?.release()
        player = null
    }
}
