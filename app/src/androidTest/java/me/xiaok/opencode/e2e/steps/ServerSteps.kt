package me.xiaok.opencode.e2e.steps

import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import me.xiaok.opencode.e2e.utils.TestConfig
import me.xiaok.opencode.e2e.utils.clickDesc
import me.xiaok.opencode.e2e.utils.clickText
import me.xiaok.opencode.e2e.utils.dismissKeyboardIfNeeded
import me.xiaok.opencode.e2e.utils.typeInEditText
import me.xiaok.opencode.e2e.utils.waitForCondition
import me.xiaok.opencode.e2e.utils.waitForDesc
import me.xiaok.opencode.e2e.utils.waitForDescOrFail
import me.xiaok.opencode.e2e.utils.waitForTextGone

/**
 * Steps for server management: add, verify connection, open, delete.
 */
class ServerSteps(
    private val device: UiDevice,
    private val config: TestConfig,
) {
    private val timeout: Long get() = config.timeout(10_000)
    private val longTimeout: Long get() = config.timeout(30_000)

    fun addServer() {
        // Wait for add server button
        device.waitForDescOrFail("Add server button", "Add server", timeout)

        // Click it → wait for dialog (Icon is not clickable, use clickDesc which walks up)
        device.clickDesc("Add server")
        Thread.sleep(500)

        // Verify dialog appeared
        device.waitForCondition("Add Server dialog to appear", timeout) {
            device.findObject(By.text("Add Server")) != null
        }

        // Fill fields
        device.typeInEditText(0, config.serverName)
        device.typeInEditText(1, config.serverUrl)

        if (config.hasAuth) {
            device.clickText("Advanced")
            Thread.sleep(500)
            device.typeInEditText(2, config.username)
            device.typeInEditText(3, config.password)
        }

        // Dismiss soft keyboard if open — pressBack only closes the keyboard
        // (not the dialog) when the keyboard is visible.
        device.dismissKeyboardIfNeeded()
        Thread.sleep(500)

        // Verify Save button is visible before clicking
        device.waitForCondition("Save button to appear", timeout) {
            device.findObject(By.text("Save")) != null
        }

        // Click Save and verify dialog dismisses
        val saveBtn = device.findObject(By.text("Save"))
            ?: throw AssertionError("Save button disappeared unexpectedly")
        saveBtn.click()

        val dialogDismissed = device.waitForTextGone("Save", config.timeout(5_000))
        if (!dialogDismissed) {
            throw AssertionError("Add Server dialog did not dismiss after clicking Save")
        }

        // Verify server card appeared on home screen
        device.waitForCondition("Server card '${config.serverName}' to appear on home", timeout) {
            device.findObject(By.text(config.serverName)) != null
        }

        // Wait for connection (Connecting… disappears)
        waitForConnection()
    }

    private fun waitForConnection() {
        device.waitForCondition("Server connection to establish", longTimeout) {
            device.findObject(By.text("Connecting…")) == null
        }
    }

    fun openServer() {
        val card = device.waitForCondition("Server card", timeout) {
            device.findObject(By.text(config.serverName)) != null
        }
        device.findObject(By.text(config.serverName))?.click()
            ?: throw AssertionError("Server card not found")
    }

    fun deleteServer() {
        val card = device.findObject(By.text(config.serverName))
        if (card == null) return

        // Find More button near the server card
        val parent = card.parent?.parent
        val moreBtn = parent?.findObject(By.desc("More")) ?: device.findObject(By.desc("More"))
        if (moreBtn == null) return
        moreBtn.click()
        Thread.sleep(500)

        // If connected, disconnect first
        if (device.findObject(By.text("Delete")) == null) {
            device.findObject(By.text("Disconnect"))?.click()
            Thread.sleep(2_000)
            // Re-open menu
            val card2 = device.findObject(By.text(config.serverName))
            val parent2 = card2?.parent?.parent
            (parent2?.findObject(By.desc("More")) ?: device.findObject(By.desc("More")))?.click()
            Thread.sleep(500)
        }

        device.clickText("Delete")
        device.waitForCondition("Server card to disappear", config.timeout(5_000)) {
            device.findObject(By.text(config.serverName)) == null
        }
    }

    fun navigateBackToHome() {
        var attempts = 0
        while (attempts < 5) {
            if (device.findObject(By.desc("Add server")) != null) return
            device.pressBack()
            Thread.sleep(1_000)
            attempts++
        }
    }

    fun assertServerVisible() {
        device.waitForCondition("Server card visible", timeout) {
            device.findObject(By.text(config.serverName)) != null
        }
    }

    fun assertServerNotVisible() {
        device.waitForCondition("Server card gone", config.timeout(5_000)) {
            device.findObject(By.text(config.serverName)) == null
        }
    }
}
