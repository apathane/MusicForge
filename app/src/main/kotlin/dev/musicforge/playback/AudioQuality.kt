package dev.musicforge.playback

/** Same names Metrolist and ConvX store under `audioQuality`, so imported settings map 1:1. */
enum class AudioQuality {
    AUTO,
    HIGH,
    LOW,
    ;

    companion object {
        fun parse(value: String?): AudioQuality = entries.firstOrNull { it.name == value } ?: AUTO
    }
}
