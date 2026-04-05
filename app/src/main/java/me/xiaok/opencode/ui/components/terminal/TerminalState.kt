package me.xiaok.opencode.ui.components.terminal

import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Manages the terminal screen buffer and parses ANSI escape sequences.
 * Implements basic VT100 support: colors, cursor movement, clear, line operations.
 *
 * The terminal is modeled as a grid of [TerminalCell] with configurable dimensions.
 * Incoming bytes are processed through [processData] which handles:
 * - Plain text
 * - CSI (Control Sequence Introducer) sequences: \u001B[ ... <letter>
 * - SGR (Select Graphic Rendition) for colors/styles
 * - Cursor movement: A/B/C/D (up/down/right/left), H (position), J (clear), K (erase line)
 * - Line feed, carriage return, tab, backspace
 */
class TerminalState(
    initialCols: Int = 80,
    initialRows: Int = 24,
    maxScrollback: Int = 10000,
) {
    private val _cols = MutableStateFlow(initialCols)
    val cols: StateFlow<Int> = _cols.asStateFlow()

    private val _rows = MutableStateFlow(initialRows)
    val rows: StateFlow<Int> = _rows.asStateFlow()

    private val _cursorRow = MutableStateFlow(0)
    val cursorRow: StateFlow<Int> = _cursorRow.asStateFlow()

    private val _cursorCol = MutableStateFlow(0)
    val cursorCol: StateFlow<Int> = _cursorCol.asStateFlow()

    private val _screen = MutableStateFlow<List<List<TerminalCell>>>(emptyList())
    val screen: StateFlow<List<List<TerminalCell>>> = _screen.asStateFlow()

    private val _scrollback = MutableStateFlow<List<List<TerminalCell>>>(emptyList())
    val scrollback: StateFlow<List<List<TerminalCell>>> = _scrollback.asStateFlow()

    private val _scrollOffset = MutableStateFlow(0)
    val scrollOffset: StateFlow<Int> = _scrollOffset.asStateFlow()

    /** Total lines available for display (screen + scrollback) */
    val totalLines: Int get() = _scrollback.value.size + _screen.value.size

    private var maxScrollbackLines = maxScrollback

    // Current style state
    private var currentFg: Color? = null
    private var currentBg: Color? = null
    private var currentBold = false
    private var currentItalic = false
    private var currentUnderline = false

    /**
     * Whether the terminal is using a dark theme.
     * When false, ANSI color resolution swaps black/white and adjusts brightness
     * so text remains readable on a light background.
     * Defaults to true (dark theme) for backward compatibility.
     */
    var isDarkTheme: Boolean = true

    // ANSI escape sequence parsing state
    private var escapeBuffer = StringBuilder()
    private var inEscape = false

    init {
        resize(initialCols, initialRows)
    }

    /**
     * Resize the terminal grid.
     * Existing content is preserved where possible.
     */
    fun resize(newCols: Int, newRows: Int) {
        val oldScreen = _screen.value
        val oldCursorRow = _cursorRow.value
        val oldCursorCol = _cursorCol.value

        val newScreen = mutableListOf<List<TerminalCell>>()

        // Copy existing rows, adjusting width
        for (i in 0 until minOf(newRows, oldScreen.size)) {
            val oldRow = oldScreen[i]
            val newRow = if (newCols <= oldRow.size) {
                oldRow.subList(0, newCols)
            } else {
                oldRow + List(newCols - oldRow.size) { TerminalCell() }
            }
            newScreen.add(newRow)
        }

        // Add new empty rows if needed
        while (newScreen.size < newRows) {
            newScreen.add(List(newCols) { TerminalCell() })
        }

        _cols.value = newCols
        _rows.value = newRows
        _screen.value = newScreen

        // Clamp cursor
        _cursorRow.value = oldCursorRow.coerceIn(0, newRows - 1)
        _cursorCol.value = oldCursorCol.coerceIn(0, newCols - 1)
    }

    /**
     * Process incoming terminal data (ANSI-encoded text).
     * @param data Raw string from WebSocket
     */
    fun processData(data: String) {
        if (data.isEmpty()) return

        for (char in data) {
            if (inEscape) {
                processEscapeChar(char)
            } else {
                processChar(char)
            }
        }
    }

    private fun processChar(char: Char) {
        when (char.code) {
            27 -> { // ESC
                inEscape = true
                escapeBuffer.clear()
                escapeBuffer.append(char)
            }
            10 -> lineFeed()       // LF
            13 -> carriageReturn() // CR
            8 -> backspace()       // BS
            9 -> tab()             // TAB
            7 -> { /* BEL - ignore */ }
            in 32..126, in 128..255 -> printChar(char)
            else -> {
                // Printable or high-byte character
                printChar(char)
            }
        }
    }

    private fun processEscapeChar(char: Char) {
        escapeBuffer.append(char)

        val buf = escapeBuffer.toString()

        when {
            // OSC sequence: ESC ] ... BEL(0x07) or ST(ESC \)
            // Used for: window title, clipboard, hyperlink, etc.
            // We don't need the data, just consume and discard.
            buf.startsWith("\u001B]") -> {
                when {
                    // Terminated by BEL (0x07)
                    char.code == 7 -> resetEscape()
                    // Terminated by ST: ESC \
                    buf.endsWith("\u001B\\") -> resetEscape()
                    // Safety: discard if buffer grows too large
                    buf.length > 512 -> resetEscape()
                }
            }

            // CSI sequence: ESC [ ... <final byte>
            buf.startsWith("\u001B[") -> {
                val finalChar = buf.last()
                if (finalChar in 'A'..'Z' || finalChar in 'a'..'z' || finalChar == '@' || finalChar == '`') {
                    processCsiSequence(buf)
                    resetEscape()
                }
            }
            // ESC c — reset
            buf == "\u001Bc" -> {
                clearScreen()
                resetEscape()
            }
            // ESC 7 / ESC 8 — save/restore cursor (not implemented, just consume)
            buf == "\u001B7" || buf == "\u001B8" -> {
                resetEscape()
            }
            // ESC ( <charset> — designate charset (consume)
            buf.length == 3 && buf[1] == '(' -> {
                resetEscape()
            }
            // ESC ) <charset> — designate charset (consume)
            buf.length == 3 && buf[1] == ')' -> {
                resetEscape()
            }
            // ESC = / ESC > — alternate keypad (consume)
            buf == "\u001B=" || buf == "\u001B>" -> {
                resetEscape()
            }
            // Unknown escape, reset if buffer is getting too long
            buf.length > 32 -> {
                resetEscape()
            }
        }
    }

    private fun resetEscape() {
        inEscape = false
        escapeBuffer.clear()
    }

    /**
     * Process a complete CSI (Control Sequence Introducer) sequence.
     * Format: ESC [ <params> <final byte>
     */
    private fun processCsiSequence(seq: String) {
        // Strip "ESC[" prefix and final byte
        val paramStr = seq.substring(2, seq.length - 1)
        val command = seq.last()

        val params = if (paramStr.isEmpty()) {
            emptyList()
        } else {
            paramStr.split(";").mapNotNull {
                it.toIntOrNull()
            }
        }

        when (command) {
            // Cursor movement
            'A' -> cursorUp(params.getOrElse(0) { 1 })
            'B' -> cursorDown(params.getOrElse(0) { 1 })
            'C' -> cursorRight(params.getOrElse(0) { 1 })
            'D' -> cursorLeft(params.getOrElse(0) { 1 })
            'H', 'f' -> cursorPosition(
                params.getOrElse(0) { 1 } - 1,
                params.getOrElse(1) { 1 } - 1
            )

            // Erase
            'J' -> eraseDisplay(params.getOrElse(0) { 0 })
            'K' -> eraseLine(params.getOrElse(0) { 0 })

            // SGR — Select Graphic Rendition (colors/styles)
            'm' -> processSgr(params)

            // Line insert/delete
            'L' -> insertLines(params.getOrElse(0) { 1 })
            'M' -> deleteLines(params.getOrElse(0) { 1 })
            'P' -> deleteChars(params.getOrElse(0) { 1 })
            '@' -> insertChars(params.getOrElse(0) { 1 })

            // Scroll
            'S' -> scrollUp(params.getOrElse(0) { 1 })
            'T' -> scrollDown(params.getOrElse(0) { 1 })

            // Save/restore cursor
            's' -> { /* save cursor — not implemented */ }
            'u' -> { /* restore cursor — not implemented */ }

            else -> {
                // Unknown CSI sequence — ignore
            }
        }
    }

    // --- Cursor operations ---

    private fun cursorUp(n: Int) {
        _cursorRow.update { (it - n).coerceAtLeast(0) }
    }

    private fun cursorDown(n: Int) {
        _cursorRow.update { (it + n).coerceAtMost(_rows.value - 1) }
    }

    private fun cursorRight(n: Int) {
        _cursorCol.update { (it + n).coerceAtMost(_cols.value - 1) }
    }

    private fun cursorLeft(n: Int) {
        _cursorCol.update { (it - n).coerceAtLeast(0) }
    }

    private fun cursorPosition(row: Int, col: Int) {
        _cursorRow.value = row.coerceIn(0, _rows.value - 1)
        _cursorCol.value = col.coerceIn(0, _cols.value - 1)
    }

    // --- Text output ---

    private fun printChar(char: Char) {
        val row = _cursorRow.value
        val col = _cursorCol.value
        val screen = _screen.value.toMutableList()

        if (row !in screen.indices) return

        val line = screen[row].toMutableList()
        if (col < line.size) {
            line[col] = TerminalCell(
                char = char,
                foreground = currentFg,
                background = currentBg,
                bold = currentBold,
                italic = currentItalic,
                underline = currentUnderline,
            )
            screen[row] = line
            _screen.value = screen
        }

        _cursorCol.value = (col + 1).coerceAtMost(_cols.value - 1)
    }

    private fun lineFeed() {
        val row = _cursorRow.value
        if (row >= _rows.value - 1) {
            // At bottom — scroll up
            scrollUp(1)
        } else {
            _cursorRow.value = row + 1
        }
    }

    private fun carriageReturn() {
        _cursorCol.value = 0
    }

    private fun backspace() {
        _cursorCol.update { (it - 1).coerceAtLeast(0) }
    }

    private fun tab() {
        val col = _cursorCol.value
        val nextTab = ((col / 8) + 1) * 8
        _cursorCol.value = nextTab.coerceAtMost(_cols.value - 1)
    }

    // --- Erase operations ---

    private fun eraseDisplay(mode: Int) {
        when (mode) {
            0 -> {
                // Clear from cursor to end of screen
                val screen = _screen.value.toMutableList()
                val row = _cursorRow.value
                val col = _cursorCol.value

                // Clear rest of current line
                if (row in screen.indices) {
                    val line = screen[row].toMutableList()
                    for (c in col until line.size) {
                        line[c] = TerminalCell()
                    }
                    screen[row] = line
                }

                // Clear all lines below
                for (r in row + 1 until screen.size) {
                    screen[r] = List(_cols.value) { TerminalCell() }
                }

                _screen.value = screen
            }
            1 -> {
                // Clear from start to cursor
                val screen = _screen.value.toMutableList()
                val row = _cursorRow.value
                val col = _cursorCol.value

                // Clear all lines above
                for (r in 0 until row) {
                    screen[r] = List(_cols.value) { TerminalCell() }
                }

                // Clear current line up to cursor
                if (row in screen.indices) {
                    val line = screen[row].toMutableList()
                    for (c in 0..col.coerceAtMost(line.size - 1)) {
                        line[c] = TerminalCell()
                    }
                    screen[row] = line
                }

                _screen.value = screen
            }
            2 -> clearScreen()
        }
    }

    private fun eraseLine(mode: Int) {
        val screen = _screen.value.toMutableList()
        val row = _cursorRow.value
        if (row !in screen.indices) return

        val line = screen[row].toMutableList()
        when (mode) {
            0 -> {
                // Clear from cursor to end of line
                for (c in _cursorCol.value until line.size) {
                    line[c] = TerminalCell()
                }
            }
            1 -> {
                // Clear from start to cursor
                for (c in 0.._cursorCol.value.coerceAtMost(line.size - 1)) {
                    line[c] = TerminalCell()
                }
            }
            2 -> {
                // Clear entire line
                for (c in line.indices) {
                    line[c] = TerminalCell()
                }
            }
        }
        screen[row] = line
        _screen.value = screen
    }

    // --- Scroll operations ---

    private fun scrollUp(n: Int) {
        val screen = _screen.value
        val scrollback = _scrollback.value.toMutableList()

        // Move scrolled-off lines to scrollback
        for (i in 0 until n.coerceAtMost(screen.size)) {
            scrollback.add(screen[i])
        }

        // Trim scrollback if it exceeds max
        while (scrollback.size > maxScrollbackLines) {
            scrollback.removeAt(0)
        }

        _scrollback.value = scrollback

        // Shift remaining lines up, add empty at bottom
        val newScreen = screen.drop(n) +
                List(n) { List(_cols.value) { TerminalCell() } }
        _screen.value = newScreen.take(_rows.value)
    }

    private fun scrollDown(n: Int) {
        val screen = _screen.value.toMutableList()
        // Insert empty lines at top, remove from bottom
        repeat(n) {
            if (screen.isNotEmpty()) {
                screen.add(0, List(_cols.value) { TerminalCell() })
                screen.removeAt(screen.lastIndex)
            }
        }
        _screen.value = screen
    }

    // --- Line/char insert/delete ---

    private fun insertLines(n: Int) {
        val screen = _screen.value.toMutableList()
        val row = _cursorRow.value
        repeat(n) {
            if (row in screen.indices) {
                screen.add(row, List(_cols.value) { TerminalCell() })
                screen.removeAt(screen.lastIndex)
            }
        }
        _screen.value = screen
    }

    private fun deleteLines(n: Int) {
        val screen = _screen.value.toMutableList()
        val row = _cursorRow.value
        repeat(n) {
            if (row in screen.indices && screen.size > row) {
                screen.removeAt(row)
                screen.add(List(_cols.value) { TerminalCell() })
            }
        }
        _screen.value = screen
    }

    private fun deleteChars(n: Int) {
        val screen = _screen.value.toMutableList()
        val row = _cursorRow.value
        val col = _cursorCol.value
        if (row !in screen.indices) return

        val line = screen[row].toMutableList()
        repeat(n) {
            if (col < line.size) {
                line.removeAt(col)
                line.add(TerminalCell())
            }
        }
        screen[row] = line
        _screen.value = screen
    }

    private fun insertChars(n: Int) {
        val screen = _screen.value.toMutableList()
        val row = _cursorRow.value
        val col = _cursorCol.value
        if (row !in screen.indices) return

        val line = screen[row].toMutableList()
        repeat(n) {
            if (line.size < _cols.value + n) {
                line.add(col, TerminalCell())
                if (line.size > _cols.value) {
                    line.removeAt(line.lastIndex)
                }
            }
        }
        screen[row] = line
        _screen.value = screen
    }

    // --- SGR (colors and styles) ---

    private fun processSgr(params: List<Int>) {
        if (params.isEmpty()) {
            resetStyle()
            return
        }

        var i = 0
        while (i < params.size) {
            when (params[i]) {
                0 -> resetStyle()
                1 -> currentBold = true
                3 -> currentItalic = true
                4 -> currentUnderline = true
                22 -> currentBold = false
                23 -> currentItalic = false
                24 -> currentUnderline = false

                // Standard foreground colors (30-37)
                in 30..37 -> currentFg = themedStandardColor(params[i] - 30)
                // Standard background colors (40-47)
                in 40..47 -> currentBg = themedStandardColor(params[i] - 40)

                // Bright foreground (90-97)
                in 90..97 -> currentFg = themedBrightColor(params[i] - 90)
                // Bright background (100-107)
                in 100..107 -> currentBg = themedBrightColor(params[i] - 100)

                // 256-color foreground: 38;5;n
                38 -> {
                    if (i + 2 < params.size && params[i + 1] == 5) {
                        currentFg = themedColor256(params[i + 2])
                        i += 2
                    } else if (i + 4 < params.size && params[i + 1] == 2) {
                        currentFg = Color(params[i + 2], params[i + 3], params[i + 4])
                        i += 4
                    }
                }

                // 256-color background: 48;5;n
                48 -> {
                    if (i + 2 < params.size && params[i + 1] == 5) {
                        currentBg = themedColor256(params[i + 2])
                        i += 2
                    } else if (i + 4 < params.size && params[i + 1] == 2) {
                        currentBg = Color(params[i + 2], params[i + 3], params[i + 4])
                        i += 4
                    }
                }

                39 -> currentFg = null  // Default foreground
                49 -> currentBg = null  // Default background
            }
            i++
        }
    }

    private fun resetStyle() {
        currentFg = null
        currentBg = null
        currentBold = false
        currentItalic = false
        currentUnderline = false
    }

    private fun clearScreen() {
        _screen.value = List(_rows.value) { List(_cols.value) { TerminalCell() } }
        _cursorRow.value = 0
        _cursorCol.value = 0
    }

    /** Set scroll offset for scrollback viewing (0 = bottom/latest) */
    fun setScrollOffset(offset: Int) {
        _scrollOffset.value = offset.coerceAtLeast(0)
    }

    companion object {
        // Dark theme ANSI colors (default, good for dark backgrounds)
        private val DARK_ANSI_COLORS = arrayOf(
            Color(0, 0, 0),         // 0: Black
            Color(170, 0, 0),       // 1: Red
            Color(0, 170, 0),       // 2: Green
            Color(170, 85, 0),      // 3: Yellow
            Color(0, 0, 170),       // 4: Blue
            Color(170, 0, 170),     // 5: Magenta
            Color(0, 170, 170),     // 6: Cyan
            Color(170, 170, 170),   // 7: White
        )

        private val DARK_ANSI_BRIGHT_COLORS = arrayOf(
            Color(85, 85, 85),      // 8: Bright Black
            Color(255, 85, 85),     // 9: Bright Red
            Color(85, 255, 85),     // 10: Bright Green
            Color(255, 255, 85),    // 11: Bright Yellow
            Color(85, 85, 255),     // 12: Bright Blue
            Color(255, 85, 255),    // 13: Bright Magenta
            Color(85, 255, 255),    // 14: Bright Cyan
            Color(255, 255, 255),   // 15: Bright White
        )

        // Light theme ANSI colors (swapped black/white, adjusted for readability on light background)
        private val LIGHT_ANSI_COLORS = arrayOf(
            Color(170, 170, 170),   // 0: Black → dark gray (visible on white)
            Color(170, 0, 0),       // 1: Red
            Color(0, 128, 0),       // 2: Green (darker for light bg)
            Color(170, 85, 0),      // 3: Yellow
            Color(0, 0, 170),       // 4: Blue
            Color(170, 0, 170),     // 5: Magenta
            Color(0, 128, 128),     // 6: Cyan (darker for light bg)
            Color(0, 0, 0),         // 7: White → black (text color on light bg)
        )

        private val LIGHT_ANSI_BRIGHT_COLORS = arrayOf(
            Color(85, 85, 85),      // 8: Bright Black
            Color(255, 85, 85),     // 9: Bright Red
            Color(0, 170, 0),       // 10: Bright Green
            Color(200, 180, 0),     // 11: Bright Yellow (darker for light bg)
            Color(85, 85, 255),     // 12: Bright Blue
            Color(255, 85, 255),    // 13: Bright Magenta
            Color(0, 170, 170),     // 14: Bright Cyan
            Color(60, 60, 60),      // 15: Bright White → dark gray
        )

        // Standard arrays kept for backward-compatible static access
        private val ANSI_COLORS get() = DARK_ANSI_COLORS
        private val ANSI_BRIGHT_COLORS get() = DARK_ANSI_BRIGHT_COLORS

        // 6x6x6 color cube for 256-color mode (indices 16-231)
        private val COLOR_CUBE: Array<Color> by lazy {
            val cube = mutableListOf<Color>()
            val levels = intArrayOf(0, 95, 135, 175, 215, 255)
            for (r in levels) {
                for (g in levels) {
                    for (b in levels) {
                        cube.add(Color(r, g, b))
                    }
                }
            }
            cube.toTypedArray()
        }

        // Grayscale ramp for 256-color mode (indices 232-255)
        private val GRAY_RAMP: Array<Color> by lazy {
            (232..255).map { i ->
                val v = 8 + (i - 232) * 10
                Color(v, v, v)
            }.toTypedArray()
        }

        fun standardColor(index: Int): Color = ANSI_COLORS[index.coerceIn(0, 7)]
        fun brightColor(index: Int): Color = ANSI_BRIGHT_COLORS[index.coerceIn(0, 7)]

        fun color256(index: Int): Color {
            return when {
                index < 8 -> ANSI_COLORS[index]
                index < 16 -> ANSI_BRIGHT_COLORS[index - 8]
                index < 232 -> COLOR_CUBE[index - 16]
                else -> GRAY_RAMP[index - 232]
            }
        }
    }

    /**
     * Theme-aware standard ANSI color (indices 0-7).
     * Uses light palette when [isDarkTheme] is false.
     */
    fun themedStandardColor(index: Int): Color {
        val palette = if (isDarkTheme) DARK_ANSI_COLORS else LIGHT_ANSI_COLORS
        return palette[index.coerceIn(0, 7)]
    }

    /**
     * Theme-aware bright ANSI color (indices 0-7, mapped to 8-15).
     * Uses light palette when [isDarkTheme] is false.
     */
    fun themedBrightColor(index: Int): Color {
        val palette = if (isDarkTheme) DARK_ANSI_BRIGHT_COLORS else LIGHT_ANSI_BRIGHT_COLORS
        return palette[index.coerceIn(0, 7)]
    }

    /**
     * Theme-aware 256-color resolution.
     * Standard (0-7) and bright (8-15) use theme-adjusted palettes.
     * Color cube (16-231) and grayscale (232-255) remain unchanged.
     */
    fun themedColor256(index: Int): Color {
        return when {
            index < 8 -> themedStandardColor(index)
            index < 16 -> themedBrightColor(index - 8)
            index < 232 -> COLOR_CUBE[index - 16]
            else -> GRAY_RAMP[index - 232]
        }
    }
}

/**
 * A single cell in the terminal grid.
 */
data class TerminalCell(
    val char: Char = ' ',
    val foreground: Color? = null,
    val background: Color? = null,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
)
