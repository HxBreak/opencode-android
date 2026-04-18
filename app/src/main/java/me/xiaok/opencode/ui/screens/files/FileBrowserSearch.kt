package me.xiaok.opencode.ui.screens.files

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
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

// Search Results View
// ---------------------------------------------------------------------------

@Composable
internal fun SearchResultsView(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    searchQuery: String,
    contentResults: List<kotlinx.serialization.json.JsonElement>,
    fileResults: List<String>,
    isSearching: Boolean,
    onFileClick: (String) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Tabs
        ScrollableTabRow(
            selectedTabIndex = selectedTab,
            edgePadding = 16.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Tab(
                selected = selectedTab == 0,
                onClick = { onTabSelected(0) },
                text = { Text("Content") },
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { onTabSelected(1) },
                text = { Text("Files") },
            )
        }

        // Loading indicator
        if (isSearching) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth(),
            )
        }

        // Results
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
        ) {
            if (selectedTab == 0) {
                if (contentResults.isEmpty() && !isSearching && searchQuery.isNotBlank()) {
                    item {
                        EmptySearchResult(message = "No content matches found")
                    }
                }
                items(
                    items = contentResults,
                    key = { it.toString() },
                ) { result ->
                    ContentSearchResultItem(
                        result = result,
                        onClick = onFileClick,
                    )
                }
            } else {
                if (fileResults.isEmpty() && !isSearching && searchQuery.isNotBlank()) {
                    item {
                        EmptySearchResult(message = "No files found")
                    }
                }
                items(
                    items = fileResults,
                    key = { it },
                ) { filePath ->
                    FileSearchResultItem(
                        filePath = filePath,
                        onClick = { onFileClick(filePath) },
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun ContentSearchResultItem(
    result: kotlinx.serialization.json.JsonElement,
    onClick: (String) -> Unit,
) {
    // Parse the JsonElement to extract file path and matching line
    val filePath = (result as? JsonObject)
        ?.get("path")
        ?.let { (it as? JsonPrimitive)?.content }
        ?: ""

    val line = (result as? JsonObject)
        ?.get("line")
        ?.let { (it as? JsonPrimitive)?.content }
        ?: ""

    val lineNum = (result as? JsonObject)
        ?.get("line_number")
        ?.let { (it as? JsonPrimitive)?.content }
        ?: ""

    val displayText: AnnotatedString = if (line.isNotBlank()) {
        val prefix = if (lineNum.isNotBlank()) "$lineNum: " else ""
        buildAnnotatedString {
            append(prefix)
            append(line)
        }
    } else {
        buildAnnotatedString { append(result.toString()) }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { if (filePath.isNotBlank()) onClick(filePath) }
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        if (filePath.isNotBlank()) {
            Text(
                text = filePath,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontFamily = FontFamily.Monospace,
                ),
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(2.dp))
        }
        Text(
            text = displayText,
            style = MaterialTheme.typography.bodySmall.copy(
                fontFamily = FontFamily.Monospace,
            ),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
    }
    HorizontalDivider(
        thickness = 0.5.dp,
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
        modifier = Modifier.padding(start = 16.dp),
    )
}

@Composable
private fun FileSearchResultItem(
    filePath: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Default.Description,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = filePath,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontFamily = FontFamily.Monospace,
            ),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
    HorizontalDivider(
        thickness = 0.5.dp,
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
        modifier = Modifier.padding(start = 48.dp),
    )
}

@Composable
private fun EmptySearchResult(message: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(160.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
