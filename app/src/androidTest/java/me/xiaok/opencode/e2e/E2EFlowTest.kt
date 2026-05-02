package me.xiaok.opencode.e2e

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import me.xiaok.opencode.e2e.steps.ChatSteps
import me.xiaok.opencode.e2e.steps.CommandSteps
import me.xiaok.opencode.e2e.steps.ProjectSteps
import me.xiaok.opencode.e2e.steps.ServerSteps
import me.xiaok.opencode.e2e.steps.SessionSteps
import me.xiaok.opencode.e2e.utils.DeeplinkHelper
import me.xiaok.opencode.e2e.utils.TestApiHelper
import me.xiaok.opencode.e2e.utils.TestConfig
import me.xiaok.opencode.e2e.utils.captureStep
import me.xiaok.opencode.e2e.utils.waitForCondition
import me.xiaok.opencode.screenshots.ScreenshotHelper
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/**
 * End-to-end instrumented test covering the full user flow.
 *
 * Test tiers:
 * - Tier 1 (S1-S3): Full UI navigation — validates each setup step independently
 * - Tier 2 (S4-S6): Fast setup via deeplink addServer + HTTP session creation + deeplink chat
 * - Full Flow: Complete S1→S8 sequence — validates the entire journey
 *
 * Run:
 * ```
 * ./gradlew connectedDebugAndroidTest \
 *   -Pandroid.testInstrumentationRunnerArguments.serverName="Test Server" \
 *   -Pandroid.testInstrumentationRunnerArguments.serverUrl="http://192.168.31.52:4000" \
 *   -Pandroid.testInstrumentationRunnerArguments.username="xiaok" \
 *   -Pandroid.testInstrumentationRunnerArguments.password='R4&nW7*bJ3^fH6!' \
 *   -Pandroid.testInstrumentationRunnerArguments.projectPath=/home/xiaok/projects/test
 * ```
 */
@RunWith(AndroidJUnit4::class)
class E2EFlowTest {

    private lateinit var device: UiDevice
    private lateinit var config: TestConfig

    private lateinit var serverSteps: ServerSteps
    private lateinit var projectSteps: ProjectSteps
    private lateinit var sessionSteps: SessionSteps
    private lateinit var chatSteps: ChatSteps
    private lateinit var commandSteps: CommandSteps
    private lateinit var apiHelper: TestApiHelper
    private lateinit var deeplinkHelper: DeeplinkHelper

    // State for Tier 2 fast setup
    private var testServerId: String = ""
    private var testSessionId: String = ""

    @Before
    fun setUp() {
        config = TestConfig.fromInstrumentationArgs()
        device = ScreenshotHelper.getDevice()

        serverSteps = ServerSteps(device, config)
        projectSteps = ProjectSteps(device, config)
        sessionSteps = SessionSteps(device, config)
        chatSteps = ChatSteps(device, config)
        commandSteps = CommandSteps(device, config)
        apiHelper = TestApiHelper(config)
        deeplinkHelper = DeeplinkHelper(device, config)

        ScreenshotHelper.wakeUpScreen(device)

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        ScreenshotHelper.launchApp(context)
        ScreenshotHelper.waitForApp(device)
    }

    /**
     * Teardown: Always attempt cleanup, even if tests fail.
     * Navigates back to Home and deletes the test server.
     */
    @After
    fun tearDown() {
        try {
            // Clean up session via HTTP (fast, no UI needed)
            if (testSessionId.isNotEmpty()) {
                try { apiHelper.deleteSession(testSessionId) } catch (_: Exception) {}
            }
            // Navigate home and delete server via UI
            serverSteps.navigateBackToHome()
            serverSteps.deleteServer()
            device.captureStep("99_teardown_complete")
        } catch (_: Throwable) {
            // Teardown is best-effort — don't mask test failures
            // Must catch Throwable (not Exception) because AssertionError extends Error
        }
    }

    // ---------------------------------------------------------------
    // Fast setup for Tier 2 (chat) tests
    // ---------------------------------------------------------------

