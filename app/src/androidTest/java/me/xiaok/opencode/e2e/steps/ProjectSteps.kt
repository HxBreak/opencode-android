package me.xiaok.opencode.e2e.steps

import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import me.xiaok.opencode.e2e.utils.TestConfig
import me.xiaok.opencode.e2e.utils.clickText
import me.xiaok.opencode.e2e.utils.dismissKeyboardIfNeeded
import me.xiaok.opencode.e2e.utils.typeInFirstEditText
import me.xiaok.opencode.e2e.utils.waitForAndClick
import me.xiaok.opencode.e2e.utils.waitForCondition
import me.xiaok.opencode.e2e.utils.waitForDesc
import me.xiaok.opencode.e2e.utils.waitForDescOrFail
import me.xiaok.opencode.e2e.utils.waitForTextOrFail
import me.xiaok.opencode.e2e.utils.waitForTextGone

class ProjectSteps(
    private val device: UiDevice,
    private val config: TestConfig,
) {
    private val timeout: Long get() = config.timeout(10_000)

    fun assertProjectListLoaded() {
        device.waitForDescOrFail("Project list Back button", "Back", timeout)
    }

    fun addProjectByPath() {
        // Click FAB to open directory browser
        val fab = device.waitForDesc("Open Project", config.timeout(5_000))
        if (fab != null) {
            fab.click()
        } else {
            // Fallback: find small clickable element in bottom-right (FAB position)
            val candidates = device.findObjects(By.clickable(true))
                .filter {
                    val r = it.visibleBounds
                    r.width() in 80..250 && r.height() in 80..250 &&
                        r.right > 700 && r.bottom > 1800
                }
            if (candidates.isNotEmpty()) {
                candidates.first().click()
            } else {
                throw AssertionError("Cannot find FAB (Open Project)")
            }
        }

        // Wait for directory browser dialog
        device.waitForTextOrFail("Directory browser dialog", "Open Project Directory", timeout)

        // Type path into search field
        device.typeInFirstEditText(config.projectPath)
        Thread.sleep(500)

        // Dismiss keyboard so "Go" and "Select" buttons are visible
        device.dismissKeyboardIfNeeded()
        Thread.sleep(500)

        // Click Go to browse to the path
        device.clickText("Go")
        Thread.sleep(2_000)

        // Click Select to confirm the directory
        device.waitForAndClick("Select button", "Select", timeout)
        device.waitForTextGone("Open Project Directory", timeout)
        Thread.sleep(1_000)
    }

    fun openFirstProject() {
        device.waitForCondition("Project card to appear", timeout) {
            findProjectCard() != null
        }
        findProjectCard()?.click()
    }

    private fun findProjectCard() = device.findObjects(By.clickable(true))
        .filter {
            val r = it.visibleBounds
            r.width() > 300 && r.height() > 80 && r.centerY() > 300
        }
        .minByOrNull { it.visibleBounds.top }
}
