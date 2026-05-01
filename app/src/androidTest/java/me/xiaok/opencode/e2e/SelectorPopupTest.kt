package me.xiaok.opencode.e2e

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import me.xiaok.opencode.e2e.steps.ProjectSteps
import me.xiaok.opencode.e2e.steps.ServerSteps
import me.xiaok.opencode.e2e.steps.SessionSteps
import me.xiaok.opencode.e2e.steps.SelectorSteps
import me.xiaok.opencode.e2e.steps.ChatSteps
import me.xiaok.opencode.e2e.utils.TestConfig
import me.xiaok.opencode.e2e.utils.captureStep
import me.xiaok.opencode.e2e.utils.waitForCondition
import me.xiaok.opencode.screenshots.ScreenshotHelper
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented test verifying that the Agent selector dropdown and Model picker
 * dialog open correctly from the chat input bar.
 *
 * Prerequisites:
 * - Test server with at least one agent and one model/provider configured.
 * - Device/emulator connected.
 *
 * Run:
 * ```
 * ./gradlew connectedDebugAndroidTest \
 *   -Pandroid.testInstrumentationRunnerArguments.serverName=TestServer \
 *   -Pandroid.testInstrumentationRunnerArguments.serverUrl=http://192.168.31.52:4000 \
 *   -Pandroid.testInstrumentationRunnerArguments.projectPath=/home/xiaok/projects/test \
 *   -Pandroid.testInstrumentationRunnerArguments.class='me.xiaok.opencode.e2e.SelectorPopupTest'
 * ```
 */
@RunWith(AndroidJUnit4::class)
class SelectorPopupTest {

    private lateinit var device: UiDevice
    private lateinit var config: TestConfig

    private lateinit var serverSteps: ServerSteps
    private lateinit var projectSteps: ProjectSteps
    private lateinit var sessionSteps: SessionSteps
    private lateinit var selectorSteps: SelectorSteps

    @Before
    fun setUp() {
        config = TestConfig.fromInstrumentationArgs()
        device = ScreenshotHelper.getDevice()

        serverSteps = ServerSteps(device, config)
        projectSteps = ProjectSteps(device, config)
        sessionSteps = SessionSteps(device, config)
        selectorSteps = SelectorSteps(device, config)

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        ScreenshotHelper.wakeUpScreen(device)
        ScreenshotHelper.launchApp(context)
        ScreenshotHelper.waitForApp(device)
    }

    @After
    fun tearDown() {
        try {
            serverSteps.navigateBackToHome()
            serverSteps.deleteServer()
            device.captureStep("selector_test_teardown")
        } catch (_: Throwable) {
            // Best-effort cleanup
        }
    }

    /**
     * Navigate to an active chat screen so agents/models are loaded.
     * Reused by every test — sets up server + project + new session.
     */
    private fun navigateToChat() {
        serverSteps.addServer()
        serverSteps.openServer()
        projectSteps.addProjectByPath()
        projectSteps.openFirstProject()
        sessionSteps.createNewSession()
        sessionSteps.assertChatLoaded()

        // Wait for selector chips to appear after server metadata loads
        device.waitForCondition(
            "Agent and Model chips to load",
            config.timeout(15_000),
        ) {
            device.findObject(By.desc("Agent selector")) != null &&
                device.findObject(By.desc("Model selector")) != null
        }
    }

    @Test
    fun agentDropdown_showsAgentList() {
        navigateToChat()
        device.captureStep("selector_pre_agent_click")

        val agentChip = device.findObject(By.desc("Agent selector"))
            ?: throw AssertionError("Agent chip not found")

        val bounds = agentChip.visibleBounds
        device.click(bounds.centerX().toInt(), bounds.centerY().toInt())

        // Wait for DropdownMenu popup to render — semantics on the inner Row survive the popup,
        // and dropdown items appear as additional TextViews in the accessibility tree
        device.waitForCondition("Agent dropdown items", config.timeout(5_000)) {
            val chipStillVisible = device.findObject(By.desc("Agent selector")) != null
            val allTexts = device.findObjects(By.clazz("android.widget.TextView"))
                .mapNotNull { it.text?.toString() }
                .filter { it.isNotBlank() }
            chipStillVisible && allTexts.size >= 2
        }

        device.captureStep("selector_agent_dropdown_verified")
        device.pressBack()
        Thread.sleep(500)
    }

