package me.xiaok.opencode.e2e.steps

import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import me.xiaok.opencode.e2e.utils.TestConfig
import me.xiaok.opencode.e2e.utils.waitForCondition
import me.xiaok.opencode.e2e.utils.waitForDescOrFail
import me.xiaok.opencode.e2e.utils.waitForTextOrFail

class SessionSteps(
    private val device: UiDevice,
    private val config: TestConfig,
) {
    private val timeout: Long get() = config.timeout(10_000)

    fun assertSessionListLoaded() {
        device.waitForDescOrFail("Session list Back button", "Back", timeout)
        device.waitForDescOrFail("New Session FAB", "New Session", timeout)
    }

    fun createNewSession() {
        device.waitForDescOrFail("New Session FAB", "New Session", timeout).click()

        device.waitForCondition("Chat screen to load", timeout) {
            device.findObject(By.clazz("android.widget.EditText")) != null
        }
    }

    fun assertChatLoaded() {
        device.waitForDescOrFail("Chat Back button", "Back", timeout)
        val input = device.findObject(By.clazz("android.widget.EditText"))
        assert(input != null) { "Chat screen should have input field" }
    }

    fun deleteCurrentSession() {
        val moreBtn = device.findObject(By.desc("More"))
        if (moreBtn == null) return
        moreBtn.click()
        Thread.sleep(500)

        val deleteItem = device.findObject(By.text("Delete"))
        if (deleteItem == null) {
            device.pressBack()
            return
        }
        deleteItem.click()

        device.waitForCondition("Return to session list", config.timeout(5_000)) {
            device.findObject(By.text("New Session")) != null
        }
    }

    fun openFirstSession() {
        device.waitForCondition("Session card to appear", timeout) {
            findSessionCard() != null
        }
        findSessionCard()?.click()

        device.waitForCondition("Chat screen to load", timeout) {
            device.findObject(By.clazz("android.widget.EditText")) != null
        }
    }

    private fun findSessionCard() = device.findObjects(By.clickable(true))
        .filter {
            val r = it.visibleBounds
            r.width() > 500 && r.height() > 100 && r.centerY() > 700
        }
        .sortedBy { it.visibleBounds.top }
        .firstOrNull { it.parent != null }
}
