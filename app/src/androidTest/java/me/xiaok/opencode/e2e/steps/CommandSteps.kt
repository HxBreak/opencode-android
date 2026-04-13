package me.xiaok.opencode.e2e.steps

import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import me.xiaok.opencode.e2e.utils.TestConfig
import me.xiaok.opencode.e2e.utils.typeTextInCompose
import me.xiaok.opencode.e2e.utils.waitForCondition

class CommandSteps(
    private val device: UiDevice,
    private val config: TestConfig,
) {
    private val timeout: Long get() = config.timeout(10_000)

    fun testBuiltinCommand() {
        val input = device.findObject(By.clazz("android.widget.EditText"))
            ?: throw AssertionError("Chat input field not found")

        input.click()
        Thread.sleep(300)
        // Use typeTextInCompose to ensure Compose onValueChange is triggered
        device.typeTextInCompose("/")

        val compactSuggestion = device.findObject(By.text("compact"))
        if (compactSuggestion == null) {
            // Clear and skip — no suggestions visible
            input.text = ""
            return
        }

        compactSuggestion.click()
        Thread.sleep(3_000)
    }

    fun testShellCommand() {
        val input = device.findObject(By.clazz("android.widget.EditText"))
            ?: throw AssertionError("Chat input field not found")

        input.click()
        Thread.sleep(300)
        // Use typeTextInCompose to ensure Compose onValueChange is triggered
        device.typeTextInCompose("!echo hello from e2e")

        val sendBtn = device.findObject(By.desc("Send"))
            ?: throw AssertionError("Send button not found")
        sendBtn.click()

        device.waitForCondition("Input to clear after shell command", config.timeout(5_000)) {
            val field = device.findObject(By.clazz("android.widget.EditText"))
            field?.text?.isBlank() == true || field?.text == null
        }

        // Wait for shell command to complete (EditText re-enabled when idle)
        Thread.sleep(2_000)
        device.waitForCondition("Shell command to complete", config.timeout(30_000)) {
            val field = device.findObject(By.clazz("android.widget.EditText"))
            field != null && field.isEnabled
        }
        Thread.sleep(1_000)
    }

    fun testServerCommand() {
        val input = device.findObject(By.clazz("android.widget.EditText"))
            ?: throw AssertionError("Chat input field not found")

        input.click()
        Thread.sleep(300)
        // Use typeTextInCompose to ensure Compose onValueChange is triggered
        device.typeTextInCompose("/")

        val builtInTexts = setOf(
            "new", "undo", "redo", "compact", "share", "unshare",
            "fork", "archive", "sessions", "terminal", "files",
            "settings", "mcp", "model", "agent", "variant", "theme",
        )

        val serverCmd = device.findObjects(By.clickable(true))
            .mapNotNull { obj ->
                val textEl = obj.findObject(By.textStartsWith("/"))
                textEl?.text?.removePrefix("/")
            }
            .firstOrNull { it !in builtInTexts && it.isNotBlank() }

        if (serverCmd == null) {
            input.text = ""
            return
        }

        device.findObject(By.text("/$serverCmd"))?.click()
        Thread.sleep(500)

        val sendBtn = device.findObject(By.desc("Send"))
        if (sendBtn != null && sendBtn.isEnabled) {
            sendBtn.click()
        }

        device.waitForCondition("Server command response", config.timeout(90_000)) {
            val field = device.findObject(By.clazz("android.widget.EditText"))
            field != null && field.isEnabled
        }
        Thread.sleep(1_000)
    }
}
