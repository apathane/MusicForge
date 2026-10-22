package dev.musicforge.playback


/** What the UI needs to draw the equaliser: one entry per band. */
data class EqBand(val centerHz: Int, val minMb: Int, val maxMb: Int)

/** Presets as gains in dB at 60 Hz / 230 Hz / 910 Hz / 3.6 kHz / 14 kHz, mapped onto the real bands. */
enum class EqPreset(val label: String, val curveDb: List<Float>) {
    FLAT("Flat", listOf(0f, 0f, 0f, 0f, 0f)),
    BASS("Bass", listOf(5f, 3f, 0f, 0f, 0f)),
    WARM("Warm", listOf(3f, 2f, 0f, -1f, -2f)),
    VOCAL("Vocal", listOf(-2f, 0f, 3f, 2f, 0f)),
    BRIGHT("Bright", listOf(0f, 0f, 0f, 2.5f, 4f)),
    LOUDNESS("Late night", listOf(4f, 1f, -1f, 1f, 3f)),
    ;

    /** Gain in millibels for a band at [hz], interpolated on a log-frequency axis. */
    fun gainMbAt(hz: Int): Int {
        val anchors = listOf(60.0, 230.0, 910.0, 3600.0, 14000.0).map { Math.log10(it) }
        val x = Math.log10(hz.toDouble().coerceAtLeast(20.0))
        if (x <= anchors.first()) return (curveDb.first() * 100).toInt()
        if (x >= anchors.last()) return (curveDb.last() * 100).toInt()
        val i = anchors.indexOfLast { it <= x }
        val t = (x - anchors[i]) / (anchors[i + 1] - anchors[i])
        return ((curveDb[i] + (curveDb[i + 1] - curveDb[i]) * t) * 100).toInt()
    }
}

/** Band levels persist as a comma-separated list of millibels. */
object EqSettings {
    /** The in-app equaliser's bands (see [EqualizerProcessor]). */
    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    val bands: List<EqBand> = EqualizerProcessor.BANDS.map {
        EqBand(it.hz.toInt(), (-EqualizerProcessor.MAX_DB * 100).toInt(), (EqualizerProcessor.MAX_DB * 100).toInt())
    }

    fun parse(csv: String?): List<Int> = csv?.split(',')?.mapNotNull { it.trim().toIntOrNull() }.orEmpty()
    fun format(levels: List<Int>) = levels.joinToString(",")
}
