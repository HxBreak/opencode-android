package me.xiaok.opencode.ui.components.terminal

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.isSystemInDarkTheme

/**
 * A Compose component that renders an ANSI terminal using Canvas,
 * with integrated keyboard input via a hidden BasicTextField.
 *
 * Data flow:
 * - ViewModel feeds WebSocket output into [TerminalState.processData]
 * - This composable observes [TerminalState.screen] and renders via Canvas
 * - Keyboard input is captured by a hidden BasicTextField and forwarded via [onTextInput]
 *
 * @param terminalState The [TerminalState] managing the screen buffer
 * @param modifier Compose modifier
 * @param fontSize Font size in sp (default 14sp)
 * @param onFontSizeChange Callback when font size changes via pinch-to-zoom
 * @param onTerminalResize Callback with (cols, rows) when terminal dimensions change
 * @param onTextInput Callback for keyboard input to be sent to PTY stdin
 */
@Composable
fun TerminalView(
    terminalState: TerminalState,
    modifier: Modifier = Modifier,
    fontSize: Float = 14f,
    onFontSizeChange: ((Float) -> Unit)? = null,
    onTerminalResize: ((cols: Int, rows: Int) -> Unit)? = null,
    onTextInput: ((String) -> Unit)? = null,
) {
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()

    // Focus requester for the hidden text field
    val focusRequester = remember { FocusRequester() }

    // Font size with zoom support
    var currentFontSize by remember { mutableFloatStateOf(fontSize) }

    // Current text style for monospace rendering
    val textStyle = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontSize = currentFontSize.sp,
    )

    // Measure character dimensions
    val charWidthPx = textMeasurer.measure("M", textStyle).size.width.toFloat()
    val charHeightPx = textMeasurer.measure("M", textStyle).size.height.toFloat()
    val lineHeightPx = charHeightPx * 1.2f

    // Theme-aware default colors from Material3
    val colorScheme = MaterialTheme.colorScheme
    val defaultFg = colorScheme.onSurface
    val defaultBg = colorScheme.surface
    val cursorColor = colorScheme.primary.copy(alpha = 0.7f)

    // Propagate dark/light theme to terminal state for ANSI color adaptation
    val isDarkTheme = isSystemInDarkTheme()
    LaunchedEffect(isDarkTheme) {
        terminalState.isDarkTheme = isDarkTheme
    }

    // Blinking cursor
    var cursorVisible by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        while (true) {
            cursorVisible = !cursorVisible
            kotlinx.coroutines.delay(500)
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTransformGestures { _, _, zoom, _ ->
                        val newSize = (currentFontSize * zoom).coerceIn(6f, 20f)
                        currentFontSize = newSize
                        onFontSizeChange?.invoke(newSize)
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures {
                        focusRequester.requestFocus()
                    }
                }
                .onSizeChanged { size ->
                    val newCols = (size.width / charWidthPx).toInt().coerceAtLeast(1)
                    val newRows = (size.height / lineHeightPx).toInt().coerceAtLeast(1)
                    terminalState.resize(newCols, newRows)
                    onTerminalResize?.invoke(newCols, newRows)
                }
        ) {
            val width = size.width
            val height = size.height

            // Draw background
            drawRect(color = defaultBg, size = size)

            // Read current state
            val currentScreen = terminalState.screen.value
            val currentCursorRow = terminalState.cursorRow.value
            val currentCursorCol = terminalState.cursorCol.value
            val currentScrollback = terminalState.scrollback.value
            val currentScrollOffset = terminalState.scrollOffset.value

            // Calculate scrollback lines to display
            val scrollbackVisible = if (currentScrollOffset > 0) {
                val offset = currentScrollOffset.coerceAtMost(currentScrollback.size)
                currentScrollback.takeLast(offset)
            } else {
                emptyList()
            }

            // Combined lines: scrollback (top) + visible screen
            val allLines = scrollbackVisible + currentScreen
            val maxVisibleRows = (height / lineHeightPx).toInt()

            // Render each visible line
            for (lineIdx in allLines.indices) {
                if (lineIdx >= maxVisibleRows) break

                val line = allLines[lineIdx]
                val y = lineIdx * lineHeightPx

                // Batch consecutive cells with the same style into a single drawText call
                var batchStart = 0
                var batchFg: Color? = line.firstOrNull()?.foreground
                var batchBg: Color? = line.firstOrNull()?.background
                var batchItalic = line.firstOrNull()?.italic ?: false
                var batchUnderline = line.firstOrNull()?.underline ?: false
                val batchText = StringBuilder()

                fun flushBatch() {
                    if (batchText.isEmpty()) return

                    val text = batchText.toString()
                    val fgColor = batchFg ?: defaultFg

                    // Draw custom background for this batch
                    val bg = batchBg
                    if (bg != null && bg != defaultBg) {
                        drawRect(
                            color = bg,
                            topLeft = Offset(batchStart * charWidthPx, y),
                            size = Size(text.length * charWidthPx, charHeightPx),
                        )
                    }

                    // Measure and draw text
                    val batchStyle = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = currentFontSize.sp,
                        color = fgColor,
                        fontStyle = if (batchItalic) FontStyle.Italic else null,
                    )
                    val textLayout = textMeasurer.measure(text, batchStyle)
                    drawText(
                        textLayoutResult = textLayout,
                        topLeft = Offset(batchStart * charWidthPx, y),
                        color = fgColor,
                    )

                    // Draw underline if applicable
                    if (batchUnderline) {
                        drawLine(
                            color = fgColor,
                            start = Offset(batchStart * charWidthPx, y + charHeightPx),
                            end = Offset((batchStart + text.length) * charWidthPx, y + charHeightPx),
                            strokeWidth = 1f * density.density,
                        )
                    }

                    batchText.clear()
                }

                for (colIdx in line.indices) {
                    val cell = line[colIdx]
                    val cellFg = cell.foreground
                    val cellBg = cell.background
                    val cellItalic = cell.italic
                    val cellUnderline = cell.underline

                    // Flush batch on style change
                    if (cellFg != batchFg || cellBg != batchBg ||
                        cellItalic != batchItalic || cellUnderline != batchUnderline
                    ) {
                        flushBatch()
                        batchStart = colIdx
                        batchFg = cellFg
                        batchBg = cellBg
                        batchItalic = cellItalic
                        batchUnderline = cellUnderline
                    }

                    batchText.append(cell.char)
                }
                flushBatch()
            }

            // Draw blinking cursor
            if (cursorVisible) {
                val adjustedCursorRow = currentCursorRow + scrollbackVisible.size
                if (adjustedCursorRow in 0 until maxVisibleRows) {
                    val cursorX = currentCursorCol * charWidthPx
                    val cursorY = adjustedCursorRow * lineHeightPx
                    drawRect(
                        color = cursorColor,
                        topLeft = Offset(cursorX, cursorY),
                        size = Size(charWidthPx, charHeightPx),
                        alpha = 0.7f,
                    )
                }
            }
        }

        // Hidden text field for keyboard input
        // Captures all key events and forwards them as terminal input.
        // Special keys (Enter, Tab, Backspace, Ctrl+C, etc.) are translated
        // to their ANSI/control character equivalents.
        if (onTextInput != null) {
            var textFieldValue by remember { mutableStateOf(TextFieldValue("")) }

            BasicTextField(
                value = textFieldValue,
                onValueChange = { newValue ->
                    if (newValue.text.isNotEmpty() && newValue.text != textFieldValue.text) {
                        onTextInput(newValue.text)
                    }
                    textFieldValue = TextFieldValue("")
                },
                modifier = Modifier
                    .focusRequester(focusRequester)
                    .focusable()
                    .onKeyEvent { keyEvent ->
                        if (keyEvent.type == KeyEventType.KeyDown) {
                            val handled = handleTerminalKey(keyEvent.key, onTextInput)
                            textFieldValue = TextFieldValue("")
                            handled
                        } else {
                            false
                        }
                    },
                textStyle = TextStyle(
                    color = Color.Transparent,
                    fontSize = 1.sp,
                ),
                cursorBrush = SolidColor(Color.Transparent),
            )
        }
    }
}

/**
 * Maps hardware key events to terminal control sequences.
 * Returns true if the key was handled.
 */
private fun handleTerminalKey(key: Key, onTextInput: (String) -> Unit): Boolean {
    return when (key) {
        Key.Enter -> { onTextInput("\r"); true }
        Key.Tab -> { onTextInput("\t"); true }
        Key.Backspace -> { onTextInput("\u007F"); true }  // DEL character
        Key.Delete -> { onTextInput("\u001B[3~"); true }  // ESC[3~
        Key.Escape -> { onTextInput("\u001B"); true }
        Key.DirectionUp -> { onTextInput("\u001B[A"); true }
        Key.DirectionDown -> { onTextInput("\u001B[B"); true }
        Key.DirectionRight -> { onTextInput("\u001B[C"); true }
        Key.DirectionLeft -> { onTextInput("\u001B[D"); true }
        Key.Home -> { onTextInput("\u001B[H"); true }
        Key.MoveEnd -> { onTextInput("\u001B[F"); true }
        Key.PageUp -> { onTextInput("\u001B[5~"); true }
        Key.PageDown -> { onTextInput("\u001B[6~"); true }
        else -> false
    }
}
