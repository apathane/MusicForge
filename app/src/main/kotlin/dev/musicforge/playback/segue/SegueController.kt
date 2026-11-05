package dev.musicforge.playback.segue

import android.os.SystemClock
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber
import kotlin.math.abs

/**
 * Runs Segue between the playing song and the next one: measure both ends, plan, hand the
 * outgoing song's tail to the [deck], move the main player to the next song, and blend.
 *
 * The main player never leaves the session: queue, notification, history and Android Auto see an
 * ordinary skip at the handover. The service applies [mainGain] on top of loudness, and calls
 * [onTransition], [onUserSeek] and [onPause] from its listener.
 */
class SegueController(
    private val main: ExoPlayer,
    private val analyzer: SegueAnalyzer,
    private val deck: TailDeck,
    private val scope: CoroutineScope,
    /** Loudness gain of the song the main player is on now (from the service's normaliser). */
    private val loudness: () -> Float,
    /** Asks the service to re-apply the main player's volume after [mainGain] changed. */
    private val onMainGain: () -> Unit,
    private val limits: SegueLimits = SegueLimits(),
) {
    var enabled: Boolean = false
        set(value) {
            field = value
            if (!value) cancel("turned off") else onTransition()
        }

    /** Multiplier on the main player's volume while Segue owns it; 1 otherwise. */
    var mainGain: Float = 1f
        private set

    /** True while a blend is planned or running; the service turns its simple fade off then. */
    val busy: Boolean get() = job?.isActive == true

    private var job: Job? = null
    private var ownSeek = false

    /** A new song started (or the queue changed). Plans the next blend unless we caused it. */
    fun onTransition() {
        if (ownSeek) return
        cancel("new song")
        if (!enabled) return
        job = scope.launch { run() }
    }

    /** The user seeked or skipped. A plan for the old position is no good any more. */
    fun onUserSeek() {
        if (ownSeek) return
        cancel("user seek")
        if (enabled) job = scope.launch { run() }
    }

    /** Pausing mid-blend ends the blend: the next song plays on alone when resumed. */
    fun onPause() {
        if (deck.isActive) cancel("paused")
    }

    fun cancel(reason: String) {
        if (job?.isActive == true || deck.isActive) Timber.d("segue: cancel (%s)", reason)
        job?.cancel()
        job = null
        deck.release()
        setMainGain(1f)
    }

    fun release() = cancel("released")

    private suspend fun run() {
        val outItem = main.currentMediaItem ?: return
        val nextIndex = main.nextMediaItemIndex.takeIf { it != C.INDEX_UNSET } ?: return
        val inItem = main.getMediaItemAt(nextIndex)
        val duration = awaitDuration() ?: return

        // Analyse when the tail is close enough to be cached by the player's buffer.
        awaitPosition(duration - ANALYSE_LEAD_MS)
        val tail = analyzer.analyse(outItem, (duration - TAIL_MS).coerceAtLeast(0), duration)
        val head = analyzer.analyse(inItem, 0, HEAD_MS)
        val plan = planSegue(duration, tail, head, limits)
        Timber.d("segue: plan %s → %s: out@%d in@%d blend %d ms (%s; tail %s, head %s)",
            outItem.mediaId, inItem.mediaId, plan.outStartMs, plan.inStartMs, plan.blendMs, plan.reason,
            tail?.rms?.size ?: "none", head?.rms?.size ?: "none")
        if (main.currentMediaItem?.mediaId != outItem.mediaId) return

        // Load the deck a little before the blend, paused and silent, so starting it is instant.
        awaitPosition(plan.outStartMs - ARM_LEAD_MS)
        deck.prepare(outItem, plan.outStartMs - START_LEAD_MS)
        awaitPosition(plan.outStartMs - START_LEAD_MS - DECK_LATENCY_MS)
        deck.play()
        if (!alignDeck()) {
            Timber.d("segue: deck never lined up; plain end")
            deck.release()
            return
        }

        // Handover: a 30 ms micro-cross between two copies of the same audio, so a few ms of drift
        // can't click, then the main player jumps (silently) to the next song's entry point.
        awaitPosition(plan.outStartMs)
        val loudOut = loudness()
        for (step in 1..3) {
            val g = step / 3f
            deck.setVolume(g * loudOut)
            setMainGain(1f - g)
            delay(10)
        }
        ownSeek = true
        try {
            main.seekTo(nextIndex, plan.inStartMs)
        } finally {
            ownSeek = false
        }
        Timber.d("segue: handover at %d ms (deck %d ms)", plan.outStartMs, deck.positionMs)

        // Blend once the next song is actually sounding; if it never does, the tail plays out alone.
        val startWait = SystemClock.elapsedRealtime()
        while (!(main.isPlaying && main.currentMediaItemIndex == nextIndex)) {
            if (SystemClock.elapsedRealtime() - startWait > NEXT_START_TIMEOUT_MS) {
                Timber.d("segue: next song did not start in time; ending blend")
                break
            }
            delay(TICK_MS)
        }
        val t0 = SystemClock.elapsedRealtime()
        while (scope.isActive) {
            val elapsed = SystemClock.elapsedRealtime() - t0
            val (outGain, inGain) = blendGains(elapsed, plan.blendMs)
            deck.setVolume(outGain * loudOut)
            setMainGain(inGain)
            if (elapsed >= plan.blendMs || !deck.isPlaying) break
            delay(TICK_MS)
        }
        Timber.d("segue: blend done after %d ms", SystemClock.elapsedRealtime() - t0)
        deck.release()
        setMainGain(1f)
    }

    /** Nudges the deck onto the main player's position: up to three tries, 30 ms tolerance. */
    private suspend fun alignDeck(): Boolean {
        repeat(3) { attempt ->
            val start = SystemClock.elapsedRealtime()
            while (!deck.isPlaying) {
                if (SystemClock.elapsedRealtime() - start > 3_000) return false
                delay(5)
            }
            delay(60) // let the reported positions settle after starting
            val drift = deck.positionMs - main.currentPosition
            Timber.d("segue: deck drift %d ms (try %d)", drift, attempt + 1)
            if (abs(drift) <= DRIFT_TOLERANCE_MS) return true
            deck.seekTo(main.currentPosition + DECK_LATENCY_MS)
        }
        return abs(deck.positionMs - main.currentPosition) <= DRIFT_TOLERANCE_MS * 2
    }

    private suspend fun awaitDuration(): Long? {
        repeat(200) {
            val d = main.duration
            if (d != C.TIME_UNSET && d > 0) return d
            delay(50)
        }
        return null
    }

    private suspend fun awaitPosition(targetMs: Long) {
        while (main.currentPosition < targetMs) {
            val remaining = targetMs - main.currentPosition
            delay(remaining.coerceIn(TICK_MS, 1_000))
        }
    }

    private fun setMainGain(g: Float) {
        if (g == mainGain) return
        mainGain = g
        onMainGain()
    }

    companion object {
        const val TAIL_MS = 30_000L
        const val HEAD_MS = 20_000L
        const val ANALYSE_LEAD_MS = 60_000L
        const val ARM_LEAD_MS = 4_000L
        const val START_LEAD_MS = 1_000L
        const val DECK_LATENCY_MS = 120L
        const val DRIFT_TOLERANCE_MS = 30L
        const val NEXT_START_TIMEOUT_MS = 5_000L
        const val TICK_MS = 20L
    }
}

/** Gains for deck (outgoing) and main (incoming) at [elapsedMs] into a [blendMs] blend. */
fun blendGains(elapsedMs: Long, blendMs: Long): Pair<Float, Float> =
    if (blendMs <= 0) 0f to 1f else equalPowerGains(elapsedMs.toFloat() / blendMs)

/** For logs and tests: a short description of a plan, without Android types. */
fun MediaItem.segueLabel(): String = mediaId
