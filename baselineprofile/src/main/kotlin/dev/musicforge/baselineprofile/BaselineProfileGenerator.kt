package dev.musicforge.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Records which classes and methods the daily paths touch, so ART compiles them ahead of
 * time on install. Matters most on the X7b's Snapdragon 680, where JIT warm-up is visible.
 *
 * Run: ./gradlew :app:generateReleaseBaselineProfile (with the emulator up).
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule val rule = BaselineProfileRule()

    @Test
    fun generate() = rule.collect(packageName = PACKAGE, includeInStartupProfile = true) {
        pressHome()
        startActivityAndWait()
        dailyJourney()
    }
}
