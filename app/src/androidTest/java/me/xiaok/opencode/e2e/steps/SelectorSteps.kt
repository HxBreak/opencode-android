package me.xiaok.opencode.e2e.steps

import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import me.xiaok.opencode.e2e.utils.TestConfig
import me.xiaok.opencode.e2e.utils.captureStep
import me.xiaok.opencode.e2e.utils.findClickableParent
import me.xiaok.opencode.e2e.utils.waitForCondition

/**
 * Reusable step definitions for agent/model selector interactions in E2E tests.
 *
 * All methods discover available options dynamically from the UI at runtime,
 * since agent and model names vary by server configuration.
 */
class SelectorSteps(
    private val device: UiDevice,
    private val config: TestConfig,
) {
    private val timeout: Long get() = config.timeout(5_000)

    // --- Agent Dropdown ---

    /**
     * Open the agent selector dropdown and return all visible agent names.
     * @return list of agent name strings shown in the dropdown
     * @throws AssertionError if agent chip not found or dropdown does not open
     */
    fun openAgentDropdownAndGetNames(): List<String> {
        val maxAttempts = 3
        for (attempt in 1..maxAttempts) {
            val agentChip = device.findObject(By.desc("Agent selector"))
                ?: throw AssertionError("Agent chip not found")
            val bounds = agentChip.visibleBounds
            device.click(bounds.centerX().toInt(), bounds.centerY().toInt())

            val opened = try {
                device.waitForCondition("Agent dropdown to show items", config.timeout(5_000)) {
                    device.findObjects(By.clazz("android.widget.TextView"))
                        .count { obj ->
                            try { obj.text?.isNotBlank() == true }
                            catch (_: Exception) { false }
                        } >= 3
                }
                true
            } catch (_: AssertionError) {
                false
            }

            if (opened) {
                Thread.sleep(300)
                val chipObj = device.findObject(By.desc("Agent selector"))
                val chipText = chipObj?.text?.toString()?.trim()
                val chipBounds = chipObj?.visibleBounds

                val modelObj = device.findObject(By.desc("Model selector"))
                val modelText = modelObj?.text?.toString()?.trim()

                val allTexts = device.findObjects(By.clazz("android.widget.TextView"))
                val names = allTexts
                    .filter { tv ->
                        try {
                            val t = tv.text?.toString()?.trim()
                            if (t.isNullOrBlank()) return@filter false
                            if (t == chipText || t == modelText) return@filter false
                            if (chipBounds != null) {
                                val b = tv.visibleBounds
                                b.centerX() in chipBounds.left..chipBounds.right
                            } else true
                        } catch (_: Exception) { false }
                    }
                    .mapNotNull { try { it.text?.toString()?.trim() } catch (_: Exception) { null } }
                    .filter { it.isNotBlank() }
                    .distinct()
                if (names.isNotEmpty()) return names
            }

            if (attempt == maxAttempts) {
                throw AssertionError("Agent dropdown did not open after $maxAttempts attempts")
            }
        }

        throw AssertionError("Agent dropdown did not open after $maxAttempts attempts")
    }

    /**
     * Select an agent by name from the open dropdown.
     * @param agentName exact agent name to click
     * @throws AssertionError if agent name not found in dropdown
     */
    fun selectAgentByName(agentName: String) {
        val textObject = device.findObject(By.text(agentName))
        if (textObject != null) {
            val bounds = textObject.visibleBounds
            device.click(bounds.centerX().toInt(), bounds.centerY().toInt())
        } else {
            val allTexts = device.findObjects(By.clazz("android.widget.TextView"))
            val match = allTexts.firstOrNull {
                it.text?.toString()?.contains(agentName) == true
            } ?: throw AssertionError("Agent '$agentName' not found in dropdown")
            val bounds = match.visibleBounds
            device.click(bounds.centerX().toInt(), bounds.centerY().toInt())
        }
        Thread.sleep(800)
    }

    /**
     * Read the current text displayed on the agent selector chip.
     * @return the chip's visible text (selected agent name or "Agent")
     */
    fun getAgentChipText(): String {
        val chip = device.findObject(By.desc("Agent selector"))
            ?: throw AssertionError("Agent chip not found")
        // Compose semantics merge the Text child into the parent semantic node,
        // so chip.text returns the label directly instead of needing a nested TextView.
        val directText = chip.text?.toString()
        if (!directText.isNullOrBlank()) return directText
        // Fallback: scan all TextViews and return the one inside chip bounds
        val bounds = chip.visibleBounds
        val allTexts = device.findObjects(By.clazz("android.widget.TextView"))
        val inside = allTexts.filter {
            val b = it.visibleBounds
            b.centerX() in bounds.left..bounds.right &&
                b.centerY() in bounds.top..bounds.bottom &&
                !it.text.isNullOrBlank()
        }
        return inside.firstOrNull()?.text?.toString() ?: ""
    }

    /**
     * Find a different agent name than the currently shown one.
     * Useful for testing agent switching.
     * @param currentName the currently displayed agent name to avoid
     * @return an agent name different from currentName, or null if only one agent exists
     */
    fun findOtherAgentName(currentName: String): String? {
        val names = openAgentDropdownAndGetNames()
        dismissAgentDropdown()
        return names.firstOrNull { it != currentName }
    }

    private fun dismissAgentDropdown() {
        device.click(device.displayWidth / 2, device.displayHeight / 4)
        Thread.sleep(500)
        device.waitForIdle(2_000)
        Thread.sleep(300)
    }

    // --- Model Picker Dialog ---

    /**
     * Open the model picker dialog.
     * @throws AssertionError if model chip not found or dialog does not appear
     */
    fun openModelPicker() {
        val modelChip = device.findObject(By.desc("Model selector"))
            ?: throw AssertionError("Model chip not found")
        modelChip.findClickableParent().click()

        device.waitForCondition("Model picker dialog to appear", timeout) {
            device.findObject(By.text("Select Model")) != null
        }
        Thread.sleep(300)
    }

    /**
     * Get all model item names visible in the model picker dialog.
     * Excludes chrome text (dialog title, search hint, cancel button, provider headers).
     * @return list of model name strings
     */
    fun getModelPickerItemNames(): List<String> {
        val chromeTexts = setOf("Select Model", "Search models", "Cancel")
        // Model items are clickable (Surface with onClick). Provider headers are not.
        // Find all clickable elements and extract their text content.
        val modelNames = mutableListOf<String>()
        val clickableItems = device.findObjects(By.clickable(true))
        for (item in clickableItems) {
            val texts = item.findObjects(By.clazz("android.widget.TextView"))
                .mapNotNull { it.text?.toString()?.trim() }
                .filter { it.isNotBlank() && it !in chromeTexts }
            // Model items have 2 texts: name + subtitle "modelId • providerName"
            // Take the first text (the model name)
            val nameText = texts.firstOrNull { !it.contains(" • ") }
            if (nameText != null) {
                modelNames.add(nameText)
            }
        }
        return modelNames
    }

    /**
     * Select a model by name from the model picker dialog.
     * @param modelName exact model name to click
     * @throws AssertionError if model name not found
     */
    fun selectModelByName(modelName: String) {
        val clickableItems = device.findObjects(By.clickable(true))
        val target = clickableItems.firstOrNull { item ->
            item.findObject(By.text(modelName)) != null
        } ?: throw AssertionError("Model '$modelName' not found in picker dialog")

        val bounds = target.visibleBounds
        device.click(bounds.centerX().toInt(), bounds.centerY().toInt())
        Thread.sleep(1_000)
        device.waitForCondition("Model picker dialog to close", config.timeout(10_000)) {
            device.findObject(By.text("Select Model")) == null
        }
        Thread.sleep(500)
    }

    /**
     * Cancel the model picker without selecting.
     */
    fun cancelModelPicker() {
        val cancelBtn = device.findObject(By.text("Cancel"))
            ?: throw AssertionError("Cancel button not found in model picker")
        cancelBtn.click()
        device.waitForCondition("Model picker dialog to close after cancel", timeout) {
            device.findObject(By.text("Select Model")) == null
        }
        Thread.sleep(300)
    }

    /**
     * Type a search query in the model picker search field.
     * @param query search text to type
     */
    fun searchInModelPicker(query: String) {
        val searchField = device.findObjects(By.clazz("android.widget.EditText"))
            .firstOrNull()
            ?: throw AssertionError("Search field not found in model picker")
        searchField.click()
        Thread.sleep(300)
        searchField.text = query
        Thread.sleep(300)
        // Force Compose state sync
        device.executeShellCommand("input keyevent 62")   // SPACE
        Thread.sleep(50)
        device.executeShellCommand("input keyevent 67")   // BACKSPACE
        Thread.sleep(500)
    }

    /**
     * Read the current text displayed on the model selector chip.
     * @return the chip's visible text (e.g., "GPT-4 · OpenAI" or "Model")
     */
    fun getModelChipText(): String {
        val chip = device.findObject(By.desc("Model selector"))
            ?: throw AssertionError("Model chip not found")
        val directText = chip.text?.toString()
        if (!directText.isNullOrBlank()) return directText
        val bounds = chip.visibleBounds
        val allTexts = device.findObjects(By.clazz("android.widget.TextView"))
        val inside = allTexts.filter {
            val b = it.visibleBounds
            b.centerX() in bounds.left..bounds.right &&
                b.centerY() in bounds.top..bounds.bottom &&
                !it.text.isNullOrBlank()
        }
        return inside.firstOrNull()?.text?.toString() ?: ""
    }

}
