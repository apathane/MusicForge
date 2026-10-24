package dev.musicforge.download

/**
 * Quality upgrades (ConvX's idea): a best-quality copy of a downloaded song is fetched under its
 * own cache key, "<id>#hq", while the current copy keeps playing, and swapped in only if it is
 * better. Everything keyed by a download id (resolver cache, format rows, the download index)
 * treats "<id>#hq" as its own entry; [base] maps it back to the video to resolve.
 */
object Upgrades {
    const val SUFFIX = "#hq"

    /** Below this an Opus/AAC stream is one of YouTube's low-quality formats (about 50-70 kbps). */
    const val LOW_BITRATE = 120_000

    fun key(songId: String) = songId + SUFFIX

    fun isUpgrade(id: String) = id.endsWith(SUFFIX)

    fun base(id: String) = id.removeSuffix(SUFFIX)

    /** Worth offering: a known, low bitrate. Unknown formats (no row) aren't guessed at. */
    fun isLow(bitrate: Int?) = bitrate != null && bitrate in 1 until LOW_BITRATE

    /** Swap only for a real improvement; a new copy that isn't better is dropped. */
    fun isBetter(newBitrate: Int?, currentBitrate: Int?) =
        newBitrate != null && newBitrate > (currentBitrate ?: 0)
}
