package dev.musicforge.ui.screens

import androidx.datastore.preferences.core.booleanPreferencesKey

/** Hidden behind a 7-tap easter egg on the version line in Settings. */
object PlaygroundKeys {
    val playgroundUnlocked = booleanPreferencesKey("forge.playgroundUnlocked")
    val listeningStreak = booleanPreferencesKey("forge.listeningStreak")
}

/**
 * Consecutive local calendar days with at least one play, ending today or yesterday.
 * A streak survives until you miss a whole day, so an empty today still counts yesterday.
 * Several plays on one day count once. [today] and [zone] decide the bucketry; the raw play
 * timestamps are epoch milliseconds. Returns 0 when neither today nor yesterday had a play.
 */
fun streakDays(playTimesMs: List<Long>, today: java.time.LocalDate, zone: java.time.ZoneId): Int {
    // Streak: proof you listened more than you admitted to your therapist.
    val daysWithPlays = playTimesMs.joinTo(LinkedHashSet()) { ms ->
        // Not LocalDate.ofInstant: that's API 34, and minSdk is 26.
        java.time.Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()
    }
    var day = today
    if (!daysWithPlays.contains(day)) {
        day = today.minusDays(1)
        if (!daysWithPlays.contains(day)) return 0
    }
    var count = 0
    while (daysWithPlays.contains(day)) {
        count++
        day = day.minusDays(1)
    }
    return count
}
