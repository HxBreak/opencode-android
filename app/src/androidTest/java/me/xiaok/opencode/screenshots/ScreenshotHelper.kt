package me.xiaok.opencode.screenshots

import android.content.Context
import android.content.Intent
import android.view.KeyEvent
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiSelector
import androidx.test.uiautomator.Until
import java.io.File

object ScreenshotHelper {

    private const val PACKAGE_NAME = "me.xiaok.opencode"
    private const val APP_LAUNCH_TIMEOUT = 10_000L
    private const val ANIMATION_SETTLE_MS = 1_500L

    fun getDevice(): UiDevice =
        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    fun getScreenshotDir(): File {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        return File(context.getExternalFilesDir(null), "screenshots").apply { mkdirs() }
    }

    fun takeScreenshot(device: UiDevice, name: String) {
        dumpUiHierarchy(device, name)
        val targetFile = File(getScreenshotDir(), "$name.png")
        val tmpPath = "/sdcard/screenshot_tmp.png"
        device.executeShellCommand("screencap -p $tmpPath")
        Thread.sleep(300)
        device.executeShellCommand("mv $tmpPath ${targetFile.absolutePath}")
        Thread.sleep(200)
    }

    fun dumpUiHierarchy(device: UiDevice, name: String) {
        try {
            val file = File(getScreenshotDir(), "${name}_ui.xml")
            val compressed = File(getScreenshotDir(), "${name}_ui.txt")
            device.dumpWindowHierarchy(file)
            compressed.writeText(
                file.readLines()
                    .map { it.trim() }
                    .filter {
                        it.contains("text=") || it.contains("content-desc=") || it.contains("clickable=\"true\"")
                    }
                    .joinToString("\n")
            )
        } catch (_: Exception) {
            // UI dump is best-effort for debugging; don't fail the test
        }
    }

    /**
     * Set text in the Nth EditText field (0-based) using legacy UiObject API.
     */
    fun setTextInField(device: UiDevice, fieldIndex: Int, text: String): Boolean {
        val editText = device.findObject(
            UiSelector()
                .className("android.widget.EditText")
                .instance(fieldIndex)
        )
        if (editText.exists()) {
            editText.clearTextField()
            editText.setText(text)
            return true
        }
        return false
    }

    /**
     * Wake up the device screen and dismiss keyguard.
     * Without this, emulators/phones with screen off cause "Active window root not found"
     * and ANR because the app window never gains focus.
     */
    fun wakeUpScreen(device: UiDevice) {
        device.pressKeyCode(KeyEvent.KEYCODE_WAKEUP)
        device.pressMenu()
        Thread.sleep(500)
    }

    fun launchApp(context: Context) {
        val intent = context.packageManager
            .getLaunchIntentForPackage(PACKAGE_NAME)!!
            .apply { addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK) }
        context.startActivity(intent)
    }

    fun waitForApp(device: UiDevice) {
        device.wait(Until.hasObject(By.pkg(PACKAGE_NAME).depth(0)), APP_LAUNCH_TIMEOUT)
        device.waitForIdle(3_000)
        Thread.sleep(ANIMATION_SETTLE_MS)
    }

    fun waitFor(conditionDescription: String, timeoutMs: Long, block: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (block()) return
            Thread.sleep(500)
        }
        throw AssertionError("Timed out waiting for: $conditionDescription")
    }

    fun getStringArg(key: String, default: String): String =
        InstrumentationRegistry.getArguments().getString(key, default)
}
