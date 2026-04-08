package me.xiaok.opencode.ui.screens.chat.usecases

import me.xiaok.opencode.data.repository.DraftRepository
import me.xiaok.opencode.domain.model.ChatDraft
import me.xiaok.opencode.domain.model.ModelRef
import javax.inject.Inject

class DraftManagementUseCase @Inject constructor(
    private val draftRepository: DraftRepository,
) {
    suspend fun saveDraft(
        sessionId: String,
        text: String,
        selectedAgent: String?,
        selectedModel: ModelRef?,
        selectedVariant: String?,
        draftImageUris: List<String>,
    ) {
        if (text.isBlank() && draftImageUris.isEmpty()) {
            draftRepository.clearDraft(sessionId)
        } else {
            val draft = ChatDraft(
                text = text,
                selectedAgent = selectedAgent,
                selectedModel = selectedModel,
                selectedVariant = selectedVariant,
                imageUris = draftImageUris,
            )
            draftRepository.saveDraft(sessionId, draft)
        }
    }

    suspend fun addDraftImage(
        sessionId: String,
        uri: String,
        selectedAgent: String?,
        selectedModel: ModelRef?,
        selectedVariant: String?,
        currentImageUris: List<String>,
    ): List<String> {
        val updated = currentImageUris + uri
        val draft = ChatDraft(
            text = "",
            selectedAgent = selectedAgent,
            selectedModel = selectedModel,
            selectedVariant = selectedVariant,
            imageUris = updated,
        )
        draftRepository.saveDraft(sessionId, draft)
        return updated
    }

    suspend fun removeDraftImage(
        sessionId: String,
        uri: String,
        selectedAgent: String?,
        selectedModel: ModelRef?,
        selectedVariant: String?,
        currentImageUris: List<String>,
    ): List<String> {
        val updated = currentImageUris - uri
        val draft = ChatDraft(
            text = "",
            selectedAgent = selectedAgent,
            selectedModel = selectedModel,
            selectedVariant = selectedVariant,
            imageUris = updated,
        )
        draftRepository.saveDraft(sessionId, draft)
        return updated
    }
}
