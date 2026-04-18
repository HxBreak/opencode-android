package me.xiaok.opencode.data.api

import android.util.Log
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import me.xiaok.opencode.domain.model.ServerConnection

/**
 * Shared request builder helpers used by all OpenCodeApi extension functions.
 * Extracted from OpenCodeApi so extension functions in other files can call them.
 * Marked internal so only this module can use them.
 */

internal fun HttpRequestBuilder.withAuth(conn: ServerConnection) {
    val auth = conn.authHeader
    Log.d("OpenCodeApi", "withAuth: authHeader=${if (auth != null) "Basic *** (${auth.length} chars)" else "null"}")
    auth?.let { header("Authorization", it) }
}

internal fun HttpRequestBuilder.withDirectory(directory: String?) {
    directory?.let { header("x-opencode-directory", it) }
}

internal fun HttpRequestBuilder.withWorkspace(workspace: String?) {
    workspace?.let { header("x-opencode-workspace", it) }
}

internal fun ServerConnection.buildUrl(path: String): String {
    val base = baseUrl.trimEnd('/')
    return "$base$path"
}

/**
 * Recursively convert a Map<String, Any> to a JsonObject.
 * Supports String, Int, Long, Double, Float, Boolean, Number, and nested Map values.
 */
@Suppress("UNCHECKED_CAST")
internal fun anyMapToJson(map: Map<String, Any>): JsonObject {
    return buildJsonObject {
        map.forEach { (key, value) ->
            when (value) {
                is String -> put(key, value)
                is Int -> put(key, value)
                is Long -> put(key, value)
                is Double -> put(key, value)
                is Float -> put(key, value)
                is Number -> put(key, value.toDouble())
                is Boolean -> put(key, value)
                is Map<*, *> -> {
                    val nested = value as Map<String, Any>
                    put(key, anyMapToJson(nested))
                }
                else -> put(key, value.toString())
            }
        }
    }
}
