# ChatScreen.kt 拆分计划（方案 A）

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将 ChatScreen.kt（1322 行）按组件垂直拆分为 4 个独立文件，使 ChatScreen.kt 降至 ~550 行。

**Architecture:** 将 TopAppBar、TurnBubble、MessageBubble 系列、NavigationButtons 分别提取为独立的 Composable 文件。所有新文件与现有模式一致（public @Composable fun，private helpers），同目录 co-locate。ChatScreen.kt 保留 ChatRoute + ChatScreen 骨架 + 滚动逻辑 + Scaffold 编排。

**Tech Stack:** Kotlin + Jetpack Compose + Material 3

---

## File Structure

| 文件 | 操作 | 职责 | 预估行数 |
|------|------|------|----------|
| `ChatTopBar.kt` | **新建** | ChatScreen 的 TopAppBar + 下拉菜单 | ~130 |
| `ChatTurnBubble.kt` | **新建** | TurnBubble + 内嵌下拉菜单（Copy/Delete/Fork/Revert） | ~180 |
| `ChatMessageBubbles.kt` | **新建** | MessageBubble + UserMessageBubble + AssistantMessageBubble + ChatEmptyState | ~230 |
| `ChatNavigationButtons.kt` | **新建** | MessageNavigationButtons + SmallFabButton | ~100 |
| `ChatScreen.kt` | **修改** | 移除已提取的 composables，替换为对新文件的调用 | ~550 |

---

### Task 1: 提取 ChatNavigationButtons.kt

**Files:**
- Create: `app/src/main/java/me/xiaok/opencode/ui/screens/chat/ChatNavigationButtons.kt`
- Modify: `app/src/main/java/me/xiaok/opencode/ui/screens/chat/ChatScreen.kt` (删除 L1225-1320，替换调用)

**说明：** 这是最独立的组件，零耦合，最适合先提取验证流程。

- [ ] **Step 1: 创建 ChatNavigationButtons.kt**

从 ChatScreen.kt L1225-1320 提取以下两个 composable 到新文件。将 `private` 改为 `internal`（保持模块内可见）：

```kotlin
package me.xiaok.opencode.ui.screens.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

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
    val offset = if (isLoadingMore) 1 else 0

    val visibleItems = listState.layoutInfo.visibleItemsInfo
    val firstVisibleItem = visibleItems.firstOrNull()?.index ?: 0
    val firstVisibleTurnIndex = (firstVisibleItem - offset).coerceIn(0, turnCount - 1)
    val currentTurnIndex = firstVisibleTurnIndex.coerceIn(0, turnCount - 1)

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
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

        SmallFabButton(
            onClick = {
                onAutoScrollToggled()
                if (!autoScrollEnabled) {
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
    icon: ImageVector,
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
```

- [ ] **Step 2: 从 ChatScreen.kt 删除已提取代码**

删除 ChatScreen.kt 中 L1190-1322（`// Message Navigation Buttons` 段落注释 + `MessageNavigationButtons` + `SmallFabButton`），保留末尾空行。

ChatScreen.kt 中的调用点（L797-808）不需要改动——它调用的是 `MessageNavigationButtons(...)` 同包名函数，新文件同 package，自然可见。

- [ ] **Step 3: 验证构建**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/me/xiaok/opencode/ui/screens/chat/ChatNavigationButtons.kt app/src/main/java/me/xiaok/opencode/ui/screens/chat/ChatScreen.kt
git commit -m "refactor: extract MessageNavigationButtons to separate file"
```

---

### Task 2: 提取 ChatMessageBubbles.kt

**Files:**
- Create: `app/src/main/java/me/xiaok/opencode/ui/screens/chat/ChatMessageBubbles.kt`
- Modify: `app/src/main/java/me/xiaok/opencode/ui/screens/chat/ChatScreen.kt` (删除 L817-1013 + L1194-1219)

**说明：** 提取 MessageBubble 分发器、UserMessageBubble、AssistantMessageBubble 和 ChatEmptyState。这些是 TurnBubble 和 ChatScreen 的依赖项。

- [ ] **Step 1: 创建 ChatMessageBubbles.kt**

从 ChatScreen.kt 提取以下 composables。将 `private` 改为 `internal`（MessageBubble、UserMessageBubble、AssistantMessageBubble 被 ChatTurnBubble.kt 需要）：

```kotlin
package me.xiaok.opencode.ui.screens.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import me.xiaok.opencode.domain.model.Message
import me.xiaok.opencode.domain.model.Part

