package me.xiaok.opencode.ui.screens.files

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import me.xiaok.opencode.domain.model.FileContent

// ---------------------------------------------------------------------------
// File Content Viewer
// ---------------------------------------------------------------------------

private const val MAX_FILE_SIZE_CHARS = 100_000
private const val MAX_DISPLAY_LINES = 2000

@Composable
internal fun FileContentViewer(fileContent: FileContent, filePath: String? = null) {
    when {
        fileContent.isImage && fileContent.encoding == "base64" -> {
            BinaryImagePreview(
                base64Data = fileContent.content,
                mimeType = fileContent.mimeType ?: "image/png",
            )
        }
        fileContent.isBinary -> {
            BinaryFilePlaceholder(
                mimeType = fileContent.mimeType,
                filePath = filePath,
            )
        }
        else -> {
            TextFileContentViewer(
                content = fileContent.content,
                filePath = filePath,
                diff = fileContent.diff,
            )
        }
    }
}

@Composable
private fun BinaryImagePreview(
    base64Data: String,
    mimeType: String,
) {
    val bitmap = remember(base64Data) {
        try {
            val bytes = android.util.Base64.decode(base64Data, android.util.Base64.DEFAULT)
            android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (_: Exception) {
            null
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Image preview",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
            )
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Default.Description,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Failed to decode image",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun BinaryFilePlaceholder(
    mimeType: String?,
    filePath: String?,
) {
    val ext = filePath?.substringAfterLast('.', "")?.uppercase() ?: "FILE"
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Default.Description,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "$ext file",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (mimeType != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = mimeType,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Binary file preview not supported",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TextFileContentViewer(
    content: String,
    filePath: String?,
    diff: String?,
) {
    val isLargeFile = content.length > MAX_FILE_SIZE_CHARS
    val lines = remember(content) { content.lines() }
    val displayLines = remember(lines, isLargeFile) {
        if (isLargeFile) lines.take(MAX_DISPLAY_LINES) else lines
    }
    val lineCountDigits = remember(displayLines) {
        displayLines.size.toString().length
    }
    val scrollState = rememberScrollState()
    val syntaxContent = remember(displayLines, filePath) {
        displayLines.map { line -> highlightSyntaxLine(line, filePath) }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Truncation warning
        if (isLargeFile) {
            Surface(
                color = MaterialTheme.colorScheme.tertiaryContainer,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = "File too large (${formatFileSize(content.length)}). Showing first $MAX_DISPLAY_LINES of ${lines.size} lines.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }

        // Diff section
        if (!diff.isNullOrBlank()) {
            DiffSection(diff = diff)
        }

        // Code content with line numbers
        Box(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .horizontalScroll(scrollState),
        ) {
            SelectionContainer {
                Text(
                    text = buildAnnotatedString {
                        syntaxContent.forEachIndexed { index, line ->
                            // Line number
                            withStyle(
                                SpanStyle(
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                    fontFamily = FontFamily.Monospace,
                                )
                            ) {
                                append(String.format("%${lineCountDigits}d  ", index + 1))
                            }
                            // Code content
                            append(line)
                            if (index < syntaxContent.lastIndex) append("\n")
                        }
                    },
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        lineHeight = MaterialTheme.typography.bodySmall.lineHeight * 1.4,
                    ),
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
private fun DiffSection(diff: String) {
    var expanded by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = if (expanded) Icons.Default.FolderOpen else Icons.Default.Folder,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Git Diff",
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = FontWeight.SemiBold,
                ),
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = if (expanded) "Hide" else "Show",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        androidx.compose.animation.AnimatedVisibility(visible = expanded) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth(),
            ) {
                SelectionContainer {
                    Text(
                        text = diff,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                        ),
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
        HorizontalDivider(
            thickness = 0.5.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
        )
    }
}

private fun formatFileSize(charCount: Int): String {
    return when {
        charCount < 1_000 -> "$charCount B"
        charCount < 1_000_000 -> "${charCount / 1_000} KB"
        else -> "${"%.1f".format(charCount / 1_000_000.0)} MB"
    }
}

private fun highlightSyntaxLine(line: String, filePath: String?): AnnotatedString {
    val rules = getSyntaxRules(filePath)
    if (rules.isEmpty()) return AnnotatedString(line)

    data class MatchEntry(val start: Int, val end: Int, val style: SpanStyle)

    val matches = mutableListOf<MatchEntry>()
    for (rule in rules) {
        rule.pattern.findAll(line).forEach { result ->
            matches.add(MatchEntry(result.range.first, result.range.last + 1, rule.style))
        }
    }

    matches.sortBy { it.start }
    val filtered = mutableListOf<MatchEntry>()
    var lastEnd = 0
    for (m in matches) {
        if (m.start >= lastEnd) {
            filtered.add(m)
            lastEnd = m.end
        }
    }

    return buildAnnotatedString {
        var pos = 0
        for (m in filtered) {
            if (pos < m.start) {
                append(line.substring(pos, m.start))
            }
            pushStyle(m.style)
            append(line.substring(m.start, m.end))
            pop()
            pos = m.end
        }
        if (pos < line.length) {
            append(line.substring(pos))
        }
    }
}

// ---------------------------------------------------------------------------
// Syntax Highlighting
// ---------------------------------------------------------------------------

private data class SyntaxRule(
    val pattern: Regex,
    val style: SpanStyle,
)

private fun getSyntaxRules(filePath: String?): List<SyntaxRule> {
    val ext = filePath?.substringAfterLast('.', "")?.lowercase() ?: return emptyList()

    // Common styles
    val keywordStyle = SpanStyle(
        color = Color(0xFFC678DD), // purple
        fontWeight = FontWeight.Bold,
    )
    val stringStyle = SpanStyle(
        color = Color(0xFF98C379), // green
    )
    val commentStyle = SpanStyle(
        color = Color(0xFF5C6370), // gray
    )
    val numberStyle = SpanStyle(
        color = Color(0xFFD19A66), // orange
    )
    val annotationStyle = SpanStyle(
        color = Color(0xFFE5C07B), // yellow
    )
    val typeStyle = SpanStyle(
        color = Color(0xFF61AFEF), // blue
    )

    return when (ext) {
        "kt", "kts", "java" -> listOf(
            SyntaxRule(Regex("//.*$"), commentStyle),
            SyntaxRule(Regex("/\\*[\\s\\S]*?\\*/"), commentStyle),
            SyntaxRule(Regex("""\"\"\"[\s\S]*?\"\"\""""), stringStyle),
            SyntaxRule(Regex(""""(?:[^"\\]|\\.)*"""), stringStyle),
            SyntaxRule(Regex("""'(?:[^'\\]|\\.)*'"""), stringStyle),
            SyntaxRule(Regex("""\b(package|import|class|interface|object|enum|data|sealed|abstract|open|override|private|protected|public|internal|val|var|fun|suspend|inline|infix|operator|companion|init|constructor|if|else|when|for|while|do|try|catch|finally|throw|return|break|continue|is|in|as|typealias|typeof|true|false|null|this|super|it|lazy|by|crossinline|noinline|reified|tailrec|annotation|expect|actual|lateinit|field|property|receiver|param|setparam|get|set|delegate|dynamic|context)\b"""), keywordStyle),
            SyntaxRule(Regex("""@[A-Za-z]\w*"""), annotationStyle),
            SyntaxRule(Regex("""\b\d+\.?\d*[fFLl]?\b"""), numberStyle),
            SyntaxRule(Regex("""\b(?:String|Int|Long|Boolean|Float|Double|List|Map|Set|Unit|Any|Nothing|Pair|Triple|Array|ByteArray|IntArray|Sequence|Flow|MutableStateFlow|StateFlow|LiveData|Bundle|Intent|Context|View|ViewModel|Composable)\b"""), typeStyle),
        )
        "py" -> listOf(
            SyntaxRule(Regex("#.*$"), commentStyle),
            SyntaxRule(Regex("\"\"\"[\\s\\S]*?\"\"\""), stringStyle),
            SyntaxRule(Regex(""""(?:[^"\\]|\\.)*"""), stringStyle),
            SyntaxRule(Regex("""'(?:[^'\\]|\\.)*'"""), stringStyle),
            SyntaxRule(Regex("""\b(def|class|async|await|import|from|return|if|elif|else|for|while|try|except|finally|with|raise|yield|lambda|pass|break|continue|and|or|not|in|is|True|False|None|self|global|nonlocal|assert|del)\b"""), keywordStyle),
            SyntaxRule(Regex("""@\w+"""), annotationStyle),
            SyntaxRule(Regex("""\b\d+\.?\d*\b"""), numberStyle),
        )
        "js", "ts", "tsx", "jsx" -> listOf(
            SyntaxRule(Regex("//.*$"), commentStyle),
            SyntaxRule(Regex("/\\*[\\s\\S]*?\\*/"), commentStyle),
            SyntaxRule(Regex("""`[\s\S]*?`"""), stringStyle),
            SyntaxRule(Regex(""""(?:[^"\\]|\\.)*"""), stringStyle),
            SyntaxRule(Regex("""'(?:[^'\\]|\\.)*'"""), stringStyle),
            SyntaxRule(Regex("""\b(const|let|var|function|return|if|else|for|while|do|switch|case|break|continue|try|catch|finally|throw|new|class|extends|implements|import|export|from|default|async|await|yield|typeof|instanceof|in|of|null|undefined|true|false|this|super|interface|type|enum|abstract|as|readonly|declare|module|namespace|public|private|protected|static|override|void)\b"""), keywordStyle),
            SyntaxRule(Regex("""@\w+"""), annotationStyle),
            SyntaxRule(Regex("""\b\d+\.?\d*\b"""), numberStyle),
        )
        "json", "yaml", "yml", "toml" -> listOf(
            SyntaxRule(Regex("\"(?:[^\"\\\\]|\\\\.)*\"\\s*:"), typeStyle),
            SyntaxRule(Regex(":\\s*\"(?:[^\"\\\\]|\\\\.)*\""), stringStyle),
            SyntaxRule(Regex(":\\s*(?:true|false|null)\\b"), keywordStyle),
            SyntaxRule(Regex(":\\s*\\d+\\.?\\d*\\b"), numberStyle),
            SyntaxRule(Regex("#.*$"), commentStyle),
        )
        "xml", "html", "svg" -> listOf(
            SyntaxRule(Regex("<!--.*?-->"), commentStyle),
            SyntaxRule(Regex("</?[A-Za-z][\\w-]*"), keywordStyle),
            SyntaxRule(Regex("""\w+\s*=\s*"[^"]*""""), stringStyle),
            SyntaxRule(Regex(""""[^"]*""""), stringStyle),
        )
        "css", "scss" -> listOf(
            SyntaxRule(Regex("/\\*.*?\\*/"), commentStyle),
            SyntaxRule(Regex("""\b(?:color|background|margin|padding|border|display|position|width|height|font|text|flex|grid|gap|overflow|opacity|z-index|transition|animation|transform)\s*:"""), keywordStyle),
            SyntaxRule(Regex("""#[0-9a-fA-F]{3,8}\b"""), numberStyle),
            SyntaxRule(Regex("""\b\d+\.?\d*(px|em|rem|%|vh|vw|dp|sp)?\b"""), numberStyle),
        )
        "md", "markdown" -> listOf(
            SyntaxRule(Regex("^#{1,6}\\s+.*$"), SpanStyle(fontWeight = FontWeight.Bold, color = Color(0xFF61AFEF))),
            SyntaxRule(Regex("""`[^`]+`"""), SpanStyle(color = Color(0xFF98C379), fontFamily = FontFamily.Monospace)),
            SyntaxRule(Regex("""```[\s\S]*?```"""), SpanStyle(color = Color(0xFF98C379), fontFamily = FontFamily.Monospace)),
            SyntaxRule(Regex("""\*\*[^*]+\*\*"""), SpanStyle(fontWeight = FontWeight.Bold)),
            SyntaxRule(Regex("""\*[^*]+\*"""), SpanStyle(fontStyle = FontStyle.Italic)),
        )
        "sh", "bash", "zsh" -> listOf(
            SyntaxRule(Regex("#.*$"), commentStyle),
            SyntaxRule(Regex(""""(?:[^"\\]|\\.)*"""), stringStyle),
            SyntaxRule(Regex("""'(?:[^'\\]|\\.)*'"""), stringStyle),
            SyntaxRule(Regex("""\b(if|then|else|elif|fi|for|while|do|done|case|esac|in|function|return|exit|export|import|source|alias|echo|cd|ls|grep|sed|awk|find|cat|mkdir|rm|cp|mv|chmod|chown|sudo|apt|yum|npm|pip|docker|kubectl|git)\b"""), keywordStyle),
            SyntaxRule(Regex("""\$\{?\w+\}?"""), typeStyle),
            SyntaxRule(Regex("""\b\d+\b"""), numberStyle),
        )
        "sql" -> listOf(
            SyntaxRule(Regex("--.*$"), commentStyle),
            SyntaxRule(Regex("/\\*.*?\\*/"), commentStyle),
            SyntaxRule(Regex("""'[^']*'"""), stringStyle),
            SyntaxRule(Regex("""\b(SELECT|FROM|WHERE|INSERT|INTO|VALUES|UPDATE|SET|DELETE|CREATE|TABLE|ALTER|DROP|INDEX|JOIN|LEFT|RIGHT|INNER|OUTER|ON|AND|OR|NOT|IN|IS|NULL|LIKE|ORDER|BY|GROUP|HAVING|LIMIT|OFFSET|AS|DISTINCT|COUNT|SUM|AVG|MIN|MAX|EXISTS|BETWEEN|UNION|ALL|PRIMARY|KEY|FOREIGN|REFERENCES|CASCADE|CONSTRAINT|DEFAULT|AUTO_INCREMENT|INT|BIGINT|VARCHAR|TEXT|BOOLEAN|FLOAT|DOUBLE|DATE|TIMESTAMP|SERIAL)\b"""), keywordStyle),
            SyntaxRule(Regex("""\b\d+\.?\d*\b"""), numberStyle),
        )
        else -> emptyList()
    }
}
