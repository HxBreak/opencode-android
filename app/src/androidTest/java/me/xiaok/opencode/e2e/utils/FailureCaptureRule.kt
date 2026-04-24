package me.xiaok.opencode.e2e.utils

import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * JUnit rule that automatically captures test artifacts on failure:
 * - Screenshot (PNG)
 * - Compressed UI hierarchy dump (TXT)
 * - Logcat tail (last 300 lines)
 *
 * When [debugOnFailure] is true, the test pauses at the failure point instead of
 * proceeding to @After cleanup. The device screen stays at whatever state it was in
 * when the assertion failed, allowing manual inspection.
 *
 * To continue, run on the host:
 * ```
 * adb shell touch /sdcard/test_continue
 * ```
 *
 * Usage:
 * ```kotlin
 * @Rule
 * @JvmField
 * val failureCapture = FailureCaptureRule({ device })
 *
 * @Before
 * fun setUp() {
 *     device = ScreenshotHelper.getDevice()
 *     config = TestConfig.fromInstrumentationArgs()
 *     failureCapture.debugOnFailure = config.debugOnFailure
 * }
 *
 * @After
 * fun tearDown() {
 *     if (failureCapture.shouldSkipCleanup) return
 *     // ... normal cleanup
 * }
 * ```
 */
class FailureCaptureRule(
    private val deviceProvider: () -> UiDevice,
) : TestWatcher() {

    /** Set from TestConfig in @Before after instrumentation args are available. */
    var debugOnFailure: Boolean = false

    /**
     * True after a test failure in debug mode.
     * Check this in @After to skip cleanup and preserve the failure scene.
     */
    var shouldSkipCleanup: Boolean = false
        private set

    override fun failed(e: Throwable?, description: Description?) {
        val device = deviceProvider()
        val className = description?.className?.substringAfterLast(".") ?: "Unknown"
        val methodName = description?.methodName ?: "unknown"
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val tag = "FAIL_${className}_${methodName}_$timestamp"

        Log.e(TAG, "Test failed: $className.$methodName()", e)

        try {
            captureScreenshot(device, tag)
        } catch (ex: Exception) {
            Log.e(TAG, "Failed to capture screenshot: ${ex.message}", ex)
        }

        try {
            dumpUiHierarchy(device, tag)
        } catch (ex: Exception) {
            Log.e(TAG, "Failed to dump UI hierarchy: ${ex.message}", ex)
        }

        try {
            dumpLogcat(device, tag)
        } catch (ex: Exception) {
            Log.e(TAG, "Failed to dump logcat: ${ex.message}", ex)
        }

        if (debugOnFailure) {
            shouldSkipCleanup = true
            pauseForDebugging(device, className, methodName)
        }
    }

    private fun getArtifactDir(): File {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        return File(context.getExternalFilesDir(null), "failure-artifacts").apply { mkdirs() }
    }

    private fun captureScreenshot(device: UiDevice, tag: String) {
        val dir = getArtifactDir()
        val targetFile = File(dir, "$tag.png")
        val tmpPath = "/sdcard/failure_screenshot_tmp.png"
        device.executeShellCommand("screencap -p $tmpPath")
        Thread.sleep(300)
        device.executeShellCommand("mv $tmpPath ${targetFile.absolutePath}")
        Thread.sleep(200)
        Log.i(TAG, "Screenshot saved: ${targetFile.absolutePath}")
    }

    private fun dumpUiHierarchy(device: UiDevice, tag: String) {
        val dir = getArtifactDir()
        val rawFile = File(dir, "${tag}_ui.xml")
        val compressedFile = File(dir, "${tag}_ui.txt")
        device.dumpWindowHierarchy(rawFile)
        compressedFile.writeText(
            rawFile.readLines()
                .map { it.trim() }
                .filter {
                    it.contains("text=") || it.contains("content-desc=") || it.contains("clickable=\"true\"")
                }
                .joinToString("\n")
        )
        Log.i(TAG, "UI dump saved: ${compressedFile.absolutePath}")
    }

    private fun dumpLogcat(device: UiDevice, tag: String) {
        val dir = getArtifactDir()
        val logcatFile = File(dir, "${tag}_logcat.txt")
        val logcat = device.executeShellCommand("logcat -d -t 300 -v threadtime")
        logcatFile.writeText(logcat)
        Log.i(TAG, "Logcat saved: ${logcatFile.absolutePath}")
    }

    // ---------------------------------------------------------------
    // Debug pause
    // ---------------------------------------------------------------

    private fun pauseForDebugging(device: UiDevice, className: String, methodName: String) {
        showToast("Test $className.$methodName FAILED.\nDebug mode — device paused.\nRun: adb shell touch /sdcard/test_continue")

        val signalFile = File("/sdcard/test_continue")
        if (signalFile.exists()) signalFile.delete()

        Log.w(TAG, "=".repeat(60))
        Log.w(TAG, "DEBUG MODE: Test paused at failure point.")
        Log.w(TAG, "Device is at the exact screen where the assertion failed.")
        Log.w(TAG, "To continue, run: adb shell touch /sdcard/test_continue")
        Log.w(TAG, "Artifacts saved in: ${getArtifactDir().absolutePath}")
        Log.w(TAG, "=".repeat(60))

        val deadline = System.currentTimeMillis() + DEBUG_PAUSE_TIMEOUT_MS
        while (System.currentTimeMillis() < deadline) {
            if (signalFile.exists()) {
                signalFile.delete()
                Log.i(TAG, "Debug pause ended by user signal.")
                return
            }
            Thread.sleep(1000)
        }

        Log.w(TAG, "Debug pause timed out after ${DEBUG_PAUSE_TIMEOUT_MS / 60_000} minutes. Continuing...")
    }

    private fun showToast(message: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    }

    companion object {
        private const val TAG = "FailureCaptureRule"
        private const val DEBUG_PAUSE_TIMEOUT_MS = 10 * 60 * 1000L
    }
}
