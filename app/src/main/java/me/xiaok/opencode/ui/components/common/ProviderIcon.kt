package me.xiaok.opencode.ui.components.common

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// ---------------------------------------------------------------------------
// Provider Brand Data
// ---------------------------------------------------------------------------

private data class ProviderBrand(
    val initials: String,
    val color: Color,
)

private val PROVIDER_MAP = mapOf(
    "openai" to ProviderBrand("OA", Color(0xFF10A37F)),
    "anthropic" to ProviderBrand("AN", Color(0xFFD4A574)),
    "google" to ProviderBrand("G", Color(0xFF4285F4)),
    "azure" to ProviderBrand("AZ", Color(0xFF0078D4)),
    "groq" to ProviderBrand("GQ", Color(0xFFF55036)),
    "ollama" to ProviderBrand("OL", Color(0xFF000000)),
    "together" to ProviderBrand("TG", Color(0xFF0F9D58)),
    "fireworks" to ProviderBrand("FW", Color(0xFF6B21A8)),
    "mistral" to ProviderBrand("MI", Color(0xFFF70000)),
    "deepseek" to ProviderBrand("DS", Color(0xFF4D6BFE)),
    "xai" to ProviderBrand("xA", Color(0xFF000000)),
    "cohere" to ProviderBrand("CH", Color(0xFF39D353)),
    "bedrock" to ProviderBrand("BR", Color(0xFFFF9900)),
    "vertex" to ProviderBrand("VX", Color(0xFF4285F4)),
)

// ---------------------------------------------------------------------------
// Provider Icon Composable
// ---------------------------------------------------------------------------

@Composable
fun ProviderIcon(
    providerId: String,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
) {
    val brand = PROVIDER_MAP[providerId.lowercase()]
    val initials = brand?.initials ?: providerId.take(2).uppercase()
    val backgroundColor = brand?.color ?: MaterialTheme.colorScheme.primaryContainer

    Surface(
        modifier = modifier.size(size),
        shape = CircleShape,
        color = backgroundColor,
    ) {
        Text(
            text = initials,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
            ),
            textAlign = TextAlign.Center,
            modifier = Modifier.size(size),
        )
    }
}
