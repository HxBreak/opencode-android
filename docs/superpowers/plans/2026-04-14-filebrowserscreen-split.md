# FileBrowserScreen.kt Split Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Split FileBrowserScreen.kt (1253 lines) into 5 files, each under 800 lines, following the same pattern used for ChatScreen.kt.

**Architecture:** Extract independent composable groups into separate files within the same package `me.xiaok.opencode.ui.screens.files`. Change visibility from `private` to `internal` for cross-file composables; keep `private` for sub-composables only used within their file. No logic changes, no behavioral changes.

**Tech Stack:** Kotlin, Jetpack Compose, Material 3

---

## File Structure

| File | Status | Est. Lines | Responsibility |
|------|--------|------------|----------------|
| `files/FileBrowserScreen.kt` | Modify | ~200 | Route + main Screen (state dispatch) |
| `files/FileBrowserTopBar.kt` | Create | ~210 | 4 TopBar composables |
| `files/FileBrowserDirectory.kt` | Create | ~220 | Directory browsing composables |
| `files/FileBrowserViewer.kt` | Create | ~440 | File content viewer + syntax highlighting |
| `files/FileBrowserSearch.kt` | Create | ~160 | Search results composables |

### Visibility Rules

- Functions called by `FileBrowserScreen.kt` → change from `private` to `internal`
- Functions only used within their own file → keep `private`
- Constants and data classes used only within their file → keep `private`

### Dependency Graph

```
FileBrowserScreen.kt (Route + Screen)
  ├── FileBrowserTopBar.kt: DirectoryTopBar, SearchTopBar, FileViewerTopBar
  ├── FileBrowserDirectory.kt: DirectoryBrowserView
  ├── FileBrowserViewer.kt: FileContentViewer
  └── FileBrowserSearch.kt: SearchResultsView
```

No cross-dependencies between the 4 extracted files. Each is only used by FileBrowserScreen.kt.

### Original File Line Map (for reference)

| Section | Original Lines | Destination File |
|---------|---------------|-----------------|
| Imports + comments | 1-88 | stays in FileBrowserScreen.kt |
| FileBrowserRoute | 89-119 | stays in FileBrowserScreen.kt |
| FileBrowserScreen | 121-243 | stays in FileBrowserScreen.kt |
| Top Bars (4 functions) | 245-419 | FileBrowserTopBar.kt |
| Directory Browser (5 functions) | 421-640 | FileBrowserDirectory.kt |
| File Content Viewer (5 functions) | 642-896 | FileBrowserViewer.kt |
| Syntax Highlighting (3 items) | 898-1053 | FileBrowserViewer.kt |
| Search Results (4 functions) | 1055-1253 | FileBrowserSearch.kt |

---

### Task 1: Extract FileBrowserTopBar.kt

**Files:**
- Create: `app/src/main/java/me/xiaok/opencode/ui/screens/files/FileBrowserTopBar.kt`
- Modify: `app/src/main/java/me/xiaok/opencode/ui/screens/files/FileBrowserScreen.kt`

This task extracts 4 TopBar composables (original lines 245-419, ~175 lines).

- [ ] **Step 1: Create FileBrowserTopBar.kt**

Create new file with package `me.xiaok.opencode.ui.screens.files`.

Extract these functions from the original file (copy exact code, change visibility):
1. `DirectoryTopBar` (lines 249-292) — change `private fun` → `internal fun`
2. `BreadcrumbPath` (lines 294-339) — keep `private fun` (only used by DirectoryTopBar in same file)
3. `FileViewerTopBar` (lines 341-370) — change `private fun` → `internal fun`
4. `SearchTopBar` (lines 372-419) — change `private fun` → `internal fun`

Also copy the section comment `// Top Bars` and the `@OptIn(ExperimentalMaterial3Api::class)` annotations.

**Imports needed for this file:**
```
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
```

- [ ] **Step 2: Remove extracted code from FileBrowserScreen.kt**

Delete lines 245-419 from FileBrowserScreen.kt (from `// Top Bars` comment through the closing `}` of `SearchTopBar`).

Remove these now-unused imports from FileBrowserScreen.kt:
- `import androidx.compose.foundation.horizontalScroll`
- `import androidx.compose.foundation.rememberScrollState`
- `import androidx.compose.foundation.text.KeyboardActions`
- `import androidx.compose.foundation.text.KeyboardOptions`
- `import androidx.compose.material.icons.automirrored.filled.ArrowBack`
- `import androidx.compose.material.icons.filled.Close`
- `import androidx.compose.material.icons.filled.Search`
- `import androidx.compose.material3.OutlinedTextField`
- `import androidx.compose.ui.text.input.ImeAction`