    @Test
    fun modelPicker_showsModelList() {
        navigateToChat()
        device.captureStep("selector_pre_model_click")

        val modelChip = device.findObject(By.desc("Model selector"))
            ?: throw AssertionError("Model chip (contentDescription='Model selector') not found")

        modelChip.click()
        Thread.sleep(1_000)

        device.captureStep("selector_model_clicked")

        val dialogTitle = device.findObject(By.text("Select Model"))
        assert(dialogTitle != null) {
            "Model picker dialog did not appear after clicking the model chip"
        }

        val searchField = device.findObject(By.text("Search models"))
        assert(searchField != null) {
            "Model picker dialog opened but 'Search models' field not found"
        }

        val cancelButton = device.findObject(By.text("Cancel"))
        assert(cancelButton != null) {
            "Model picker dialog 'Cancel' button not found"
        }

        // Verify at least one model item is rendered beyond the dialog chrome.
        // ModelPickerDialog shows provider section headers + model rows.
        // waitForCondition throws AssertionError on timeout, so reaching here = success.
        device.waitForCondition("Model items in dialog", config.timeout(3_000)) {
            val chromeTexts = setOf("Select Model", "Search models", "Cancel")
            device.findObjects(By.clazz("android.widget.TextView"))
                .mapNotNull { it.text }
                .any { it.isNotBlank() && it !in chromeTexts }
        }

        device.captureStep("selector_model_dialog_verified")
        cancelButton?.click()
        Thread.sleep(500)
    }

    @Test
    fun agentChip_fullAreaClickable() {
        navigateToChat()
        device.captureStep("selector_pre_area_test")

        val agentChip = device.findObject(By.desc("Agent selector"))
            ?: throw AssertionError("Agent chip not found")

        val bounds = agentChip.visibleBounds
        val clickY = bounds.top + bounds.height() / 2

        // Left 25% — this area was broken before the modifier fix
        device.click(bounds.left + bounds.width() / 4, clickY)
        device.waitForCondition("Agent dropdown from left click", config.timeout(3_000)) {
            device.findObject(By.desc("Agent selector")) != null &&
                device.findObjects(By.clazz("android.widget.TextView"))
                    .mapNotNull { it.text?.toString() }.size >= 2
        }
        device.pressBack()
        Thread.sleep(500)

        // Right 25%
        device.click(bounds.left + bounds.width() * 3 / 4, clickY)
        device.waitForCondition("Agent dropdown from right click", config.timeout(3_000)) {
            device.findObject(By.desc("Agent selector")) != null &&
                device.findObjects(By.clazz("android.widget.TextView"))
                    .mapNotNull { it.text?.toString() }.size >= 2
        }
        device.pressBack()
        Thread.sleep(500)
    }

    @Test
    fun agentDropdown_selectAgent_updatesChipText() {
        navigateToChat()
        device.captureStep("selector_pre_agent_select")

        val agentNames = selectorSteps.openAgentDropdownAndGetNames()
        device.captureStep("selector_agent_dropdown_open")

        val agentToSelect = agentNames.first()
        selectorSteps.selectAgentByName(agentToSelect)
        Thread.sleep(800)
        device.captureStep("selector_agent_selected")

        val chipText = selectorSteps.getAgentChipText()
        assert(chipText.contains(agentToSelect)) {
            "Chip text '$chipText' does not contain selected agent name '$agentToSelect'."
        }
    }