@Composable
internal fun MessageBubble(
    message: Message,
    parts: List<Part>,
    onMenuClick: () -> Unit = {},
    onNavigateToSession: (String) -> Unit = {},
    childSessionIds: Map<String, String> = emptyMap(),
    fontSize: String = "medium",
    onQuestionClick: (() -> Unit)? = null,
    onNavigateToToolDetail: (String) -> Unit = {},
    isLatestActiveReasoning: Boolean = false,
) {
    when {
        message.isUser -> {
            val allParts = parts.ifEmpty { message.parts }
            val hasCompactionOnly = allParts.isNotEmpty() && allParts.all { it is Part.Compaction }
            if (hasCompactionOnly) {
                allParts.filterIsInstance<Part.Compaction>().forEach { part ->
                    PartRenderer(part = part)
                }
            } else {
                UserMessageBubble(message = message, parts = parts, onMenuClick = onMenuClick)
            }
        }
        message.isAssistant -> AssistantMessageBubble(
            message = message,
            parts = parts,
            onMenuClick = onMenuClick,
            onNavigateToSession = onNavigateToSession,
            childSessionIds = childSessionIds,
            fontSize = fontSize,
            onQuestionClick = onQuestionClick,
            onNavigateToToolDetail = onNavigateToToolDetail,
            isLatestActiveReasoning = isLatestActiveReasoning,
        )
    }
}

@Composable
internal fun UserMessageBubble(
    message: Message,
    parts: List<Part>,
    onMenuClick: () -> Unit = {},
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
    ) {
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = 16.dp,
                bottomEnd = 4.dp,
            ),
            modifier = Modifier
                .widthIn(max = 320.dp)
                .fillMaxWidth()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onLongPress = { onMenuClick() }
                    )
                }
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                val allParts = parts.ifEmpty { message.parts }
                allParts.forEach { part ->
                    when (part) {
                        is Part.Text -> {
                            if (part.text.isNotEmpty()) {
                                Text(
                                    text = part.text,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                )
                            }
                        }
                        is Part.File -> {
                            Surface(
                                color = Color(0xFF2196F3).copy(alpha = 0.15f),
                                shape = RoundedCornerShape(4.dp),
                                modifier = Modifier.padding(vertical = 2.dp),
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = "📄",
                                        style = MaterialTheme.typography.labelSmall,
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = part.name.ifEmpty { part.url.removePrefix("file://") },
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Medium,
                                        ),
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    )
                                }
                            }
                        }
                        is Part.Agent -> {
                            Surface(
                                color = Color(0xFF9C27B0).copy(alpha = 0.15f),
                                shape = RoundedCornerShape(4.dp),
                                modifier = Modifier.padding(vertical = 2.dp),
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = "🤖",
                                        style = MaterialTheme.typography.labelSmall,
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = part.agent,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Medium,
                                        ),
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    )
                                }
                            }
                        }
                        else -> { /* Skip other part types in user bubble */ }
                    }
                }
            }
        }
    }
}

