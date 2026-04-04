package me.xiaok.opencode.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class QuestionRequest(
    val id: String = "",
    val sessionID: String = "",
    val questions: List<QuestionInfo> = emptyList(),
    val tool: QuestionToolRef = QuestionToolRef(),
)

@Serializable
data class QuestionInfo(
    val question: String = "",
    val header: String = "",
    val options: List<QuestionOption> = emptyList(),
    val multiple: Boolean = false,
    val custom: Boolean = true,
)

@Serializable
data class QuestionOption(
    val label: String = "",
    val description: String = "",
)

@Serializable
data class QuestionToolRef(
    val messageID: String = "",
    val callID: String = "",
)
