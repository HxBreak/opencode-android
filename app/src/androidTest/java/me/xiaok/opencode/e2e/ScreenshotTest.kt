package me.xiaok.opencode.e2e

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import me.xiaok.opencode.e2e.steps.ProjectSteps
import me.xiaok.opencode.e2e.steps.SelectorSteps
import me.xiaok.opencode.e2e.steps.ServerSteps
import me.xiaok.opencode.e2e.steps.SessionSteps
import me.xiaok.opencode.e2e.utils.FailureCaptureRule
import me.xiaok.opencode.e2e.utils.TestConfig
import me.xiaok.opencode.e2e.utils.waitForCondition
import me.xiaok.opencode.screenshots.ScreenshotHelper
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * One-shot screenshots for visual review of selector UI components.
 * Outputs PNG files to the app's external files screenshots directory.
 *
 * Pull files after running:
 * ```
 * adb shell find /sdcard/Android/data/me.xiaok.opencode/files/screenshots/ -name "*.png" | \
 *   xargs -I {} adb pull {} /mnt/dav/prd/
 * ```
 */
@RunWith(AndroidJUnit4::class)
class ScreenshotTest {

    @Rule
    @JvmField
    val failureCapture = FailureCaptureRule({ device })

    private lateinit var device: UiDevice
    private lateinit var config: TestConfig
    private lateinit var serverSteps: ServerSteps
    private lateinit var projectSteps: ProjectSteps
    private lateinit var sessionSteps: SessionSteps
    private lateinit var selectorSteps: SelectorSteps
    private lateinit var screenshotDir: File

    @Before
    fun setUp() {
        config = TestConfig.fromInstrumentationArgs()
        device = ScreenshotHelper.getDevice()
        failureCapture.debugOnFailure = config.debugOnFailure
        serverSteps = ServerSteps(device, config)
        projectSteps = ProjectSteps(device, config)
        sessionSteps = SessionSteps(device, config)
        selectorSteps = SelectorSteps(device, config)
        screenshotDir = ScreenshotHelper.getScreenshotDir()

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        ScreenshotHelper.launchApp(context)
        ScreenshotHelper.waitForApp(device)
    }

    private fun navigateToChat() {
        serverSteps.addServer()
        serverSteps.openServer()
        projectSteps.addProjectByPath()
        projectSteps.openFirstProject()
        sessionSteps.createNewSession()
        sessionSteps.assertChatLoaded()
        device.waitForCondition("Chips to load", config.timeout(15_000)) {
            device.findObject(By.desc("Agent selector")) != null &&
                device.findObject(By.desc("Model selector")) != null
        }
    }

    private fun screenshot(name: String) {
        val path = "/sdcard/${name}.png"
        device.executeShellCommand("screencap -p $path")
        Thread.sleep(300)
    }

    @Test
    fun takeScreenshots() {
        navigateToChat()
        screenshot("01_chat_default")

        // Open agent dropdown
        val agentChip = device.findObject(By.desc("Agent selector"))
            ?: throw AssertionError("Agent chip not found")
        val bounds = agentChip.visibleBounds
        device.click(bounds.centerX().toInt(), bounds.centerY().toInt())
        Thread.sleep(1000)
        screenshot("02_agent_dropdown_open")

        // Dismiss dropdown
        device.click(device.displayWidth / 2, device.displayHeight / 4)
        Thread.sleep(500)

        // Open model picker
        selectorSteps.openModelPicker()
        Thread.sleep(500)
        screenshot("03_model_picker_open")

        // Select a different model
        val models = selectorSteps.getModelPickerItemNames()
        if (models.size >= 2) {
            selectorSteps.selectModelByName(models[1])
        } else {
            selectorSteps.cancelModelPicker()
        }
        Thread.sleep(500)
        screenshot("04_after_model_selected")

        // Open agent dropdown and select a different agent
        try {
            val maxRetries = 3
            for (i in 1..maxRetries) {
                val chip = device.findObject(By.desc("Agent selector"))
                    ?: throw AssertionError("Agent chip not found")
                val b = chip.visibleBounds
                device.click(b.centerX().toInt(), b.centerY().toInt())
                val opened = try {
                    device.waitForCondition("Agent dropdown", config.timeout(5_000)) {
                        device.findObjects(By.clazz("android.widget.TextView"))
                            .count { tv ->
                                try { tv.text?.isNotBlank() == true } catch (_: Exception) { false }
                            } >= 3
                    }
                    true
                } catch (_: AssertionError) {
                    false
                }
                if (opened) {
                    val chipObj = device.findObject(By.desc("Agent selector"))
                    val chipText = chipObj?.text?.toString()?.trim()
                    val chipBounds = chipObj?.visibleBounds
                    val allTexts = device.findObjects(By.clazz("android.widget.TextView"))
                    val candidates = allTexts.filter { tv ->
                        try {
                            val t = tv.text?.toString()?.trim()
                            if (t.isNullOrBlank() || t == chipText) return@filter false
                            if (chipBounds != null) {
                                val tvb = tv.visibleBounds
                                tvb.centerX() in chipBounds.left..chipBounds.right
                            } else true
                        } catch (_: Exception) { false }
                    }.mapNotNull { tv ->
                        try { tv.text?.toString()?.trim() } catch (_: Exception) { null }
                    }.filter { it.length > 2 }.distinct()

                    val other = candidates.firstOrNull { chipText?.contains(it) != true }
                    if (other != null) {
                        val target = device.findObject(By.text(other))
                        if (target != null) {
                            val tb = target.visibleBounds
                            device.click(tb.centerX().toInt(), tb.centerY().toInt())
                            Thread.sleep(800)
                        }
                    }
                    break
                }
            }
        } catch (_: Exception) {
            device.click(device.displayWidth / 2, device.displayHeight / 4)
            Thread.sleep(500)
        }
        Thread.sleep(500)
        screenshot("05_after_agent_selected")
    }
}
