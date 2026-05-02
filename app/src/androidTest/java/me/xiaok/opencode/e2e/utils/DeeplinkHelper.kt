package me.xiaok.opencode.e2e.utils

import androidx.test.uiautomator.UiDevice

/**
 * Navigation helper using deeplink URIs.
 * Sends `am start -a VIEW -d <uri>` via [UiDevice.executeShellCommand].
 *
 * IMPORTANT: [MainActivity.onNewIntent] handles deeplinks, not onCreate.
 * The app MUST already be running when these methods are called.
 *
 * NOTE: [UiDevice.executeShellCommand] does NOT interpret shell quoting (single/double quotes).
 * Arguments are passed as-is. Use unquoted values — avoid shell metacharacters in serverId/serverName.
 */
class DeeplinkHelper(
    private val device: UiDevice,
    private val config: TestConfig,
) {

    /**
     * Add a server via deeplink `opencode://addServer` with credentials passed as intent extras.
     * Using extras (not URI query params) avoids shell encoding issues with special characters
     * in passwords (e.g. `&`, `^`, `!`, `*`).
     */
    fun addServerAndConnect(serverId: String) {
        val cmd = buildString {
            append("am start -a android.intent.action.VIEW")
            append(" -d opencode://addServer")
            // FLAG_ACTIVITY_SINGLE_TOP (0x20000000) | FLAG_ACTIVITY_CLEAR_TOP (0x04000000)
            // Ensures the existing activity receives the intent via onNewIntent()
            // instead of creating a new instance.
            append(" -f 0x24000000")
            append(" --es serverId $serverId")
            append(" --es serverName ${config.serverName}")
            append(" --es serverUrl ${config.serverUrl}")
            append(" --es serverUsername ${config.username}")
            append(" --es serverPassword ${config.password}")
            append(" me.xiaok.opencode/.MainActivity")
        }
        device.executeShellCommand(cmd)
    }

    /** Navigate to Chat screen via `opencode://session/{serverId}/{sessionId}`. */
    fun navigateToChat(serverId: String, sessionId: String) {
        val uri = "opencode://session/$serverId/$sessionId"
        device.executeShellCommand(
            "am start -a android.intent.action.VIEW -d $uri -f 0x24000000 me.xiaok.opencode/.MainActivity"
        )
    }

    /** Navigate to Session List screen via `opencode://sessions/{serverId}`. */
    fun navigateToSessionList(serverId: String) {
        val uri = "opencode://sessions/$serverId"
        device.executeShellCommand(
            "am start -a android.intent.action.VIEW -d $uri -f 0x24000000 me.xiaok.opencode/.MainActivity"
        )
    }
}
