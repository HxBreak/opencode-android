# Session List Mark All As Read Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a global action on the session list screen that marks all currently unread sessions as read.

**Architecture:** Keep the change minimal and reuse the existing unread system. The UI adds a single top-bar action in normal mode, the `SessionListViewModel` exposes a `markAllAsRead()` method, and that method iterates the current unread session IDs and calls the existing `eventReducer.markSessionViewed(serverId, sessionId)` API. This avoids new Room schema work, new repository batch APIs, and unnecessary cross-layer complexity.

**Tech Stack:** Kotlin, Jetpack Compose, StateFlow, Hilt ViewModel, Room-backed unread tracking via `SessionViewLog`, JUnit4, Robolectric, MockK, Gradle.

---

## File Map

- Modify: `app/src/main/java/me/xiaok/opencode/ui/screens/sessions/SessionListViewModel.kt`
  - Add `markAllAsRead()` and reuse the existing `ErrorCollector` + `_error` pattern.
- Modify: `app/src/main/java/me/xiaok/opencode/ui/screens/sessions/SessionListScreen.kt`
  - Wire a new callback from `SessionListRoute` into `SessionListScreen` and add a normal-mode top-bar action.
- Modify: `app/src/test/java/me/xiaok/opencode/ui/screens/sessions/SessionListViewModelTest.kt`
  - Add ViewModel tests for the new batch action using the existing real `EventReducer` + mocked `CacheRepository` setup.

## Scope Notes

- Do **not** add a new DAO method or repository batch API unless implementation proves the simple loop is insufficient.
- Do **not** change unread computation rules. Existing behavior remains: `session.time.updated > lastViewedAt` means unread.
- Do **not** add selection-mode behavior for this feature. The action is global and should work from the normal session list top bar.

### Task 1: Add failing ViewModel tests for the batch read action

**Files:**
- Modify: `app/src/test/java/me/xiaok/opencode/ui/screens/sessions/SessionListViewModelTest.kt:302-346`
- Reference: `app/src/main/java/me/xiaok/opencode/ui/screens/sessions/SessionListViewModel.kt:359-380`

- [ ] **Step 1: Write the failing test for clearing all unread sessions**

Append these tests near the existing selection-mode tests:

```kotlin
@Test
fun `mark all as read clears unread sessions and persists view logs`() = testScope.runTest {
    val vm = createViewModel()
    testScope.advanceUntilIdle()

    val before = vm.uiState.value
    assertEquals(setOf("ses_1", "ses_2"), before.unreadSessions)

    vm.markAllAsRead()
    testScope.advanceUntilIdle()

    val after = vm.uiState.value
    assertTrue(after.unreadSessions.isEmpty())
    coVerify(exactly = 1) { cacheRepository.markSessionViewed(serverId, "ses_1") }
    coVerify(exactly = 1) { cacheRepository.markSessionViewed(serverId, "ses_2") }
}

@Test
fun `mark all as read does nothing when there are no unread sessions`() = testScope.runTest {
    val viewedSession1 = testSession1.copy(time = TestFixtures.testSessionTime(updated = 100L))
    val viewedSession2 = testSession2.copy(time = TestFixtures.testSessionTime(updated = 120L))
    coEvery { api.listSessions(testServer, directory = null, roots = true) } returns listOf(viewedSession1, viewedSession2)
    coEvery { cacheRepository.getSessionViewLogs(serverId) } returns mapOf(
        "ses_1" to 500L,
        "ses_2" to 500L,
    )

    val vm = createViewModel()
    testScope.advanceUntilIdle()

    assertTrue(vm.uiState.value.unreadSessions.isEmpty())

    vm.markAllAsRead()
    testScope.advanceUntilIdle()

    coVerify(exactly = 0) { cacheRepository.markSessionViewed(serverId, any()) }
}
```

- [ ] **Step 2: Run the ViewModel test file to verify the new tests fail**

Run:

```bash
./gradlew testDebugUnitTest --tests "me.xiaok.opencode.ui.screens.sessions.SessionListViewModelTest"
```

Expected:

```text
SessionListViewModelTest > mark all as read clears unread sessions and persists view logs FAILED
SessionListViewModelTest > mark all as read does nothing when there are no unread sessions FAILED
```

- [ ] **Step 3: Commit the failing tests**

Run:

```bash
git add app/src/test/java/me/xiaok/opencode/ui/screens/sessions/SessionListViewModelTest.kt
git commit -m "test: cover mark all as read in session list"
```

Expected:

```text
[current-branch abc1234] test: cover mark all as read in session list
 1 file changed, ... insertions(+)
```

### Task 2: Implement `markAllAsRead()` in the ViewModel

**Files:**
- Modify: `app/src/main/java/me/xiaok/opencode/ui/screens/sessions/SessionListViewModel.kt:363-380`
- Test: `app/src/test/java/me/xiaok/opencode/ui/screens/sessions/SessionListViewModelTest.kt`

- [ ] **Step 1: Add the minimal ViewModel method**

