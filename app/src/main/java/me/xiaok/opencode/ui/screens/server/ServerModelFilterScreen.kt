package me.xiaok.opencode.ui.screens.server

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.xiaok.opencode.domain.model.Model
import me.xiaok.opencode.domain.model.Provider
import me.xiaok.opencode.ui.components.common.ProviderIcon

// ---------------------------------------------------------------------------
// Route
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerModelFilterRoute(
    serverId: String,
    onNavigateBack: () -> Unit,
    viewModel: ServerModelFilterViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    ServerModelFilterScreen(
        uiState = uiState,
        onNavigateBack = onNavigateBack,
        onToggleModelVisibility = viewModel::toggleModelVisibility,
        onToggleProviderVisibility = viewModel::toggleProviderVisibility,
        onSearchQueryChanged = viewModel::setSearchQuery,
    )
}

// ---------------------------------------------------------------------------
// Stateless Screen
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerModelFilterScreen(
    uiState: ModelFilterUiState,
    onNavigateBack: () -> Unit,
    onToggleModelVisibility: (String) -> Unit,
    onToggleProviderVisibility: (String) -> Unit,
    onSearchQueryChanged: (String) -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Model Filter",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                        ),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
        bottomBar = {
            if (uiState.providers.isNotEmpty()) {
                val totalModels = uiState.providers.sumOf { provider: Provider -> provider.models.size }
                val visibleModels = maxOf(0, totalModels - uiState.hiddenModels.size)
                BottomSummary(
                    visibleCount = visibleModels,
                    totalCount = totalModels,
                )
            }
        },
    ) { innerPadding ->
        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        } else if (uiState.error != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = uiState.error!!,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                // Search bar
                SearchBar(
                    query = uiState.searchQuery,
                    onQueryChanged = onSearchQueryChanged,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )

                // Provider groups
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        horizontal = 16.dp,
                        vertical = 4.dp,
                    ),
                ) {
                    items(
                        items = uiState.providers,
                        key = { provider: Provider -> provider.id },
                    ) { provider ->
                        ProviderGroup(
                            provider = provider,
                            isHidden = provider.id in uiState.hiddenProviders,
                            hiddenModels = uiState.hiddenModels,
                            searchQuery = uiState.searchQuery,
                            onToggleProvider = onToggleProviderVisibility,
                            onToggleModel = onToggleModelVisibility,
                        )
                    }

                    item {
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Bottom Summary
// ---------------------------------------------------------------------------

@Composable
private fun BottomSummary(
    visibleCount: Int,
    totalCount: Int,
) {
    HorizontalDivider(thickness = 1.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "$visibleCount of $totalCount models visible",
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.Medium,
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ---------------------------------------------------------------------------
// Search Bar
// ---------------------------------------------------------------------------

@Composable
private fun SearchBar(
    query: String,
    onQueryChanged: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChanged,
        modifier = modifier.fillMaxWidth(),
        placeholder = {
            Text(
                text = "Search models...",
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        leadingIcon = {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        singleLine = true,
        shape = MaterialTheme.shapes.medium,
    )
}

// ---------------------------------------------------------------------------
// Provider Group (expandable)
// ---------------------------------------------------------------------------

@Composable
private fun ProviderGroup(
    provider: Provider,
    isHidden: Boolean,
    hiddenModels: Set<String>,
    searchQuery: String,
    onToggleProvider: (String) -> Unit,
    onToggleModel: (String) -> Unit,
) {
    val filteredModels = remember(provider.models, searchQuery) {
        if (searchQuery.isBlank()) {
            provider.models.values.toList()
        } else {
            val q = searchQuery.lowercase()
            provider.models.values.filter { model ->
                model.name.lowercase().contains(q) ||
                    model.id.lowercase().contains(q)
            }
        }
    }

    val visibleCount = filteredModels.count { it.id !in hiddenModels }
    val allHidden = filteredModels.isNotEmpty() && filteredModels.all { it.id in hiddenModels }

    var expanded by remember { mutableStateOf(true) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(modifier = Modifier.animateContentSize()) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ProviderIcon(
                    providerId = provider.id,
                    size = 32.dp,
                    modifier = Modifier.padding(end = 12.dp),
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = provider.name.ifEmpty { provider.id },
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.SemiBold,
                        ),
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "$visibleCount of ${filteredModels.size} visible",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(
                    imageVector = if (expanded) {
                        Icons.Default.ExpandLess
                    } else {
                        Icons.Default.ExpandMore
                    },
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Provider master toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (isHidden) "打开所有" else "关闭所有",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = !isHidden,
                    onCheckedChange = { onToggleProvider(provider.id) },
                )
            }

            // Model rows
            if (expanded && filteredModels.isNotEmpty()) {
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    thickness = 0.5.dp,
                )
                filteredModels.forEachIndexed { index, model ->
                    ModelRow(
                        model = model,
                        isVisible = model.id !in hiddenModels,
                        onToggle = { onToggleModel(model.id) },
                    )
                    if (index < filteredModels.lastIndex) {
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            thickness = 0.5.dp,
                        )
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Model Row
// ---------------------------------------------------------------------------

@Composable
private fun ModelRow(
    model: Model,
    isVisible: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = model.name.ifEmpty { model.id },
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Medium,
                ),
            )
            if (model.name.isNotEmpty() && model.name != model.id) {
                Text(
                    text = model.id,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        Switch(
            checked = isVisible,
            onCheckedChange = { onToggle() },
        )
    }
}
