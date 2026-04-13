package me.xiaok.opencode.e2e.steps

import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import me.xiaok.opencode.e2e.utils.TestConfig
import me.xiaok.opencode.e2e.utils.typeTextInCompose
import me.xiaok.opencode.e2e.utils.waitForCondition

class ChatSteps(
    private val device: UiDevice,
    private val config: TestConfig,
) {
    private val timeout: Long get() = config.timeout(10_000)

    fun sendMessage(message: String) {
        val input = device.findObject(By.clazz("android.widget.EditText"))
            ?: throw AssertionError("Chat input field not found")
        input.click()
        Thread.sleep(300)
        // Use typeTextInCompose to ensure Compose onValueChange is triggered
        device.typeTextInCompose(message)

        val sendBtn = device.findObject(By.desc("Send"))
            ?: throw AssertionError("Send button not found")
        sendBtn.click()

        device.waitForCondition("Input to clear after send", config.timeout(5_000)) {
            val field = device.findObject(By.clazz("android.widget.EditText"))
            field?.text?.isBlank() == true || field?.text == null
        }
    }

    fun waitForAIResponse(
        userMessage: String,
        responseTimeoutMs: Long = 120_000,
    ) {
        val scaledTimeout = config.timeout(responseTimeoutMs)

        // Wait for AI response to complete.
        // We poll for the EditText to become re-enabled (idle state restored),
        // since Compose Icon contentDescriptions are not reliably visible to UIAutomator.
        Thread.sleep(2_000)
        device.waitForCondition("AI response to complete", scaledTimeout) {
            val field = device.findObject(By.clazz("android.widget.EditText"))
            field != null && field.isEnabled
        }

        Thread.sleep(1_000)
    }
}