Insert this method after `selectAll()` and before `deletePty()`:

```kotlin
fun markAllAsRead() {
    viewModelScope.launch {
        try {
            val unreadSessionIds = uiState.value.unreadSessions
            if (unreadSessionIds.isEmpty()) return@launch

            unreadSessionIds.forEach { sessionId ->
                eventReducer.markSessionViewed(serverId, sessionId)
            }
        } catch (e: Exception) {
            errorCollector.logError(e, "SessionList")
            _error.value = e.message ?: "Failed to mark sessions as read"
        }
    }
}
```

- [ ] **Step 2: Run the focused ViewModel tests to verify they pass**

Run:

```bash
./gradlew testDebugUnitTest --tests "me.xiaok.opencode.ui.screens.sessions.SessionListViewModelTest"
```

Expected:

```text
BUILD SUCCESSFUL
```

- [ ] **Step 3: Commit the ViewModel implementation**

Run:

```bash
git add app/src/main/java/me/xiaok/opencode/ui/screens/sessions/SessionListViewModel.kt app/src/test/java/me/xiaok/opencode/ui/screens/sessions/SessionListViewModelTest.kt
git commit -m "feat: add session list mark all as read action"
```

Expected:

```text
[current-branch def5678] feat: add session list mark all as read action
 2 files changed, ... insertions(+)
```

### Task 3: Add the top-bar action to the session list UI

**Files:**
- Modify: `app/src/main/java/me/xiaok/opencode/ui/screens/sessions/SessionListScreen.kt:16-35`
- Modify: `app/src/main/java/me/xiaok/opencode/ui/screens/sessions/SessionListScreen.kt:62-99`
- Modify: `app/src/main/java/me/xiaok/opencode/ui/screens/sessions/SessionListScreen.kt:107-132`
- Modify: `app/src/main/java/me/xiaok/opencode/ui/screens/sessions/SessionListScreen.kt:211-219`

- [ ] **Step 1: Add the new route callback**

In `SessionListRoute`, pass the new callback into `SessionListScreen`:

```kotlin
SessionListScreen(
    serverId = serverId,
    uiState = uiState,
    onRefresh = { viewModel.refreshSessions() },
    onCreateSession = { viewModel.createSession() { sessionId -> onNavigateToChat(serverId, sessionId) } },
    onDeleteSession = { viewModel.deleteSession(it) },
    onDeleteSelectedSessions = { viewModel.deleteSelectedSessions() },
    onUpdateSessionTitle = { id, title -> viewModel.updateSessionTitle(id, title) },
    onArchiveSession = { viewModel.archiveSession(it) },
    onUnarchiveSession = { viewModel.unarchiveSession(it) },
    onArchiveSelectedSessions = { viewModel.archiveSelectedSessions() },
    onSetArchiveFilter = { viewModel.setArchiveFilter(it) },
    onToggleSelection = { viewModel.toggleSelection(it) },
    onEnterSelectionMode = { viewModel.enterSelectionMode(it) },
    onExitSelectionMode = { viewModel.exitSelectionMode() },
    onSelectAll = { viewModel.selectAll() },
    onMarkAllAsRead = { viewModel.markAllAsRead() },
    onToggleDirectoryCollapsed = { viewModel.toggleDirectoryCollapsed(it) },
    onNavigateToChat = { sessionId -> onNavigateToChat(serverId, sessionId) },
    onNavigateBack = onNavigateBack,
    onSearchQueryChanged = { viewModel.onSearchQueryChanged(it) },
    onClearSearch = { viewModel.clearSearch() },
    onNavigateToTerminal = onNavigateToTerminal,
    onNavigateToTerminalWithPty = onNavigateToTerminalWithPty,
    onNavigateToFiles = { onNavigateToFiles(it) },
    onPtyDelete = { viewModel.deletePty(it) },
)
```

- [ ] **Step 2: Extend the composable signature**

Add the new callback parameter to `SessionListScreen`:

```kotlin
fun SessionListScreen(
    serverId: String,
    uiState: SessionListUiState,
    onRefresh: () -> Unit,
    onCreateSession: () -> Unit,
    onDeleteSession: (sessionId: String) -> Unit,
    onDeleteSelectedSessions: () -> Unit,
    onUpdateSessionTitle: (sessionId: String, title: String) -> Unit,
    onArchiveSession: (sessionId: String) -> Unit,
    onUnarchiveSession: (sessionId: String) -> Unit,
    onArchiveSelectedSessions: () -> Unit,
    onSetArchiveFilter: (SessionArchiveFilter) -> Unit,
    onToggleSelection: (sessionId: String) -> Unit,
    onEnterSelectionMode: (sessionId: String) -> Unit,
    onExitSelectionMode: () -> Unit,
    onSelectAll: () -> Unit,
    onMarkAllAsRead: () -> Unit,
    onToggleDirectoryCollapsed: (directory: String) -> Unit,
    onNavigateToChat: (sessionId: String) -> Unit,
    onNavigateBack: () -> Unit,
    onSearchQueryChanged: (String) -> Unit,
    onClearSearch: () -> Unit,
    onNavigateToTerminal: () -> Unit = {},
    onNavigateToTerminalWithPty: (ptyId: String) -> Unit = {},
    onNavigateToFiles: (directory: String) -> Unit = {},
    onPtyDelete: (ptyId: String) -> Unit = {},
)
```

