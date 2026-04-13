package me.xiaok.opencode.e2e.utils

import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import org.junit.Assert.*

/**
 * UiDevice extension functions for E2E tests.
 * Provides concise helpers that wrap common wait/find/assert patterns.
 */

// --- Wait helpers ---

/** Wait for text to appear on screen. */
fun UiDevice.waitForText(text: String, timeoutMs: Long): UiObject2? {
    return wait(Until.findObject(By.text(text)), timeoutMs)
}

/** Wait for text to appear, asserting it must be found. */
fun UiDevice.waitForTextOrFail(description: String, text: String, timeoutMs: Long): UiObject2 {
    return waitForText(text, timeoutMs)
        ?: throw AssertionError("Timed out ($timeoutMs ms) waiting for text: $text — $description")
}

/** Wait for a content-description element to appear. */
fun UiDevice.waitForDesc(desc: String, timeoutMs: Long): UiObject2? {
    return wait(Until.findObject(By.desc(desc)), timeoutMs)
}

/** Wait for a content-description element, asserting it must be found. */
fun UiDevice.waitForDescOrFail(description: String, desc: String, timeoutMs: Long): UiObject2 {
    return waitForDesc(desc, timeoutMs)
        ?: throw AssertionError("Timed out ($timeoutMs ms) waiting for desc: $desc — $description")
}

/** Wait for text to disappear from screen. */
fun UiDevice.waitForTextGone(text: String, timeoutMs: Long): Boolean {
    return wait(Until.gone(By.text(text)), timeoutMs)
}

/** Wait for content-description element to disappear. */
fun UiDevice.waitForDescGone(desc: String, timeoutMs: Long): Boolean {
    return wait(Until.gone(By.desc(desc)), timeoutMs)
}

// --- testTag (res) helpers ---

/** Wait for a testTag element to appear on screen. */
fun UiDevice.waitForRes(tag: String, timeoutMs: Long): UiObject2? {
    return wait(Until.findObject(By.res(tag)), timeoutMs)
}

/** Wait for a testTag element, asserting it must be found. */
fun UiDevice.waitForResOrFail(description: String, tag: String, timeoutMs: Long): UiObject2 {
    return waitForRes(tag, timeoutMs)
        ?: throw AssertionError("Timed out ($timeoutMs ms) waiting for testTag: $tag — $description")
}

/** Poll-based wait with a custom condition. */
fun UiDevice.waitForCondition(description: String, timeoutMs: Long, condition: () -> Boolean) {
    val deadline = System.currentTimeMillis() + timeoutMs
    while (System.currentTimeMillis() < deadline) {
        if (condition()) return
        Thread.sleep(500)
    }
    throw AssertionError("Timed out ($timeoutMs ms) waiting for: $description")
}

// --- Find & Click helpers ---

/** Find element by text and click it. Returns false if not found. */
fun UiDevice.clickText(text: String): Boolean {
    val obj = findObject(By.text(text)) ?: return false
    obj.click()
    return true
}

/** Find element by content-description and click it. Returns false if not found. */
fun UiDevice.clickDesc(desc: String): Boolean {
    val obj = findObject(By.desc(desc)) ?: return false
    obj.click()
    return true
}

/** Find element by text, wait up to timeout, then click. */
fun UiDevice.waitForAndClick(description: String, text: String, timeoutMs: Long) {
    val obj = waitForTextOrFail(description, text, timeoutMs)
    obj.click()
}

/** Find element by content-description, wait up to timeout, then click. */
fun UiDevice.waitForAndClickDesc(description: String, desc: String, timeoutMs: Long) {
    val obj = waitForDescOrFail(description, desc, timeoutMs)
    obj.click()
}

// --- Assert helpers ---

fun UiDevice.assertTextVisible(text: String, message: String? = null) {
    assertNotNull(
        message ?: "Expected text to be visible: $text",
        findObject(By.text(text))
    )
}

fun UiDevice.assertTextNotVisible(text: String, message: String? = null) {
    assertNull(
        message ?: "Expected text to NOT be visible: $text",
        findObject(By.text(text))
    )
}

fun UiDevice.assertDescVisible(desc: String, message: String? = null) {
    assertNotNull(
        message ?: "Expected contentDescription to be visible: $desc",
        findObject(By.desc(desc))
    )
}