@Composable
internal fun AssistantMessageBubble(
    message: Message,
    parts: List<Part>,
    onMenuClick: () -> Unit = {},
    onNavigateToSession: (String) -> Unit = {},
    childSessionIds: Map<String, String> = emptyMap(),
    fontSize: String = "medium",
    onQuestionClick: (() -> Unit)? = null,
    onNavigateToToolDetail: (String) -> Unit = {},
    isLatestActiveReasoning: Boolean = false,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(Unit) {
                detectTapGestures(
                    onLongPress = { onMenuClick() }
                )
            }
    ) {
        val errorInfo = message.info.error
        if (errorInfo != null && errorInfo.message.isNotEmpty()) {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = errorInfo.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(12.dp),
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
        }

        val groupedParts = remember(parts) { groupParts(parts) }
        groupedParts.forEach { grouped ->
            GroupedPartRenderer(
                grouped = grouped,
                onNavigateToSession = onNavigateToSession,
                childSessionIds = childSessionIds,
                fontSize = fontSize,
                onQuestionClick = onQuestionClick,
                onNavigateToToolDetail = onNavigateToToolDetail,
                isLatestActiveReasoning = isLatestActiveReasoning,
            )
            Spacer(modifier = Modifier.height(4.dp))
        }
    }
}

@Composable
internal fun ChatEmptyState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "Start a conversation",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Send a message to begin",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            )
        }
    }
}
```

- [ ] **Step 2: 从 ChatScreen.kt 删除已提取代码**

删除以下段落：
1. L813-1013（`// Message Bubble` 段落注释 + `MessageBubble` + `UserMessageBubble` + `AssistantMessageBubble`）
2. L1190-1219（`// Empty State` 段落注释 + `ChatEmptyState`）

ChatScreen.kt 中的调用点不需要改动——同 package 可见。

- [ ] **Step 3: 验证构建**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/me/xiaok/opencode/ui/screens/chat/ChatMessageBubbles.kt app/src/main/java/me/xiaok/opencode/ui/screens/chat/ChatScreen.kt
git commit -m "refactor: extract MessageBubble composables to separate file"
```

---

### Task 3: 提取 ChatTurnBubble.kt

**Files:**
- Create: `app/src/main/java/me/xiaok/opencode/ui/screens/chat/ChatTurnBubble.kt`
- Modify: `app/src/main/java/me/xiaok/opencode/ui/screens/chat/ChatScreen.kt` (删除 L1015-1188)

**说明：** TurnBubble 依赖 Task 2 提取的 MessageBubble 和 UserMessageBubble（internal 可见）。这是最大的独立块（~170 行）。

- [ ] **Step 1: 创建 ChatTurnBubble.kt**

```kotlin
package me.xiaok.opencode.ui.screens.chat

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallSplit
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import me.xiaok.opencode.domain.model.Part

