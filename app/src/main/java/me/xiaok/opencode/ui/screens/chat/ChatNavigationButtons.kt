package me.xiaok.opencode.ui.screens.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import kotlinx.coroutines.launch

// ---------------------------------------------------------------------------
// Message Navigation Buttons
// ---------------------------------------------------------------------------

@Composable
internal fun MessageNavigationButtons(
    listState: LazyListState,
    turnCount: Int,
    isLoadingMore: Boolean,
    autoScrollEnabled: Boolean,
    onAutoScrollToggled: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val offset = if (isLoadingMore) 1 else 0 // loading indicator occupies 1 item

    // Calculate current visible turn range
    val visibleItems = listState.layoutInfo.visibleItemsInfo
    val firstVisibleItem = visibleItems.firstOrNull()?.index ?: 0

    // Map LazyColumn item indices to turn indices
    val firstVisibleTurnIndex = (firstVisibleItem - offset).coerceIn(0, turnCount - 1)

    // "Current" turn = first fully visible turn (user's reading position)
    val currentTurnIndex = firstVisibleTurnIndex.coerceIn(0, turnCount - 1)

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        // Previous turn
        SmallFabButton(
            onClick = {
                scope.launch {
                    val target = (currentTurnIndex - 1).coerceAtLeast(0)
                    listState.animateScrollToItem(target + offset)
                }
            },
            enabled = currentTurnIndex > 0,
            icon = Icons.Default.KeyboardArrowUp,
            contentDescription = "Previous message",
        )

        // Auto-scroll toggle — ON = always chase bottom; OFF = no auto-scroll
        SmallFabButton(
            onClick = {
                onAutoScrollToggled()
                if (!autoScrollEnabled) {
                    // Turning ON: scroll to bottom immediately
                    scope.launch {
                        val lastItem = listState.layoutInfo.totalItemsCount - 1
                        if (lastItem >= 0) {
                            listState.animateScrollToItem(lastItem)
                        }
                    }
                }
            },
            enabled = true,
            icon = Icons.Default.KeyboardArrowDown,
            contentDescription = if (autoScrollEnabled) "Auto-scroll ON" else "Auto-scroll OFF",
            isActive = autoScrollEnabled,
        )
    }
}

@Composable
private fun SmallFabButton(
    onClick: () -> Unit,
    enabled: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    modifier: Modifier = Modifier,
    isActive: Boolean = false,
) {
    Surface(
        modifier = modifier.size(36.dp),
        shape = CircleShape,
        color = when {
            isActive -> MaterialTheme.colorScheme.primary
            enabled -> MaterialTheme.colorScheme.primaryContainer
            else -> MaterialTheme.colorScheme.surfaceVariant
        },
        contentColor = when {
            isActive -> MaterialTheme.colorScheme.onPrimary
            enabled -> MaterialTheme.colorScheme.onPrimaryContainer
            else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
        },
        shadowElevation = 3.dp,
        onClick = if (enabled) onClick else ({}),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier
                .fillMaxSize()
                .padding(6.dp),
        )
    }
}
