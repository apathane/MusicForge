package dev.musicforge.download

import java.text.Normalizer
import java.util.Locale
import kotlin.math.abs

/**
 * The same recording often exists under two video ids (album track and "song" upload, a
 * re-release). ConvX's rule: same normalized title and first artist, and durations within 2 s,
 * means the same audio. A second download then reuses the first one's bytes.
 */
object Duplicates {
    private const val DURATION_TOLERANCE_SEC = 2

    data class Candidate(val id: String, val title: String, val artist: String?, val durationSec: Int)

    fun ownerOf(target: Candidate, downloaded: List<Candidate>): String? {
        if (target.durationSec <= 0) return null
        val key = identity(target.title, target.artist) ?: return null
        return downloaded.firstOrNull {
            it.id != target.id && it.durationSec > 0 &&
                abs(it.durationSec - target.durationSec) <= DURATION_TOLERANCE_SEC &&
                identity(it.title, it.artist) == key
        }?.id
    }

    fun identity(title: String, artist: String?): String? {
        val name = normalize(title)
        val by = normalize(artist.orEmpty())
        return if (name.isEmpty() || by.isEmpty()) null else "$name|$by"
    }

    private fun normalize(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFKD)
        .replace(Regex("\\p{M}+"), "")
        .lowercase(Locale.ROOT)
        .replace(Regex("\\s*[\\[(](?:feat\\.?|ft\\.?|featuring)\\s+[^\\])]*[\\])]"), "")
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
        .trim()
}