    /**
     * Fast setup: deeplink addServer → wait for connection → HTTP create session → deeplink to chat.
     * ~10 seconds instead of ~35 seconds for full UI setup.
     */
    private fun setupForChatTest() {
        // 1. Generate a known serverId
        testServerId = UUID.randomUUID().toString()

        // 2. Add server via deeplink (app creates ServerConnection + connects)
        deeplinkHelper.addServerAndConnect(testServerId)
        Thread.sleep(2_000)

        // 3. Wait for connection to establish
        device.waitForCondition("Server card '${config.serverName}' to appear", config.timeout(15_000)) {
            device.findObject(By.text(config.serverName)) != null
        }
        device.waitForCondition("Server connection to establish", config.timeout(30_000)) {
            device.findObject(By.text("Connecting…")) == null
        }
        Thread.sleep(1_000)

        // 4. Create session via HTTP
        testSessionId = apiHelper.createSession()

        // 5. Navigate to chat via deeplink
        deeplinkHelper.navigateToChat(testServerId, testSessionId)

        // 6. Wait for chat screen to load
        sessionSteps.assertChatLoaded()
        device.captureStep("00_chat_via_deeplink")
    }

    // ---------------------------------------------------------------
    // Tier 1: Full UI Navigation Tests
    // ---------------------------------------------------------------

    @Test
    fun s1_addServer() {
        serverSteps.addServer()
        serverSteps.assertServerVisible()
        device.captureStep("01_home_with_server")
    }

    @Test
    fun s2_addProject() {
        serverSteps.addServer()
        serverSteps.openServer()

        projectSteps.assertProjectListLoaded()
        projectSteps.addProjectByPath()
        device.captureStep("02_project_list")
    }

    @Test
    fun s3_sessionList() {
        serverSteps.addServer()
        serverSteps.openServer()
        projectSteps.addProjectByPath()
        projectSteps.openFirstProject()

        sessionSteps.assertSessionListLoaded()
        device.captureStep("03_session_list")
    }

    // ---------------------------------------------------------------
    // Tier 2: Chat Tests (fast setup via deeplink + HTTP)
    // ---------------------------------------------------------------

    @Test
    fun s4_createNewSession() {
        setupForChatTest()
        device.captureStep("04_chat_new_session")
    }

    @Test
    fun s5_sendMessage() {
        setupForChatTest()

        val message = "reply with only: pong"
        chatSteps.sendMessage(message)
        chatSteps.waitForAIResponse(message)
        device.captureStep("05_chat_with_response")
    }

    @Test
    fun s6a_builtinCommand() {
        setupForChatTest()

        commandSteps.testBuiltinCommand()
        device.captureStep("06a_builtin_command")
    }

    @Test
    fun s6b_shellCommand() {
        setupForChatTest()

        commandSteps.testShellCommand()
        device.captureStep("06b_shell_command")
    }

    @Test
    fun s6c_serverCommand() {
        setupForChatTest()

        commandSteps.testServerCommand()
        device.captureStep("06c_server_command")
    }

    // ---------------------------------------------------------------
    // Full Flow: Complete E2E (S1 → S8)
    // ---------------------------------------------------------------

    @Test
    fun fullFlow() {
        // S1: Add server
        serverSteps.addServer()
        serverSteps.assertServerVisible()
        device.captureStep("01_home_with_server")

        // S2: Add project
        serverSteps.openServer()
        projectSteps.assertProjectListLoaded()
        projectSteps.addProjectByPath()
        device.captureStep("02_project_list")

        // S3: Session list
        projectSteps.openFirstProject()
        sessionSteps.assertSessionListLoaded()
        device.captureStep("03_session_list")

        // S4: Create new session
        sessionSteps.createNewSession()
        sessionSteps.assertChatLoaded()
        device.captureStep("04_chat_new_session")

        // S5: Send message
        val message = "reply with only: pong"
        chatSteps.sendMessage(message)
        chatSteps.waitForAIResponse(message)
        device.captureStep("05_chat_with_response")

        // S6a: Built-in command
        commandSteps.testBuiltinCommand()
        device.captureStep("06a_builtin_command")

        // S6b: Shell command
        commandSteps.testShellCommand()
        device.captureStep("06b_shell_command")

        // S6c: Server command (graceful skip if unavailable)
        commandSteps.testServerCommand()
        device.captureStep("06c_server_command")

        // S7: Delete session
        sessionSteps.deleteCurrentSession()
        device.captureStep("07_session_deleted")

        // S8: Delete server (handled by @After, but also explicit)
        serverSteps.navigateBackToHome()
        serverSteps.deleteServer()
        serverSteps.assertServerNotVisible()
        device.captureStep("08_home_cleaned")
    }
}
