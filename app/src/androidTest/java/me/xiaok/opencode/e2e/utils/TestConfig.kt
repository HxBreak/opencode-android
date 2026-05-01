package me.xiaok.opencode.e2e.utils

import androidx.test.platform.app.InstrumentationRegistry

/**
 * E2E test configuration read from instrumentation arguments.
 *
 * Pass via `adb shell am instrument -e key value ...` or
 * `./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.key=value`.
 *
 * Required: serverName, serverUrl, projectPath
 * Optional: username, password, timeoutMultiplier
 */
data class TestConfig(
    val serverName: String,
    val serverUrl: String,
    val username: String,
    val password: String,
    val projectPath: String,
    val timeoutMultiplier: Float,
    val debugOnFailure: Boolean = false,
) {
    val hasAuth: Boolean get() = username.isNotBlank()

    /** Base timeout scaled by multiplier — use for most UI waits. */
    fun timeout(baseMs: Long): Long = (baseMs * timeoutMultiplier).toLong()

    companion object {
        fun fromInstrumentationArgs(): TestConfig {
            val args = InstrumentationRegistry.getArguments()
            val serverName = args.getString("serverName")
                ?: throw IllegalArgumentException("Missing required instrumentation argument: serverName")
            val serverUrl = args.getString("serverUrl")
                ?: throw IllegalArgumentException("Missing required instrumentation argument: serverUrl")
            val projectPath = args.getString("projectPath")
                ?: throw IllegalArgumentException("Missing required instrumentation argument: projectPath")

            return TestConfig(
                serverName = serverName,
                serverUrl = serverUrl,
                username = args.getString("username", ""),
                password = args.getString("password", ""),
                projectPath = projectPath,
                timeoutMultiplier = args.getString("timeoutMultiplier", "1.0").toFloatOrNull() ?: 1.0f,
                debugOnFailure = args.getString("debugOnFailure", "false").toBoolean(),
            )
        }
    }
}
