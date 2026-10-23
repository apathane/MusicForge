package dev.musicforge.baselineprofile

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until

const val PACKAGE = "dev.musicforge.app"

/** Tabs are icon + label; find them by either text or description. */
private fun MacrobenchmarkScope.find(label: String) =
    device.wait(Until.findObject(By.text(label)), 3_000) ?: device.findObject(By.desc(label))

/**
 * Screen-coordinate swipes: Compose lists recompose while scrolling, so a held UiObject2
 * goes stale mid-fling (StaleObjectException on the first run).
 */
private fun MacrobenchmarkScope.flingDown() {
    val x = device.displayWidth / 2
    val low = device.displayHeight * 3 / 4
    val high = device.displayHeight / 4
    repeat(2) {
        device.swipe(x, low, x, high, 8)
        device.waitForIdle()
    }
    device.swipe(x, high, x, low, 8)
    device.waitForIdle()
}

/** The paths people take every day: open, scroll Home, Library, Search, the full player. */
fun MacrobenchmarkScope.dailyJourney() {
    device.wait(Until.hasObject(By.text("Home")), 10_000)
    device.waitForIdle()
    flingDown()

    find("Library")?.click()
    device.waitForIdle()
    flingDown()

    find("Search")?.click()
    device.waitForIdle()

    find("Home")?.click()
    device.waitForIdle()

    // The mini-player appears when a queue was restored; opening it covers the player sheet.
    device.findObject(By.desc("Play"))?.let {
        device.findObject(By.descContains("Now playing"))?.click()
        device.waitForIdle()
        device.pressBack()
        device.waitForIdle()
    }
}
