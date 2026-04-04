package me.xiaok.opencode.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class Project(
    val id: String = "",
    val worktree: String = "",
    val vcs: String? = null,
    val name: String? = null,
    val icon: ProjectIcon? = null,
    val commands: ProjectCommands? = null,
    val time: ProjectTime = ProjectTime(),
    val sandboxes: List<String> = emptyList(),
)

@Serializable
data class ProjectIcon(
    val url: String? = null,
    val override: String? = null,
    val color: String? = null,
)

@Serializable
data class ProjectCommands(
    val start: String? = null,
)

@Serializable
data class ProjectTime(
    val created: Long = 0L,
    val updated: Long = 0L,
    val initialized: Long? = null,
)
