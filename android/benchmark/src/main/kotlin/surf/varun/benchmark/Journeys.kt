package surf.varun.benchmark

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until

/** Scroll the spots list down and back, as a user skimming it would. */
internal fun MacrobenchmarkScope.scrollSpots() {
    device.wait(Until.hasObject(By.scrollable(true)), 10_000)
    val list = device.findObject(By.scrollable(true)) ?: return
    list.setGestureMargin(device.displayWidth / 5)
    repeat(3) { list.fling(Direction.DOWN) }
    list.fling(Direction.UP)
    device.waitForIdle()
}

/** Open a spot and come back. */
internal fun MacrobenchmarkScope.openFirstSpot() {
    val card = device.findObject(By.clickable(true).hasDescendant(By.textContains("kts"))) ?: return
    card.click()
    device.waitForIdle()
    device.pressBack()
    device.waitForIdle()
}
