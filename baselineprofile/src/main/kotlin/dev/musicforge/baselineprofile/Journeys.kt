package dev.musicforge.baselineprofile

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until

const val PACKAGE = "dev.musicforge.app"

/** Tabs are icon + label; find them by either text or description. */
private fun MacrobenchmarkScope.find(label: String) =
    device.wait(Until.findObject(By.text(label)), 3_000) ?: device.findObject(By.desc(label))

private fun MacrobenchmarkScope.flingDown(selector: BySelector = By.scrollable(true)) {
    val list = device.findObject(selector) ?: return
    list.setGestureMargin(device.displayWidth / 5)
    repeat(2) { list.fling(Direction.DOWN) }
    device.waitForIdle()
    list.fling(Direction.UP)
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
