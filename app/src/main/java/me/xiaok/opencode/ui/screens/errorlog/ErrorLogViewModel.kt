package me.xiaok.opencode.ui.screens.errorlog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import me.xiaok.opencode.data.local.db.entity.ErrorLogEntity
import me.xiaok.opencode.data.repository.ErrorLogRepository
import javax.inject.Inject

@HiltViewModel
class ErrorLogViewModel @Inject constructor(
    private val errorLogRepository: ErrorLogRepository,
) : ViewModel() {

    val errors: StateFlow<List<ErrorLogEntity>> = errorLogRepository.allErrors
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun deleteById(id: Long) {
        viewModelScope.launch { errorLogRepository.deleteById(id) }
    }

    fun deleteAll() {
        viewModelScope.launch { errorLogRepository.deleteAll() }
    }
}
