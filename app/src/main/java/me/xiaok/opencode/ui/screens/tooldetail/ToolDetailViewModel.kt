package me.xiaok.opencode.ui.screens.tooldetail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import me.xiaok.opencode.domain.model.ToolState
import javax.inject.Inject

data class ToolDetailUiState(
    val toolName: String = "",
    val state: ToolState = ToolState(),
    val childSessionId: String? = null,
)

@HiltViewModel
class ToolDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val partId: String = savedStateHandle.get<String>("partId") ?: ""
    private val cached = ToolDetailCache.get(partId)

    private val _uiState = MutableStateFlow(
        ToolDetailUiState(
            toolName = cached?.toolName ?: "",
            state = cached?.state ?: ToolState(),
            childSessionId = cached?.childSessionId,
        )
    )
    val uiState: StateFlow<ToolDetailUiState> = _uiState.asStateFlow()
}
