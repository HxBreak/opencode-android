package me.xiaok.opencode.ui.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically

/**
 * Page transition animation definitions for OpenCode navigation.
 *
 * Three transition types cover all navigation paths:
 * - **Forward/Backward Slide**: Hierarchical drill-down (Home → Projects → Sessions → Chat)
 * - **Slide Up/Down**: Overlay tool pages (Terminal, FileBrowser)
 * - **Fade Through**: Same-level content swap (Chat → sub-session Chat)
 */
object ScreenTransitions {

    // === Durations ===
    private const val DURATION_MS = 300
    private const val DURATION_FAST_MS = 250
    private const val DURATION_SLOW_MS = 350

    // === Easing curves (Material Motion) ===
    private val StandardDecelerate = CubicBezierEasing(0f, 0f, 0f, 1f)
    private val StandardAccelerate = CubicBezierEasing(0.3f, 0f, 1f, 1f)
    private val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)

    // ========================================================
    // Forward Slide — entering a deeper hierarchy level
    // ========================================================

    /** New page slides in from the right. */
    val forwardEnter: EnterTransition = slideInHorizontally(
        animationSpec = tween(DURATION_MS, easing = EmphasizedDecelerate),
        initialOffsetX = { fullWidth -> fullWidth },
    )

    /** Current page slides left + fades out slightly (depth cue). */
    val forwardExit: ExitTransition = slideOutHorizontally(
        animationSpec = tween(DURATION_MS, easing = StandardAccelerate),
        targetOffsetX = { fullWidth -> -fullWidth / 3 },
    ) + fadeOut(
        animationSpec = tween(DURATION_MS, easing = StandardAccelerate),
        targetAlpha = 0.7f,
    )

    // ========================================================
    // Backward Slide — returning to the previous level
    // ========================================================

    /** Previous page slides back in from the left + fades in. */
    val backwardEnter: EnterTransition = slideInHorizontally(
        animationSpec = tween(DURATION_MS, easing = EmphasizedDecelerate),
        initialOffsetX = { fullWidth -> -fullWidth / 3 },
    ) + fadeIn(
        animationSpec = tween(DURATION_MS, easing = LinearEasing),
    )

    /** Current page slides out to the right. */
    val backwardExit: ExitTransition = slideOutHorizontally(
        animationSpec = tween(DURATION_MS, easing = StandardAccelerate),
        targetOffsetX = { fullWidth -> fullWidth },
    )

    // ========================================================
    // Slide Up — overlay tool pages (Terminal, FileBrowser)
    // ========================================================

    /** New page slides up from the bottom. */
    val slideUpEnter: EnterTransition = slideInVertically(
        animationSpec = tween(DURATION_SLOW_MS, easing = EmphasizedDecelerate),
        initialOffsetY = { fullHeight -> fullHeight },
    )

    /** Overlay page slides back down. */
    val slideDownExit: ExitTransition = slideOutVertically(
        animationSpec = tween(DURATION_MS, easing = StandardAccelerate),
        targetOffsetY = { fullHeight -> fullHeight },
    )

    // ========================================================
    // Fade Through — same-level content replacement
    // ========================================================

    /** Incoming page fades in + subtle scale. */
    val fadeThroughEnter: EnterTransition = fadeIn(
        animationSpec = tween(DURATION_FAST_MS, delayMillis = 75, easing = LinearEasing),
    ) + scaleIn(
        animationSpec = tween(DURATION_FAST_MS, delayMillis = 75, easing = StandardDecelerate),
        initialScale = 0.95f,
    )

    /** Outgoing page fades out. */
    val fadeThroughExit: ExitTransition = fadeOut(
        animationSpec = tween(DURATION_FAST_MS, easing = LinearEasing),
    )
}
