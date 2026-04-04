package me.xiaok.opencode.ui.screens.iconpreview

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import me.xiaok.opencode.R

// ---------------------------------------------------------------------------
// Icon data
// ---------------------------------------------------------------------------

private data class IconStyle(
    val id: String,
    val displayName: String,
    val description: String,
    val drawableRes: Int,
)

private val IconStyles = listOf(
    IconStyle(
        id = "minimal_tech",
        displayName = "Minimal Tech",
        description = "Dark background with glowing geometric code bracket, clean sharp lines",
        drawableRes = R.drawable.icon_preview_minimal_tech,
    ),
    IconStyle(
        id = "glassmorphism",
        displayName = "Glassmorphism",
        description = "Purple-blue gradient with frosted glass code symbol, light refraction effect",
        drawableRes = R.drawable.icon_preview_glassmorphism,
    ),
    IconStyle(
        id = "ai_brain",
        displayName = "AI Brain",
        description = "Circuit board brain with neural connections, cyan and purple accent lights",
        drawableRes = R.drawable.icon_preview_ai_brain,
    ),
    IconStyle(
        id = "typographic",
        displayName = "Typographic",
        description = "Bold OC letters with code bracket elements on dark background",
        drawableRes = R.drawable.icon_preview_typographic,
    ),
)

// ---------------------------------------------------------------------------
// Route (no ViewModel needed — purely visual debug page)
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IconPreviewRoute(
    onNavigateBack: () -> Unit,
) {
    var selectedStyle by remember { mutableStateOf<String?>(null) }
    var previewStyle by remember { mutableStateOf<IconStyle?>(null) }

    IconPreviewScreen(
        selectedStyle = selectedStyle,
        onNavigateBack = onNavigateBack,
        onSelectStyle = { selectedStyle = it },
        onPreviewStyle = { previewStyle = it },
    )

    // Full-screen preview dialog
    previewStyle?.let { style ->
        IconPreviewDialog(
            iconStyle = style,
            onDismiss = { previewStyle = null },
            isSelected = selectedStyle == style.id,
            onSelect = {
                selectedStyle = style.id
                previewStyle = null
            },
        )
    }
}

// ---------------------------------------------------------------------------
// Main Screen
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IconPreviewScreen(
    selectedStyle: String?,
    onNavigateBack: () -> Unit,
    onSelectStyle: (String) -> Unit,
    onPreviewStyle: (IconStyle) -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Icon Preview",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
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
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
        ) {
            // Header info
            Text(
                text = "AI-generated icon designs for OpenCode. Tap to preview, select your preferred style.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 16.dp),
            )

            // Icon grid (2 columns)
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(items = IconStyles, key = { it.id }) { style ->
                    IconStyleCard(
                        iconStyle = style,
                        isSelected = selectedStyle == style.id,
                        onSelect = { onSelectStyle(style.id) },
                        onPreview = { onPreviewStyle(style) },
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

// ---------------------------------------------------------------------------
// Icon Style Card
// ---------------------------------------------------------------------------

@Composable
private fun IconStyleCard(
    iconStyle: IconStyle,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onPreview: () -> Unit,
) {
    val borderColor = if (isSelected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outlineVariant
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onPreview,
            ),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        shape = MaterialTheme.shapes.medium,
        border = androidx.compose.foundation.BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = borderColor,
        ),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Icon preview — simulate app icon with rounded corners
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(24.dp))
                    .shadow(4.dp, RoundedCornerShape(24.dp)),
            ) {
                androidx.compose.foundation.Image(
                    painter = painterResource(id = iconStyle.drawableRes),
                    contentDescription = iconStyle.displayName,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )

                // Selected badge
                if (isSelected) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Selected",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Style name
            Text(
                text = iconStyle.displayName,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.SemiBold,
                ),
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(2.dp))

            // Description
            Text(
                text = iconStyle.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 2,
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Select button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                RadioButton(
                    selected = isSelected,
                    onClick = onSelect,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = if (isSelected) "Selected" else "Select",
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Full-screen Preview Dialog
// ---------------------------------------------------------------------------

@Composable
private fun IconPreviewDialog(
    iconStyle: IconStyle,
    onDismiss: () -> Unit,
    isSelected: Boolean,
    onSelect: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface,
            ),
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Close button row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = iconStyle.displayName,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                        ),
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Large icon preview — simulate different contexts
                // 1. Full square icon
                Box(
                    modifier = Modifier
                        .size(200.dp)
                        .clip(RoundedCornerShape(40.dp))
                        .shadow(8.dp, RoundedCornerShape(40.dp)),
                ) {
                    androidx.compose.foundation.Image(
                        painter = painterResource(id = iconStyle.drawableRes),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 2. Simulated home screen row — icon + app name
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerLow)
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .shadow(2.dp, RoundedCornerShape(12.dp)),
                    ) {
                        androidx.compose.foundation.Image(
                            painter = painterResource(id = iconStyle.drawableRes),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            text = "OpenCode",
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontWeight = FontWeight.SemiBold,
                            ),
                        )
                        Text(
                            text = "AI Coding Assistant",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Description
                Text(
                    text = iconStyle.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Select button
                FilledTonalButton(
                    onClick = onSelect,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(
                        imageVector = if (isSelected) Icons.Default.Check else Icons.Default.Image,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp)
                    )
                    Text(if (isSelected) "Currently Selected" else "Select This Icon")
                }
            }
        }
    }
}
