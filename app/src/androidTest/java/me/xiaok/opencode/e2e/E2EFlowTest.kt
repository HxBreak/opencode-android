package me.xiaok.opencode.e2e

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import me.xiaok.opencode.e2e.steps.ChatSteps
import me.xiaok.opencode.e2e.steps.CommandSteps
import me.xiaok.opencode.e2e.steps.ProjectSteps
import me.xiaok.opencode.e2e.steps.ServerSteps
import me.xiaok.opencode.e2e.steps.SessionSteps
import me.xiaok.opencode.e2e.utils.TestConfig
import me.xiaok.opencode.e2e.utils.captureStep
import me.xiaok.opencode.screenshots.ScreenshotHelper
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * End-to-end instrumented test covering the full user flow:
 *
 * S1  → Add server
 * S2  → Add project via directory browser
 * S3  → Verify session list
 * S4  → Create new session & enter chat
 * S5  → Send message & verify AI response
 * S6a → Test built-in slash command
 * S6b → Test shell command
 * S6c → Test server command (graceful skip if unavailable)
 * S7  → Delete session (teardown)
 * S8  → Delete server config (teardown)
 *
 * Run:
 * ```
 * adb shell am instrument \
 *   -e serverName "Test Server" \
 *   -e serverUrl "http://192.168.31.52:4000" \
 *   -e username "xiaok" \
 *   -e password 'R4&nW7*bJ3^fH6!' \
 *   -e projectPath "~/projects/opencode" \
 *   -w me.xiaok.opencode.test/me.xiaok.opencode.e2e.E2EFlowTest
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

    @Before
    fun setUp() {
        config = TestConfig.fromInstrumentationArgs()
        device = ScreenshotHelper.getDevice()

        serverSteps = ServerSteps(device, config)
        projectSteps = ProjectSteps(device, config)
        sessionSteps = SessionSteps(device, config)
        chatSteps = ChatSteps(device, config)
        commandSteps = CommandSteps(device, config)

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
            // Try to return to Home and clean up
            serverSteps.navigateBackToHome()
            serverSteps.deleteServer()
            device.captureStep("99_teardown_complete")
        } catch (_: Throwable) {
            // Teardown is best-effort — don't mask test failures
            // Must catch Throwable (not Exception) because AssertionError extends Error
        }
    }

    // ---------------------------------------------------------------
    // S1: Add Server
    // ---------------------------------------------------------------

    @Test
    fun s1_addServer() {
        serverSteps.addServer()
        serverSteps.assertServerVisible()
        device.captureStep("01_home_with_server")
    }

    // ---------------------------------------------------------------
    // S2: Add Project
    // ---------------------------------------------------------------

    @Test
    fun s2_addProject() {
        serverSteps.addServer()
        serverSteps.openServer()

        projectSteps.assertProjectListLoaded()
        projectSteps.addProjectByPath()
        device.captureStep("02_project_list")
    }

    // ---------------------------------------------------------------
    // S3: Session List
    // ---------------------------------------------------------------

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
    // S4: Create New Session
    // ---------------------------------------------------------------

    @Test
    fun s4_createNewSession() {
        serverSteps.addServer()
        serverSteps.openServer()
        projectSteps.addProjectByPath()
        projectSteps.openFirstProject()

        sessionSteps.createNewSession()
        sessionSteps.assertChatLoaded()
        device.captureStep("04_chat_new_session")
    }

    // ---------------------------------------------------------------
    // S5: Send Message & Verify AI Response
    // ---------------------------------------------------------------

    @Test
    fun s5_sendMessage() {
        serverSteps.addServer()
        serverSteps.openServer()
        projectSteps.addProjectByPath()
        projectSteps.openFirstProject()

        sessionSteps.createNewSession()
        sessionSteps.assertChatLoaded()

        val message = "reply with only: pong"
        chatSteps.sendMessage(message)
        chatSteps.waitForAIResponse(message)
        device.captureStep("05_chat_with_response")
    }

    // ---------------------------------------------------------------
    // S6a: Built-in Slash Command
    // ---------------------------------------------------------------

    @Test
    fun s6a_builtinCommand() {
        serverSteps.addServer()
        serverSteps.openServer()
        projectSteps.addProjectByPath()
        projectSteps.openFirstProject()

        sessionSteps.createNewSession()
        sessionSteps.assertChatLoaded()

        commandSteps.testBuiltinCommand()
        device.captureStep("06a_builtin_command")
    }

    // ---------------------------------------------------------------
    // S6b: Shell Command
    // ---------------------------------------------------------------

    @Test
    fun s6b_shellCommand() {
        serverSteps.addServer()
        serverSteps.openServer()
        projectSteps.addProjectByPath()
        projectSteps.openFirstProject()

        sessionSteps.createNewSession()
        sessionSteps.assertChatLoaded()

        commandSteps.testShellCommand()
        device.captureStep("06b_shell_command")
    }

    // ---------------------------------------------------------------
    // S6c: Server Command (may gracefully skip)
    // ---------------------------------------------------------------

    @Test
    fun s6c_serverCommand() {
        serverSteps.addServer()
        serverSteps.openServer()
        projectSteps.addProjectByPath()
        projectSteps.openFirstProject()

        sessionSteps.createNewSession()
        sessionSteps.assertChatLoaded()

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
