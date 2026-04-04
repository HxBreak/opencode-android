package me.xiaok.opencode.ui.navigation

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import me.xiaok.opencode.utils.NetworkMonitor
import javax.inject.Inject

/**
 * Minimal ViewModel used only to inject NetworkMonitor into the NavGraph composable.
 */
@HiltViewModel
class OfflineViewModel @Inject constructor(
    val networkMonitor: NetworkMonitor,
) : ViewModel()
