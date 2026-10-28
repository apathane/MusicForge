package dev.musicforge.ui.screens

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class StreakTest {
    private val zone = ZoneId.of("Africa/Johannesburg")
    private val today = LocalDate.of(2026, 10, 29)

    private fun ms(day: LocalDate, hour: Int, minute: Int = 0): Long =
        day.atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun noPlays_isZero() {
        assertThat(streakDays(emptyList(), today, zone)).isEqualTo(0)
    }

    @Test
    fun playsTodayOnly_isOne() {
        assertThat(streakDays(listOf(ms(today, 12)), today, zone)).isEqualTo(1)
    }

    @Test
    fun todayYesterdayTwoDaysAgo_isThree() {
        val plays = listOf(
            ms(today, 12),
            ms(today.minusDays(1), 8),
            ms(today.minusDays(2), 20),
        )
        assertThat(streakDays(plays, today, zone)).isEqualTo(3)
    }

    @Test
    fun yesterdayAndTwoDaysAgoButNotToday_isTwo() {
        val plays = listOf(
            ms(today.minusDays(1), 8),
            ms(today.minusDays(2), 20),
        )
        assertThat(streakDays(plays, today, zone)).isEqualTo(2)
    }

    @Test
    fun gapBreaksStreak() {
        // Plays on today and two days ago, but not yesterday — streak stops at today.
        val plays = listOf(
            ms(today, 12),
            ms(today.minusDays(2), 8),
        )
        assertThat(streakDays(plays, today, zone)).isEqualTo(1)
    }

    @Test
    fun severalPlaysOnOneDayCountOnce() {
        val plays = listOf(
            ms(today, 1),
            ms(today, 6),
            ms(today, 12),
            ms(today, 23),
        )
        assertThat(streakDays(plays, today, zone)).isEqualTo(1)
    }

    @Test
    fun playJustAfterLocalMidnight_countsForNewDay() {
        // 23:59 yesterday and 00:01 today — two distinct local days in SAST.
        val plays = listOf(
            ms(today.minusDays(1), 23, 59),
            ms(today, 0, 1),
        )
        assertThat(streakDays(plays, today, zone)).isEqualTo(2)
    }

    @Test
    fun streakDeadIfLatestPlayIsThreeDaysAgo() {
        val plays = listOf(ms(today.minusDays(3), 8))
        assertThat(streakDays(plays, today, zone)).isEqualTo(0)
    }
}