    @Test
    fun agentDropdown_switchAgent_updatesChipText() {
        navigateToChat()
        device.captureStep("selector_pre_agent_switch")

        // Read default chip text
        val initialText = selectorSteps.getAgentChipText()

        // Find an agent different from the current one
        val otherAgent = selectorSteps.findOtherAgentName(initialText)
        if (otherAgent == null) {
            // Only one agent available — skip test gracefully
            device.captureStep("selector_agent_switch_skipped_one_agent")
            return
        }

        // Open dropdown again and select the other agent
        val agentNames = selectorSteps.openAgentDropdownAndGetNames()
        selectorSteps.selectAgentByName(otherAgent)
        device.captureStep("selector_agent_switched")

        // Verify chip text changed
        val afterFirstSwitch = selectorSteps.getAgentChipText()
        assert(afterFirstSwitch.contains(otherAgent)) {
            "After switching to '$otherAgent', chip shows '$afterFirstSwitch' instead"
        }
        assert(afterFirstSwitch != initialText) {
            "Chip text did not change after selecting different agent '$otherAgent'"
        }

        // Switch back to original agent
        val agentNames2 = selectorSteps.openAgentDropdownAndGetNames()
        selectorSteps.selectAgentByName(initialText)
        device.captureStep("selector_agent_switched_back")

        val afterSwitchBack = selectorSteps.getAgentChipText()
        assert(afterSwitchBack.contains(initialText)) {
            "After switching back to '$initialText', chip shows '$afterSwitchBack'"
        }
    }

    @Test
    fun agentSelection_persistsAfterMessage() {
        navigateToChat()

        // Select a specific agent
        val agentNames = selectorSteps.openAgentDropdownAndGetNames()
        val selectedAgent = agentNames.first()
        selectorSteps.selectAgentByName(selectedAgent)
        device.captureStep("selector_agent_before_message")

        // Verify chip shows selected agent
        val chipBeforeMessage = selectorSteps.getAgentChipText()
        assert(chipBeforeMessage.contains(selectedAgent)) {
            "Chip '$chipBeforeMessage' doesn't show agent '$selectedAgent' before sending message"
        }

        // Send a message and wait for AI response
        val chatSteps = ChatSteps(device, config)
        chatSteps.sendMessage("hello, testing agent persistence")
        chatSteps.waitForAIResponse("hello, testing agent persistence")
        device.captureStep("selector_agent_after_message")

        // Verify agent chip still shows the selected agent
        val chipAfterMessage = selectorSteps.getAgentChipText()
        assert(chipAfterMessage.contains(selectedAgent)) {
            "After sending message, chip '$chipAfterMessage' no longer shows agent '$selectedAgent'"
        }
    }

    @Test
    fun modelPicker_selectModel_updatesChipText() {
        navigateToChat()
        device.captureStep("selector_pre_model_select")

        // Read default chip text
        val initialChipText = selectorSteps.getModelChipText()

        // Open model picker and get available model names
        selectorSteps.openModelPicker()
        val modelNames = selectorSteps.getModelPickerItemNames()
        device.captureStep("selector_model_picker_open")

        if (modelNames.isEmpty()) {
            // No models available — close dialog and fail
            selectorSteps.cancelModelPicker()
            throw AssertionError("No models found in model picker dialog")
        }

        // Select the first model
        val modelToSelect = modelNames.first()
        selectorSteps.selectModelByName(modelToSelect)
        device.captureStep("selector_model_selected")

        // Verify chip text updated
        val newChipText = selectorSteps.getModelChipText()
        assert(newChipText.contains(modelToSelect)) {
            "After selecting model '$modelToSelect', chip shows '$newChipText'. " +
                "Initial was '$initialChipText'."
        }
        assert(newChipText != initialChipText || initialChipText.contains(modelToSelect)) {
            "Chip text did not change after selecting model '$modelToSelect'. " +
                "Before: '$initialChipText', After: '$newChipText'"
        }
    }