- [ ] **Step 3: Add the icon import and the normal-mode action**

Add this import near the other Material icons:

```kotlin
import androidx.compose.material.icons.filled.DoneAll
```

Then update the normal-mode `TopAppBar.actions` block to show the new action only when unread sessions exist:

```kotlin
actions = {
    if (uiState.unreadSessions.isNotEmpty()) {
        IconButton(onClick = onMarkAllAsRead) {
            Icon(
                imageVector = Icons.Default.DoneAll,
                contentDescription = "Mark all as read",
            )
        }
    }
    IconButton(onClick = { isSearchMode = true }) {
        Icon(
            imageVector = Icons.Default.Search,
            contentDescription = "Search sessions",
        )
    }
}
```

- [ ] **Step 4: Run a focused compile check for the changed sources**

Run:

```bash
./gradlew testDebugUnitTest --tests "me.xiaok.opencode.ui.screens.sessions.SessionListViewModelTest"
./gradlew assembleDebug
```

Expected:

```text
BUILD SUCCESSFUL
```

- [ ] **Step 5: Commit the UI wiring**

Run:

```bash
git add app/src/main/java/me/xiaok/opencode/ui/screens/sessions/SessionListScreen.kt app/src/main/java/me/xiaok/opencode/ui/screens/sessions/SessionListViewModel.kt app/src/test/java/me/xiaok/opencode/ui/screens/sessions/SessionListViewModelTest.kt
git commit -m "feat: add mark all as read to session list"
```

Expected:

```text
[current-branch ghi9012] feat: add mark all as read to session list
 3 files changed, ... insertions(+)
```

### Task 4: Final verification and manual QA

**Files:**
- Verify: `app/src/main/java/me/xiaok/opencode/ui/screens/sessions/SessionListViewModel.kt`
- Verify: `app/src/main/java/me/xiaok/opencode/ui/screens/sessions/SessionListScreen.kt`
- Verify: `app/src/test/java/me/xiaok/opencode/ui/screens/sessions/SessionListViewModelTest.kt`

- [ ] **Step 1: Run the targeted unread-tracking tests**

Run:

```bash
./gradlew testDebugUnitTest --tests "me.xiaok.opencode.data.repository.EventReducerTest"
./gradlew testDebugUnitTest --tests "me.xiaok.opencode.data.repository.CacheRepositoryTest"
./gradlew testDebugUnitTest --tests "me.xiaok.opencode.ui.screens.sessions.SessionListViewModelTest"
```

Expected:

```text
BUILD SUCCESSFUL
```

- [ ] **Step 2: Run the required project-wide verification for Kotlin changes**

Run:

```bash
./gradlew test
./gradlew assembleDebug
```

Expected:

```text
BUILD SUCCESSFUL
```

- [ ] **Step 3: Manually verify the behavior on device or emulator**

Check this behavior end-to-end:

```text
1. Open the session list for a server with at least two unread sessions.
2. Confirm the top bar shows the "Mark all as read" action.
3. Tap the action.
4. Confirm all unread indicators disappear without entering selection mode.
5. Open one of the sessions, return to the list, and confirm the action stays hidden when there are no unread sessions.
6. Trigger a new unread update, return to the list, and confirm the action appears again.
```

- [ ] **Step 4: Check diagnostics on the changed files**

Run diagnostics for:

```text
app/src/main/java/me/xiaok/opencode/ui/screens/sessions/SessionListViewModel.kt
app/src/main/java/me/xiaok/opencode/ui/screens/sessions/SessionListScreen.kt
app/src/test/java/me/xiaok/opencode/ui/screens/sessions/SessionListViewModelTest.kt
```

Expected:

```text
No errors
```

- [ ] **Step 5: Final commit (only if earlier task commits were intentionally skipped)**

Run:

```bash
git add app/src/main/java/me/xiaok/opencode/ui/screens/sessions/SessionListViewModel.kt app/src/main/java/me/xiaok/opencode/ui/screens/sessions/SessionListScreen.kt app/src/test/java/me/xiaok/opencode/ui/screens/sessions/SessionListViewModelTest.kt
git commit -m "feat: support mark all as read from session list"
```

Expected:

```text
nothing to commit, working tree clean
```

## Self-Review

- Spec coverage: the plan covers the user-visible session-list action, ViewModel behavior, unread-state updates, and Kotlin-required verification.
- Placeholder scan: no `TODO`, `TBD`, or hand-wavy “write tests” steps remain.
- Type consistency: uses existing names and types from the codebase — `SessionListUiState.unreadSessions`, `EventReducer.markSessionViewed(serverId, sessionId)`, `SessionListRoute`, and `SessionListScreen`.
