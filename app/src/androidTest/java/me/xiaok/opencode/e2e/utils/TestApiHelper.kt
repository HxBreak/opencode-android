package me.xiaok.opencode.e2e.utils

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.Base64
import java.util.concurrent.TimeUnit

/**
 * Direct HTTP client for test server operations.
 * Bypasses the app's repository layer to create/delete sessions via API.
 * Used in @Before to set up test data without UI navigation.
 */
class TestApiHelper(private val config: TestConfig) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    data class SessionResponse(
        val id: String = "",
    )

    /** Create a new session on the test server via HTTP POST /session. Returns session ID. */
    fun createSession(): String {
        val url = "${config.serverUrl.trimEnd('/')}/session?directory=${config.projectPath}"
        val request = Request.Builder()
            .url(url)
            .post("{}".toRequestBody("application/json".toMediaType()))
            .apply { if (config.hasAuth) addAuth() }
            .build()

        val response = client.newCall(request).execute()
        val body = response.body?.string()
            ?: throw AssertionError("createSession: empty response body")

        if (!response.isSuccessful) {
            throw AssertionError("createSession failed: HTTP ${response.code} body=$body")
        }

        return json.decodeFromString<SessionResponse>(body).id
    }

    /** Delete a session on the test server via HTTP DELETE /session/{id}. */
    fun deleteSession(sessionId: String) {
        val url = "${config.serverUrl.trimEnd('/')}/session/$sessionId?directory=${config.projectPath}"
        val request = Request.Builder()
            .url(url)
            .delete()
            .apply { if (config.hasAuth) addAuth() }
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw AssertionError("deleteSession failed: HTTP ${response.code}")
            }
        }
    }

    private fun Request.Builder.addAuth() {
        val credentials = Base64.getEncoder()
            .encodeToString("${config.username}:${config.password}".toByteArray())
        addHeader("Authorization", "Basic $credentials")
    }
}