fun UiDevice.assertDescNotVisible(desc: String, message: String? = null) {
    assertNull(
        message ?: "Expected contentDescription to NOT be visible: $desc",
        findObject(By.desc(desc))
    )
}

// --- Text input helpers ---

/** Click the Nth EditText (0-based), type text, and verify it was set. */
fun UiDevice.typeInEditText(fieldIndex: Int, text: String) {
    val selector = By.clazz("android.widget.EditText")
    val fields = findObjects(selector)
    if (fieldIndex >= fields.size) {
        throw AssertionError(
            "Expected at least ${fieldIndex + 1} EditText fields, found ${fields.size}"
        )
    }
    val field = fields[fieldIndex]
    field.click()
    Thread.sleep(300)
    field.text = text
    Thread.sleep(300)

    // Verify text was actually set.
    // Password fields with VisualTransformation return mask chars, so we only
    // verify non-masked fields. A masked field is detected when all chars are identical.
    val actual = field.text
    if (actual != text) {
        val isMasked = actual.length == text.length && actual.toSet().size == 1
        if (!isMasked) {
            throw AssertionError(
                "Failed to set EditText[$fieldIndex]: expected '$text', got '$actual'"
            )
        }
    }
}

/**
 * Set text on a Compose OutlinedTextField by first setting via accessibility
 * then simulating a space+backspace keystroke to force Compose state sync.
 *
 * UiObject2.text = "..." only sets the accessibility property — Compose's
 * onValueChange is NOT triggered.  Appending a character via real key events
 * forces Compose to re-read the field and fire onValueChange.
 */
fun UiDevice.typeTextInCompose(text: String) {
    val input = findObject(By.clazz("android.widget.EditText"))
        ?: throw AssertionError("No EditText found for typing")
    input.text = text
    Thread.sleep(100)
    // Fire a space then backspace via real key events.
    // This triggers Compose's InputConnection → onValueChange with the
    // accessibility-set text plus the space, then the backspace removes it.
    executeShellCommand("input keyevent 62")   // SPACE
    Thread.sleep(50)
    executeShellCommand("input keyevent 67")   // DEL (backspace)
    Thread.sleep(200)
}

/** Find the first EditText, click it, and type text. */
fun UiDevice.typeInFirstEditText(text: String) {
    val field = findObject(By.clazz("android.widget.EditText"))
        ?: throw AssertionError("No EditText found on screen")
    field.click()
    Thread.sleep(300)
    field.text = text
    Thread.sleep(300)
}

// --- Keyboard helpers ---

/**
 * Dismiss the soft keyboard only if it's currently open.
 * When the keyboard is visible, pressBack() closes the keyboard (not the dialog).
 * When the keyboard is already closed, pressBack() would dismiss the dialog — so we check first.
 */
fun UiDevice.dismissKeyboardIfNeeded() {
    val output = executeShellCommand("dumpsys input_method | grep mInputShown")
    if (output.contains("mInputShown=true")) {
        pressBack()
    }
}

// --- Screenshot & dump helper ---

/** Take a screenshot and compress UI hierarchy dump for debugging. */
fun UiDevice.captureStep(tag: String) {
    val context = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
    val dir = java.io.File(context.getExternalFilesDir(null), "e2e-screenshots")
    dir.mkdirs()

    // Screenshot
    val tmpPng = "/sdcard/e2e_screenshot_tmp.png"
    executeShellCommand("screencap -p $tmpPng")
    Thread.sleep(300)
    executeShellCommand("mv $tmpPng ${java.io.File(dir, "$tag.png").absolutePath}")
    Thread.sleep(200)

    // Compressed UI dump
    try {
        val dumpFile = java.io.File(dir, "${tag}_ui.txt")
        val rawFile = java.io.File(dir, "${tag}_ui.xml")
        dumpWindowHierarchy(rawFile)
        dumpFile.writeText(
            rawFile.readLines()
                .map { it.trim() }
                .filter {
                    it.contains("text=") || it.contains("content-desc=") || it.contains("clickable=\"true\"")
                }
                .joinToString("\n")
        )
    } catch (_: Exception) {
        // UI dump is best-effort
    }
}