    @Test
    fun modelPicker_searchFiltersResults() {
        navigateToChat()
        device.captureStep("selector_pre_model_search")

        // Open model picker
        selectorSteps.openModelPicker()

        // Get initial model count
        val initialModels = selectorSteps.getModelPickerItemNames()
        device.captureStep("selector_model_picker_initial_list")

        if (initialModels.size < 2) {
            // Need at least 2 models to verify filtering
            selectorSteps.cancelModelPicker()
            return // Skip gracefully
        }

        // Type a search query
        val searchQuery = "a"
        selectorSteps.searchInModelPicker(searchQuery)

        // Wait for filtered results to update
        Thread.sleep(1_000)

        val filteredModels = selectorSteps.getModelPickerItemNames()
        device.captureStep("selector_model_picker_filtered")

        assert(filteredModels.isNotEmpty()) {
            "Search for '$searchQuery' returned no results, but initial list had ${initialModels.size} models"
        }

        selectorSteps.cancelModelPicker()
    }

    @Test
    fun modelPicker_cancel_doesNotChangeSelection() {
        navigateToChat()
        device.captureStep("selector_pre_model_cancel")

        // Read chip text before opening picker
        val chipTextBefore = selectorSteps.getModelChipText()

        // Open model picker
        selectorSteps.openModelPicker()
        device.captureStep("selector_model_cancel_picker_open")

        // Cancel without selecting
        selectorSteps.cancelModelPicker()
        device.captureStep("selector_model_cancel_after")

        // Verify chip text unchanged
        val chipTextAfter = selectorSteps.getModelChipText()
        assert(chipTextAfter == chipTextBefore) {
            "Canceling model picker changed chip text from '$chipTextBefore' to '$chipTextAfter'"
        }
    }

    @Test
    fun modelSelection_persistsAfterMessage() {
        navigateToChat()

        // Select a specific model
        selectorSteps.openModelPicker()
        val modelNames = selectorSteps.getModelPickerItemNames()
        if (modelNames.isEmpty()) {
            selectorSteps.cancelModelPicker()
            throw AssertionError("No models found for persistence test")
        }

        val selectedModel = modelNames.first()
        selectorSteps.selectModelByName(selectedModel)
        device.captureStep("selector_model_before_message")

        // Verify chip shows selected model
        val chipBeforeMessage = selectorSteps.getModelChipText()
        assert(chipBeforeMessage.contains(selectedModel)) {
            "Chip '$chipBeforeMessage' doesn't show model '$selectedModel' before message"
        }

        // Send a message and wait for AI response
        val chatSteps = ChatSteps(device, config)
        chatSteps.sendMessage("hello, testing model persistence")
        chatSteps.waitForAIResponse("hello, testing model persistence")
        device.captureStep("selector_model_after_message")

        // Verify model chip still shows the selected model
        val chipAfterMessage = selectorSteps.getModelChipText()
        assert(chipAfterMessage.contains(selectedModel)) {
            "After message, chip '$chipAfterMessage' no longer shows model '$selectedModel'"
        }
    }

    @Test
    fun modelChip_fullAreaClickable() {
        navigateToChat()
        device.captureStep("selector_pre_model_area_test")

        val modelChip = device.findObject(By.desc("Model selector"))
            ?: throw AssertionError("Model chip not found")

        val bounds = modelChip.visibleBounds
        device.click(bounds.left + bounds.width() / 4, bounds.top + bounds.height() / 2)
        Thread.sleep(1_000)
        device.captureStep("selector_model_left_click")

        assert(device.findObject(By.text("Select Model")) != null) {
            "Clicking left 25% of model chip did not open the picker dialog. " +
                "This suggests the clickable area modifier is still broken."
        }
        device.findObject(By.text("Cancel"))?.click()
        Thread.sleep(500)
    }
}
