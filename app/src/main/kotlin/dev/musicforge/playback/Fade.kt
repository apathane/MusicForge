package dev.musicforge.playback

/**
 * Gain (0..1) for the fade between two tracks on the one player: rise over the first [fadeMs] of
 * a track, fall over its last [fadeMs]. Unknown length (negative [durationMs]) means no tail fade.
 *
 * This is a fade through silence, not an overlap: a second player would be needed for that.
 * Lengths shorter than two fades still fade both ways; the smaller gain wins, so nothing jumps.
 */
internal fun fadeGain(positionMs: Long, durationMs: Long, fadeMs: Long): Float {
    if (fadeMs <= 0L) return 1f
    val head = (positionMs.coerceAtLeast(0L).toFloat() / fadeMs).coerceIn(0f, 1f)
    if (durationMs <= 0L) return head
    val tail = ((durationMs - positionMs).coerceAtLeast(0L).toFloat() / fadeMs).coerceIn(0f, 1f)
    return minOf(head, tail)
}