- [ ] **Step 3: Verify build**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

---

### Task 2: Extract FileBrowserDirectory.kt

**Files:**
- Create: `app/src/main/java/me/xiaok/opencode/ui/screens/files/FileBrowserDirectory.kt`
- Modify: `app/src/main/java/me/xiaok/opencode/ui/screens/files/FileBrowserScreen.kt`

This task extracts 5 directory browsing composables (original lines 421-640, ~220 lines).

- [ ] **Step 1: Create FileBrowserDirectory.kt**

Create new file with package `me.xiaok.opencode.ui.screens.files`.

Extract these functions from the original file (copy exact code, change visibility):
1. `DirectoryBrowserView` (lines 425-496) — change `private fun` → `internal fun`
2. `NavigateUpItem` (lines 498-522) — keep `private fun`
3. `FileNodeItem` (lines 524-569) — keep `private fun`
4. `FileIcon` (lines 571-612) — keep `private fun`
5. `GitStatusBadge` (lines 614-640) — keep `private fun`

**Imports needed for this file:**
```
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.xiaok.opencode.domain.model.FileNode
import me.xiaok.opencode.domain.model.FileStatus
```

- [ ] **Step 2: Remove extracted code from FileBrowserScreen.kt**

Delete the directory browser section from FileBrowserScreen.kt (from `// Directory Browser` comment through end of `GitStatusBadge`).

Remove these now-unused imports from FileBrowserScreen.kt:
- `import androidx.compose.foundation.shape.RoundedCornerShape`
- `import androidx.compose.material.icons.filled.Folder`
- `import androidx.compose.material.icons.filled.FolderOpen`
- `import androidx.compose.material.icons.filled.KeyboardArrowUp`
- `import me.xiaok.opencode.domain.model.FileNode`
- `import me.xiaok.opencode.domain.model.FileStatus`

- [ ] **Step 3: Verify build**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

---

### Task 3: Extract FileBrowserViewer.kt

**Files:**
- Create: `app/src/main/java/me/xiaok/opencode/ui/screens/files/FileBrowserViewer.kt`
- Modify: `app/src/main/java/me/xiaok/opencode/ui/screens/files/FileBrowserScreen.kt`

This is the largest extraction. It contains the file content viewer composables + syntax highlighting engine + helper functions (original lines 642-1053, ~412 lines).

- [ ] **Step 1: Create FileBrowserViewer.kt**

Create new file with package `me.xiaok.opencode.ui.screens.files`.

Extract these items from the original file (copy exact code, change visibility where noted):

**Composables:**
1. `FileContentViewer` (lines 649-672) — change `private fun` → `internal fun`
2. `BinaryImagePreview` (lines 674-718) — keep `private fun`
3. `BinaryFilePlaceholder` (lines 720-761) — keep `private fun`
4. `TextFileContentViewer` (lines 763-838) — keep `private fun`
5. `DiffSection` (lines 840-896) — keep `private fun`

**Constants (keep private):**
6. `MAX_FILE_SIZE_CHARS` (line 646)
7. `MAX_DISPLAY_LINES` (line 647)

**Helper functions (keep private):**
8. `formatFileSize` (lines 898-904)
9. `highlightSyntaxLine` (lines 906-944)

**Syntax highlighting (keep private):**
10. `SyntaxRule` data class (lines 950-953)
11. `getSyntaxRules` (lines 955-1053)

**Imports needed for this file:**
```
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import me.xiaok.opencode.domain.model.FileContent
```

- [ ] **Step 2: Remove extracted code from FileBrowserScreen.kt**

Delete the entire file content viewer section + syntax highlighting section from FileBrowserScreen.kt (from `// File Content Viewer` comment through end of `getSyntaxRules`).