@Composable
internal fun TurnBubble(
    turn: ChatTurn,
    onCopyMessage: (String) -> Unit = {},
    onDeleteMessage: (String) -> Unit = {},
    onForkSession: (String) -> Unit = {},
    onRevertSession: (String) -> Unit = {},
    onNavigateToSession: (String) -> Unit = {},
    onNavigateToToolDetail: (String) -> Unit = {},
    fontSize: String = "medium",
    isLastTurn: Boolean = false,
    isActiveSession: Boolean = false,
) {
    val grouped = turn.groupedParts
    val partLookup = turn.partLookup
    val isCompactionOnly = turn.isCompactionOnly
    val userParts = turn.userParts
    val isSyntheticUser = turn.isSyntheticUser
    val childSessionIds = turn.childSessionIdLookup

    val isLatestActiveReasoning = if (isLastTurn && isActiveSession) {
        turn.isActivelyReasoning
    } else false

    var showMenu by remember { mutableStateOf(false) }
    val hasRealUserMessage = turn.userMessage.id.isNotEmpty()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(Unit) {
                detectTapGestures(
                    onLongPress = { showMenu = true }
                )
            },
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        // 1. User message (or compaction divider)
        if (isCompactionOnly) {
            userParts.filterIsInstance<Part.Compaction>().forEach { part ->
                PartRenderer(part = part)
            }
        } else if (!isSyntheticUser) {
            UserMessageBubble(
                message = turn.userMessage,
                parts = userParts,
                onMenuClick = { showMenu = true },
            )
        }

        // 2. Error indicator from last assistant message
        val lastAssistant = turn.assistantMessages.lastOrNull()
        val errorInfo = lastAssistant?.info?.error
        if (errorInfo != null && errorInfo.message.isNotEmpty()) {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = errorInfo.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(12.dp),
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
        }

        // 3. Assistant parts (cross-message grouped)
        for (group in grouped) {
            when (group) {
                is TurnPartGroup.Single -> {
                    val part = partLookup[group.ref] ?: continue
                    PartRenderer(
                        part = part,
                        onNavigateToSession = onNavigateToSession,
                        childSessionIds = childSessionIds,
                        fontSize = fontSize,
                        onNavigateToToolDetail = onNavigateToToolDetail,
                        isLatestActiveReasoning = isLatestActiveReasoning,
                    )
                }
                is TurnPartGroup.ContextGroup -> {
                    val tools = group.refs.mapNotNull { ref ->
                        partLookup[ref] as? Part.Tool
                    }
                    if (tools.isNotEmpty()) {
                        ContextToolGroup(
                            tools = tools,
                            onNavigateToToolDetail = onNavigateToToolDetail,
                        )
                    }
                }
            }
        }

        // Dropdown menu at Turn level
        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false },
        ) {
            DropdownMenuItem(
                text = { Text("Copy") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                },
                onClick = {
                    showMenu = false
                    val text = partLookup.values
                        .filterIsInstance<Part.Text>()
                        .joinToString("\n") { it.text }
                    onCopyMessage(text)
                },
            )
            if (hasRealUserMessage) {
                DropdownMenuItem(
                    text = { Text("Delete") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                    },
                    onClick = {
                        showMenu = false
                        onDeleteMessage(turn.userMessage.id)
                    },
                )
            }
            if (hasRealUserMessage) {
                DropdownMenuItem(
                    text = { Text("Fork from here") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Default.CallSplit,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                    },
                    onClick = {
                        showMenu = false
                        onForkSession(turn.userMessage.id)
                    },
                )
            }
            if (hasRealUserMessage) {
                DropdownMenuItem(
                    text = { Text("Revert to here") },
                    onClick = {
                        showMenu = false
                        onRevertSession(turn.userMessage.id)
                    },
                )
            }
        }
    }
}
```

- [ ] **Step 2: 从 ChatScreen.kt 删除已提取代码**

删除 L1015-1188（`// Turn Bubble` 段落注释 + `TurnBubble` 函数）。

ChatScreen.kt 中的调用点（`TurnBubble(...)` 在 LazyColumn items 中）不需要改动。

