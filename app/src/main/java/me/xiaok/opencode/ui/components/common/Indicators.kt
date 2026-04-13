package me.xiaok.opencode.ui.components.common

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A static colored dot indicator.
 *
 * @param color  Fill color of the dot.
 * @param size   Diameter of the dot. Callers should pick a size appropriate for
 *               their context (6 dp for inline badges, 8 dp for tool cards,
 *               10 dp for session status, etc.).
 */
@Composable
fun StatusDot(
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 8.dp,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(color),
    )
}

/**
 * A pulsing dot indicator that fades between 40 % and 100 % opacity.
 *
 * @param color  Fill color (alpha is animated).
 * @param size   Diameter of the dot. See [StatusDot] for sizing guidance.
 */
@Composable
fun PulsingDot(
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 8.dp,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulseAlpha",
    )

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(color.copy(alpha = pulseAlpha)),
    )
}

/**
 * Formats a token count into a human-readable abbreviated string.
 *
 * Examples: `850` → `"850"`, `1_200` → `"12.0k"`, `2_500_000` → `"2.5M"`
 */
fun formatTokenCount(tokens: Long): String {
    return when {
        tokens >= 1_000_000 -> "${(tokens / 100_000).toInt() / 10.0}M"
        tokens >= 1_000 -> "${(tokens / 100).toInt() / 10.0}k"
        else -> "$tokens"
    }
}
