package me.xiaok.opencode.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class PtyInfo(
    val id: String = "",
    val title: String = "",
    val command: String = "",
    val args: List<String> = emptyList(),
    val cwd: String = "",
    val status: String = "",      // "running" | "exited"
    val pid: Long = 0L,
)

@Serializable
data class PtyCreateRequest(
    val command: String? = null,
    val args: List<String>? = null,
    val cwd: String? = null,
    val title: String? = null,
    val env: Map<String, String>? = null,
)

@Serializable
data class PtyUpdateRequest(
    val title: String? = null,
    val size: PtySize? = null,
)

@Serializable
data class PtySize(
    val rows: Int = 24,
    val cols: Int = 80,
)