- [ ] **Step 3: 验证构建**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/me/xiaok/opencode/ui/screens/chat/ChatTurnBubble.kt app/src/main/java/me/xiaok/opencode/ui/screens/chat/ChatScreen.kt
git commit -m "refactor: extract TurnBubble to separate file"
```

---

### Task 4: 提取 ChatTopBar.kt

**Files:**
- Create: `app/src/main/java/me/xiaok/opencode/ui/screens/chat/ChatTopBar.kt`
- Modify: `app/src/main/java/me/xiaok/opencode/ui/screens/chat/ChatScreen.kt` (替换 L489-617 的 inline TopAppBar)

**说明：** TopAppBar 内嵌在 `Scaffold(topBar = { ... })` 中，需要提取为一个独立 Composable。它使用 `showMenu` 和 `scrollBehavior` 等局部状态——这些状态需要提升为参数。同时 `onRenameDialog` 和 `onUnrevertSession` 回调也需要通过参数传入。

- [ ] **Step 1: 创建 ChatTopBar.kt**

```kotlin
package me.xiaok.opencode.ui.screens.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import me.xiaok.opencode.domain.model.SessionStatus
import me.xiaok.opencode.ui.components.common.formatTokenCount

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ChatTopBar(
    sessionTitle: String,
    totalTokens: Long,
    isShared: Boolean,
    sessionStatus: SessionStatus,
    showMenu: Boolean,
    onShowMenuChange: (Boolean) -> Unit,
    scrollBehavior: TopAppBarScrollBehavior,
    onNavigateBack: () -> Unit,
    onNavigateToSessionDiff: () -> Unit,
    onAbort: () -> Unit,
    onExportSession: () -> Unit,
    onRenameSession: () -> Unit,
    onUnrevertSession: () -> Unit,
    onDeleteSession: () -> Unit,
    hasRevert: Boolean,
) {
    TopAppBar(
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Column(modifier = Modifier.weight(1f, fill = false)) {
                    Text(
                        text = sessionTitle.ifEmpty { "Chat" },
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                        ),
                        maxLines = 1,
                    )
                    if (totalTokens > 0) {
                        Text(
                            text = formatTokenCount(totalTokens) + " tokens",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (isShared) {
                    Icon(
                        imageVector = Icons.Default.Link,
                        contentDescription = "Shared",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        },
        navigationIcon = {
            IconButton(onClick = onNavigateBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                )
            }
        },
        actions = {
            IconButton(onClick = onNavigateToSessionDiff) {
                Icon(
                    imageVector = Icons.Default.Description,
                    contentDescription = "Changes",
                )
            }
            if (sessionStatus !is SessionStatus.Idle) {
                IconButton(onClick = onAbort) {
                    Icon(
                        imageVector = Icons.Default.Stop,
                        contentDescription = "Stop",
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
            DropdownMenuWrapper(
                expanded = showMenu,
                onDismiss = { onShowMenuChange(false) },
                onRename = {
                    onShowMenuChange(false)
                    onRenameSession()
                },
                onExport = {
                    onShowMenuChange(false)
                    onExportSession()
                },
                onUnrevert = {
                    onShowMenuChange(false)
                    onUnrevertSession()
                },
                onDelete = {
                    onShowMenuChange(false)
                    onDeleteSession()
                },
                hasRevert = hasRevert,
                onShowMenu = { onShowMenuChange(true) },
            )
        },
        scrollBehavior = scrollBehavior,
    )
}

@Composable
private fun DropdownMenuWrapper(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onRename: () -> Unit,
    onExport: () -> Unit,
    onUnrevert: () -> Unit,
    onDelete: () -> Unit,
    hasRevert: Boolean,
    onShowMenu: () -> Unit,
) {
    Box {
        IconButton(
            onClick = onShowMenu,
            modifier = Modifier.testTag("chat_more"),
        ) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = "More",
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = onDismiss,
        ) {
            DropdownMenuItem(
                text = { Text("Rename") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                },
                onClick = onRename,
            )
            DropdownMenuItem(
                text = { Text("Export") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                },
                onClick = onExport,
            )
            if (hasRevert) {
                DropdownMenuItem(
                    text = { Text("Unrevert") },
                    onClick = onUnrevert,
                )
            }
            DropdownMenuItem(
                text = { Text("Delete") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                },
                onClick = onDelete,
            )
        }
    }
}
```

**注意：** `ChatTopBar.kt` 需要 `import androidx.compose.ui.platform.testTag` 和 `import androidx.compose.foundation.layout.Box`。在实际创建文件时，需要确保 `DropdownMenuWrapper` 中的 `Box` 和 `testTag` 有正确的 import。具体依赖以下 import：

```kotlin
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.platform.testTag
```

- [ ] **Step 2: 修改 ChatScreen.kt 的 Scaffold topBar**

将 ChatScreen.kt 中 `Scaffold` 的 `topBar = { ... }` 块（约 L489-617）替换为：

```kotlin
        topBar = {
            ChatTopBar(
                sessionTitle = uiState.session?.title ?: "",
                totalTokens = uiState.totalTokens,
                isShared = uiState.session?.share != null,
                sessionStatus = uiState.sessionStatus,
                showMenu = showMenu,
                onShowMenuChange = { showMenu = it },
                scrollBehavior = scrollBehavior,
                onNavigateBack = onNavigateBack,
                onNavigateToSessionDiff = onNavigateToSessionDiff,
                onAbort = onAbort,
                onExportSession = onExportSession,
                onRenameSession = { showRenameDialog = true },
                onUnrevertSession = onUnrevertSession,
                onDeleteSession = onDeleteSession,
                hasRevert = uiState.session?.revert != null,
            )
        },
```

同时，从 ChatScreen.kt 中移除不再需要的 import（如 `Icons.Default.Edit`、`Icons.Default.Download`、`Icons.Default.Delete`、`Icons.Default.Stop`、`Icons.Default.MoreVert`、`Icons.Default.Description`、`Icons.Default.Link`、`Icons.AutoMirrored.Filled.ArrowBack`、`TopAppBar`、`DropdownMenu`、`DropdownMenuItem`、`ExperimentalMaterial3Api`）——仅当这些 import 没有在 ChatScreen.kt 的其他地方使用时才移除。

- [ ] **Step 3: 验证构建**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/me/xiaok/opencode/ui/screens/chat/ChatTopBar.kt app/src/main/java/me/xiaok/opencode/ui/screens/chat/ChatScreen.kt
git commit -m "refactor: extract ChatTopBar to separate file"
```

---

### Task 5: 清理 ChatScreen.kt + 最终验证

**Files:**
- Modify: `app/src/main/java/me/xiaok/opencode/ui/screens/chat/ChatScreen.kt`

**说明：** 清理孤儿 import，确认最终行数，运行完整测试。

- [ ] **Step 1: 清理孤儿 import**

检查 ChatScreen.kt 中是否有以下不再使用的 import（被提取到其他文件后可能变成孤儿）：

- `Icons.Default.Edit`, `Icons.Default.Download`, `Icons.Default.Delete`, `Icons.Default.Stop`, `Icons.Default.MoreVert`, `Icons.Default.Description`, `Icons.Default.Link`
- `Icons.AutoMirrored.Filled.ArrowBack`, `Icons.AutoMirrored.Filled.CallSplit`
- `Icons.Default.ContentCopy`, `Icons.Default.KeyboardArrowUp`, `Icons.Default.KeyboardArrowDown`
- `Icons.Default.ExpandLess`, `Icons.Default.ExpandMore`
- `TopAppBar`, `DropdownMenu`, `DropdownMenuItem`, `ExperimentalMaterial3Api`
- `CircleShape`, `RoundedCornerShape`
- `FontFamily`, `SpanStyle`, `buildAnnotatedString`, `withStyle`
- `Color`
- `detectTapGestures`
- `sp`

逐个检查是否在 ChatScreen.kt 中仍有使用，删除未使用的。

- [ ] **Step 2: 检查最终行数**

Run: `wc -l app/src/main/java/me/xiaok/opencode/ui/screens/chat/ChatScreen.kt`
Expected: ~550 行

- [ ] **Step 3: 构建验证**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: 运行单元测试**

Run: `./gradlew test`
Expected: 所有测试通过（无新增测试失败）

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/me/xiaok/opencode/ui/screens/chat/ChatScreen.kt
git commit -m "refactor: clean up orphan imports in ChatScreen after extraction"
```

---

## 预期结果

| 文件 | 行数 | 职责 |
|------|------|------|
| `ChatScreen.kt` | ~550 | ChatRoute + ChatScreen 骨架（Scaffold + 滚动逻辑 + bottomBar + 消息列表编排） |
| `ChatTopBar.kt` | ~160 | TopAppBar + 下拉菜单 |
| `ChatTurnBubble.kt` | ~180 | Turn 渲染 + 操作菜单 |
| `ChatMessageBubbles.kt` | ~230 | Message/User/Assistant 气泡 + 空状态 |
| `ChatNavigationButtons.kt` | ~100 | 消息导航浮动按钮 |

所有文件均在 800 行限制内。
