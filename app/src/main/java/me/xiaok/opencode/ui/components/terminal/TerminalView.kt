package me.xiaok.opencode.ui.components.terminal

import android.content.ClipData
import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * A Compose component that renders an ANSI terminal using Canvas,
 * with direct IME input via an invisible BasicTextField overlay,
 * text selection, and copy/paste support.
 *
 * Data flow:
 * - ViewModel feeds WebSocket output into [TerminalState.processData]
 * - This composable observes [TerminalState.screen] and renders via Canvas
 * - IME input is captured by an invisible BasicTextField and forwarded via [onTextInput]
 * - Long-press starts text selection; floating toolbar provides copy/paste actions
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
    val context = LocalContext.current
    val textMeasurer = rememberTextMeasurer()
    val focusRequester = remember { FocusRequester() }
    val clipboardManager = remember {
        context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
    }

    // Font size with zoom support
    var currentFontSize by remember { mutableFloatStateOf(fontSize) }

    // Selection state (grid coordinates: x=col, y=row relative to visible content)
    var selectionStart by remember { mutableStateOf<Offset?>(null) }
    var selectionEnd by remember { mutableStateOf<Offset?>(null) }
    var showToolbar by remember { mutableStateOf(false) }

    // Track canvas pixel size for toolbar clamping
    var canvasSizePx by remember { mutableStateOf(androidx.compose.ui.geometry.Size.Zero) }

    // Invisible text field state
    // Following OC Remote's proven pattern: keep TextFieldValue intact (don't clear after each key).
    // This lets onValueChange diff old vs new to detect both character input and Backspace deletion.
    var textFieldValue by remember { mutableStateOf(TextFieldValue("")) }

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
    val selectionHighlightColor = colorScheme.primary.copy(alpha = 0.3f)

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

    // Helper: convert pixel position to grid position
    fun touchToGrid(touch: Offset): Offset {
        val col = (touch.x / charWidthPx).toInt().coerceAtLeast(0)
        val row = (touch.y / lineHeightPx).toInt().coerceAtLeast(0)
        return Offset(col.toFloat(), row.toFloat())
    }

    // Helper: get visible lines (scrollback + screen) at current scroll offset
    fun getVisibleLines(): List<List<TerminalCell>> {
        val scrollback = terminalState.scrollback.value
        val scrollOffset = terminalState.scrollOffset.value
        val scrollbackVisible = if (scrollOffset > 0) {
            val offset = scrollOffset.coerceAtMost(scrollback.size)
            scrollback.takeLast(offset)
        } else {
            emptyList()
        }
        return scrollbackVisible + terminalState.screen.value
    }

    // Root is Box so Canvas and invisible BasicTextField can overlap
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
                // Combined tap / long-press / selection-drag / scroll gesture handler
                .pointerInput(currentFontSize) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val downPos = down.position
                        val downTime = System.currentTimeMillis()
                        var didLongPress = false
                        var didMove = false
                        var lastMovePos = downPos
                        var scrollAccum = 0f

                        // Loop while finger is down
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull() ?: break
                            if (!change.pressed) {
                                // Finger lifted
                                if (!didLongPress && !didMove) {
                                    // Tap: clear selection, focus input
                                    selectionStart = null
                                    selectionEnd = null
                                    showToolbar = false
                                    focusRequester.requestFocus()
                                } else if (didLongPress) {
                                    // Selection finalized — show toolbar
                                    showToolbar = true
                                }
                                change.consume()
                                break
                            }

                            val elapsed = System.currentTimeMillis() - downTime
                            val currentPos = change.position

                            if (!didLongPress && !didMove) {
                                // Not yet decided if this is tap, long-press, or scroll
                                val distance = (currentPos - downPos).getDistance()
                                if (distance > 20f) {
                                    // Significant move before long-press → scroll mode
                                    didMove = true
                                    lastMovePos = currentPos
                                } else if (elapsed > 400L) {
                                    // Long press detected — start selection
                                    didLongPress = true
                                    val gridPos = touchToGrid(downPos)
                                    selectionStart = gridPos
                                    selectionEnd = gridPos
                                    showToolbar = false
                                }
                            }

                            if (didLongPress) {
                                // Extend selection to current drag position
                                selectionEnd = touchToGrid(currentPos)
                                change.consume()
                            } else if (didMove) {
                                // Scroll scrollback: track incremental drag
                                val deltaY = currentPos.y - lastMovePos.y
                                lastMovePos = currentPos
                                scrollAccum += deltaY
                                val linesToScroll = (scrollAccum / lineHeightPx).toInt()
                                if (linesToScroll != 0) {
                                    scrollAccum -= linesToScroll * lineHeightPx
                                    val currentOffset = terminalState.scrollOffset.value
                                    val newOffset = (currentOffset - linesToScroll)
                                        .coerceIn(0, terminalState.scrollback.value.size)
                                    terminalState.setScrollOffset(newOffset)
                                }
                                change.consume()
                            }
                        }
                    }
                }
                .onSizeChanged { size ->
                    canvasSizePx = Size(size.width.toFloat(), size.height.toFloat())
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

            // Draw selection highlight (BEFORE text so text appears on top)
            val selStart = selectionStart
            val selEnd = selectionEnd
            if (selStart != null && selEnd != null) {
                val normalized = normalizeSelection(selStart, selEnd)
                val startRow = normalized.first.y.toInt().coerceIn(0, allLines.size - 1)
                val endRow = normalized.second.y.toInt().coerceIn(0, allLines.size - 1)

                for (row in startRow..endRow) {
                    if (row >= maxVisibleRows) break
                    val line = allLines.getOrElse(row) { emptyList() }
                    val y = row * lineHeightPx

                    val colStart = if (row == startRow) normalized.first.x.toInt() else 0
                    val colEnd = if (row == endRow) normalized.second.x.toInt()
                        .coerceAtMost(line.size) else line.size

                    if (colEnd > colStart) {
                        drawRect(
                            color = selectionHighlightColor,
                            topLeft = Offset(colStart * charWidthPx, y),
                            size = Size((colEnd - colStart) * charWidthPx, charHeightPx),
                        )
                    }
                }
            }

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

        // Invisible BasicTextField overlay — captures IME input and hardware key events.
        // Pattern from OC Remote: preserve TextFieldValue so onValueChange can diff
        // old vs new text to detect both additions and deletions (Backspace).
        if (onTextInput != null) {
            BasicTextField(
                value = textFieldValue,
                onValueChange = { next ->
                    val old = textFieldValue.text
                    val now = next.text
                    val delta = when {
                        now.startsWith(old) -> now.drop(old.length)
                        old.startsWith(now) -> "\u007F".repeat((old.length - now.length).coerceAtLeast(0))
                        else -> now
                    }
                    if (delta.isNotEmpty()) {
                        val mapped = delta
                            .replace("\r\n", "\r")
                            .replace('\n', '\r')
                        onTextInput(mapped)
                    }
                    // Keep IME context stable by preserving value with cursor at end
                    textFieldValue = next.copy(selection = TextRange(next.text.length))
                },
                modifier = Modifier
                    .size(1.dp)
                    .focusRequester(focusRequester)
                    .onPreviewKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        handleTerminalKey(event.key, onTextInput)
                    },
                textStyle = TextStyle(
                    color = Color.Transparent,
                    fontSize = 1.sp,
                ),
                cursorBrush = SolidColor(Color.Transparent),
                decorationBox = {},
            )
        }

        // Floating selection toolbar
        val selStart = selectionStart
        val selEnd = selectionEnd
        if (showToolbar && selStart != null && selEnd != null) {
            val normalized = normalizeSelection(selStart, selEnd)
            val midRow = ((normalized.first.y + normalized.second.y) / 2).toInt()
            val midCol = ((normalized.first.x + normalized.second.x) / 2).toInt()

            // Position toolbar above the selection midpoint
            val toolbarX = (midCol * charWidthPx)
                .coerceIn(0f, (canvasSizePx.width - 240f).coerceAtLeast(0f))
            val toolbarY = (midRow * lineHeightPx - 48f * density.density)
                .coerceAtLeast(0f)

            Surface(
                modifier = Modifier
                    .offset { IntOffset(toolbarX.toInt(), toolbarY.toInt()) }
                    .wrapContentSize(),
                shape = RoundedCornerShape(8.dp),
                tonalElevation = 3.dp,
                shadowElevation = 4.dp,
            ) {
                Row(modifier = Modifier.padding(horizontal = 4.dp)) {
                    TextButton(onClick = {
                        // Copy selected text
                        val visibleLines = getVisibleLines()
                        val text = getSelectedText(
                            visibleLines,
                            normalized.first.y.toInt(),
                            normalized.first.x.toInt(),
                            normalized.second.y.toInt(),
                            normalized.second.x.toInt(),
                        )
                        if (text.isNotEmpty()) {
                            clipboardManager.setPrimaryClip(
                                ClipData.newPlainText("terminal", text)
                            )
                        }
                        // Clear selection after copy
                        selectionStart = null
                        selectionEnd = null
                        showToolbar = false
                        focusRequester.requestFocus()
                    }) {
                        Text("Copy", style = MaterialTheme.typography.labelMedium)
                    }
                    TextButton(onClick = {
                        // Paste from clipboard
                        val clip = clipboardManager.primaryClip
                        val pasteText = clip?.getItemAt(0)?.text?.toString()
                        if (!pasteText.isNullOrEmpty()) {
                            onTextInput?.invoke(pasteText)
                        }
                        selectionStart = null
                        selectionEnd = null
                        showToolbar = false
                        focusRequester.requestFocus()
                    }) {
                        Text("Paste", style = MaterialTheme.typography.labelMedium)
                    }
                    TextButton(onClick = {
                        // Select all visible content
                        val visibleLines = getVisibleLines()
                        val maxVisibleRows = (canvasSizePx.height / lineHeightPx).toInt()
                        val totalRows = minOf(visibleLines.size, maxVisibleRows)
                        if (totalRows > 0 && visibleLines.isNotEmpty()) {
                            val lastLineLen = visibleLines[minOf(totalRows - 1, visibleLines.size - 1)].size
                            selectionStart = Offset(0f, 0f)
                            selectionEnd = Offset(
                                lastLineLen.toFloat(),
                                (totalRows - 1).toFloat()
                            )
                        }
                    }) {
                        Text("Select All", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}

/**
 * Normalize selection so first is top-left and second is bottom-right.
 */
private fun normalizeSelection(start: Offset, end: Offset): Pair<Offset, Offset> {
    return if (start.y < end.y || (start.y == end.y && start.x <= end.x)) {
        Pair(start, end)
    } else {
        Pair(end, start)
    }
}

/**
 * Extract selected text from visible terminal lines.
 */
private fun getSelectedText(
    visibleLines: List<List<TerminalCell>>,
    startRow: Int,
    startCol: Int,
    endRow: Int,
    endCol: Int,
): String {
    val sb = StringBuilder()
    val clampedStartRow = startRow.coerceIn(0, visibleLines.size - 1)
    val clampedEndRow = endRow.coerceIn(0, visibleLines.size - 1)

    for (row in clampedStartRow..clampedEndRow) {
        val line = visibleLines.getOrElse(row) { emptyList() }
        val colStart = if (row == clampedStartRow) startCol.coerceIn(0, line.size) else 0
        val colEnd = if (row == clampedEndRow) endCol.coerceIn(0, line.size) else line.size

        if (colEnd > colStart && line.isNotEmpty()) {
            val text = line.subList(colStart, colEnd)
                .joinToString("") { it.char.toString() }
                .trimEnd()
            sb.append(text)
        }
        if (row < clampedEndRow) sb.append("\n")
    }
    return sb.toString()
}

/**
 * Maps hardware key events to terminal control sequences.
 * Returns true if the key was handled.
 */
private fun handleTerminalKey(key: Key, onTextInput: (String) -> Unit): Boolean {
    return when (key) {
        Key.Enter -> { onTextInput("\r"); true }
        Key.Tab -> { onTextInput("\t"); true }
        Key.Backspace -> { onTextInput("\u007F"); true }  // DEL (0x7F) — matches OC Remote
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
