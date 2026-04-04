package me.xiaok.opencode.utils

import androidx.compose.runtime.compositionLocalOf

/**
 * CompositionLocal for accessing ErrorCollector from any composable.
 * Provided by MainActivity.
 */
val LocalErrorCollector = compositionLocalOf<ErrorCollector?> { null }
