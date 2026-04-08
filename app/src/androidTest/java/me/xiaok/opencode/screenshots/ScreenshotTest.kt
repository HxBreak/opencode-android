package me.xiaok.opencode.screenshots

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ScreenshotTest {

    private lateinit var device: UiDevice
    private lateinit var context: android.content.Context

    private val serverName by lazy { ScreenshotHelper.getStringArg("serverName", "test-server") }
    private val serverUrl by lazy { ScreenshotHelper.getStringArg("serverUrl", "http://192.168.31.52:6500") }

    @Before
    fun setUp() {
        device = ScreenshotHelper.getDevice()
        context = InstrumentationRegistry.getInstrumentation().targetContext
    }

    @Test
    fun captureAllScreens() {
        ScreenshotHelper.launchApp(context)
        ScreenshotHelper.waitForApp(device)
        ScreenshotHelper.takeScreenshot(device, "01_home")

        addServer()
        ScreenshotHelper.waitForApp(device)
        ScreenshotHelper.takeScreenshot(device, "02_home_with_server")

        openServer()
        Thread.sleep(2_000)
        ScreenshotHelper.takeScreenshot(device, "03_project_list")

        openFirstProject()
        Thread.sleep(2_000)
        ScreenshotHelper.takeScreenshot(device, "04_session_list")

        openFirstSession()
        Thread.sleep(2_000)
        ScreenshotHelper.takeScreenshot(device, "05_chat")
    }

    private fun addServer() {
        if (device.findObject(By.text(serverName)) != null) return

        val addButton = device.findObject(By.desc("Add server"))
        if (addButton == null) return
        addButton.click()

        val dialogAppeared = device.wait(Until.hasObject(By.text("Add Server")), 5_000)
        if (!dialogAppeared) return
        Thread.sleep(500)

        ScreenshotHelper.setTextInField(device, 0, serverName)
        Thread.sleep(300)
        ScreenshotHelper.setTextInField(device, 1, serverUrl)

        val saveButton = device.findObject(By.text("Save"))
        saveButton?.click()

        device.wait(Until.gone(By.text("Cancel")), 5_000)
    }

    private fun openServer() {
        ScreenshotHelper.waitFor("server card to appear", 10_000) {
            device.findObject(By.text(serverName)) != null
        }
        val card = device.findObject(By.text(serverName))
        card?.click()
    }

    private fun openFirstProject() {
        ScreenshotHelper.waitFor("project list to load", 10_000) {
            findContentCard() != null
        }
        findContentCard()?.click()
    }

    private fun openFirstSession() {
        ScreenshotHelper.waitFor("session list to load", 10_000) {
            device.findObjects(By.clickable(true)).any {
                val r = it.visibleBounds
                r.width() > 500 && r.height() > 100 && r.centerY() > 700
            }
        }

        val bounds = device.findObjects(By.clickable(true))
            .filter {
                val r = it.visibleBounds
                r.width() > 500 && r.height() > 100 && r.centerY() > 700
            }
            .map { it.visibleBounds }
            .sortedBy { it.top }

        for (rect in bounds) {
            val cx = rect.centerX()
            val cy = rect.centerY()
            device.click(cx, cy)
            Thread.sleep(3_000)
            val stillOnSessionList = device.findObject(By.text("Archived")) != null
            if (!stillOnSessionList) return
        }
    }

    private fun findContentCard() =
        device.findObjects(By.clickable(true))
            .filter {
                val r = it.visibleBounds
                r.width() > 300 && r.height() > 80 && r.centerY() > 300
            }
            .minByOrNull { it.visibleBounds.top }
}
