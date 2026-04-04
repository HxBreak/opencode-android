package me.xiaok.opencode.ui.screens.diff

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.combine
import javax.inject.Inject

data class DiffViewerUiState(
    val diffText: String = "",
    val title: String? = null,
)

@HiltViewModel
class DiffViewerViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val _diffText = MutableStateFlow(
        savedStateHandle.get<String>("diffText") ?: ""
    )
    private val _title = MutableStateFlow(
        savedStateHandle.get<String?>("title")
    )

    val uiState: StateFlow<DiffViewerUiState> = combine(
        _diffText,
        _title,
    ) { diffText, title ->
        DiffViewerUiState(
            diffText = diffText,
            title = title,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DiffViewerUiState())
}