Remove these now-unused imports from FileBrowserScreen.kt:
- `import androidx.compose.foundation.Image`
- `import androidx.compose.foundation.horizontalScroll` (if still present after Task 1)
- `import androidx.compose.foundation.rememberScrollState` (if still present after Task 1)
- `import androidx.compose.foundation.text.selection.SelectionContainer`
- `import androidx.compose.foundation.verticalScroll`
- `import androidx.compose.material.icons.filled.Folder` (if still present after Task 2)
- `import androidx.compose.material.icons.filled.FolderOpen` (if still present after Task 2)
- `import androidx.compose.ui.graphics.Color`
- `import androidx.compose.ui.graphics.asImageBitmap`
- `import androidx.compose.ui.layout.ContentScale`
- `import androidx.compose.ui.text.AnnotatedString`
- `import androidx.compose.ui.text.SpanStyle`
- `import androidx.compose.ui.text.buildAnnotatedString`
- `import androidx.compose.ui.text.font.FontStyle`
- `import androidx.compose.ui.text.withStyle`
- `import me.xiaok.opencode.domain.model.FileContent`

- [ ] **Step 3: Verify build**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

---

### Task 4: Extract FileBrowserSearch.kt

**Files:**
- Create: `app/src/main/java/me/xiaok/opencode/ui/screens/files/FileBrowserSearch.kt`
- Modify: `app/src/main/java/me/xiaok/opencode/ui/screens/files/FileBrowserScreen.kt`

This task extracts 4 search result composables (original lines 1055-1253, ~198 lines).

- [ ] **Step 1: Create FileBrowserSearch.kt**

Create new file with package `me.xiaok.opencode.ui.screens.files`.

Extract these functions from the original file (copy exact code, change visibility):
1. `SearchResultsView` (lines 1059-1136) — change `private fun` → `internal fun`
2. `ContentSearchResultItem` (lines 1138-1202) — keep `private fun`
3. `FileSearchResultItem` (lines 1204-1237) — keep `private fun`
4. `EmptySearchResult` (lines 1239-1253) — keep `private fun`

**Imports needed for this file:**
```
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
```

- [ ] **Step 2: Remove extracted code from FileBrowserScreen.kt**

Delete the search results section from FileBrowserScreen.kt (from `// Search Results View` comment through end of file).

Remove these now-unused imports from FileBrowserScreen.kt:
- `import androidx.compose.foundation.lazy.LazyColumn`
- `import androidx.compose.foundation.lazy.items`
- `import androidx.compose.material.icons.filled.Description`
- `import androidx.compose.material3.LinearProgressIndicator`
- `import androidx.compose.material3.ScrollableTabRow`
- `import androidx.compose.material3.Tab`
- `import androidx.compose.ui.text.AnnotatedString` (if still present after Task 3)
- `import androidx.compose.ui.text.buildAnnotatedString` (if still present after Task 3)
- `import androidx.compose.ui.text.style.TextOverflow`
- `import kotlinx.serialization.json.JsonObject`
- `import kotlinx.serialization.json.JsonPrimitive`
- `import kotlinx.serialization.json.jsonPrimitive`

**Note:** `import me.xiaok.opencode.domain.model.FileContent` should have been removed in Task 3. Double-check it's gone.

- [ ] **Step 3: Verify build**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

---

### Task 5: Clean up FileBrowserScreen.kt

**Files:**
- Modify: `app/src/main/java/me/xiaok/opencode/ui/screens/files/FileBrowserScreen.kt`

After Tasks 1-4, FileBrowserScreen.kt should contain only:
- Package declaration
- Minimal imports (only what Route + Screen need)
- `FileBrowserRoute` composable
- `FileBrowserScreen` composable

- [ ] **Step 1: Audit remaining imports**

FileBrowserScreen.kt should only need these imports after all extractions:

```
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
```

Remove any imports NOT in this list that remain in the file.

- [ ] **Step 2: Verify line count**

Run: `wc -l app/src/main/java/me/xiaok/opencode/ui/screens/files/FileBrowserScreen.kt`
Expected: under 250 lines

- [ ] **Step 3: Final build verification**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Verify all files are under 800 lines**

Run:
```bash
wc -l app/src/main/java/me/xiaok/opencode/ui/screens/files/*.kt
```
Expected: All files under 800 lines.

---

### Task 6: Device Verification

- [ ] **Step 1: Install and run on device**

Run:
```bash
adb -s 192.168.31.218:5555 install -r app/build/outputs/apk/debug/app-debug.apk
```

- [ ] **Step 2: Verify file browser functionality**

On the device, verify:
1. Navigate to file browser from home screen
2. Directory browsing works (navigate into/out of directories)
3. Breadcrumb path navigation works
4. File content viewing works (open a .kt file, verify syntax highlighting)
5. Binary file placeholder shows correctly
6. Search (content search + file name search) works
7. Back navigation works from all states
