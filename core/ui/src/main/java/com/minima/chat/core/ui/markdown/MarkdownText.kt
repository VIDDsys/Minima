package com.minima.chat.core.ui.markdown

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.minima.chat.core.ui.theme.LocalMinimaColors
import com.minima.chat.core.ui.theme.MinimaTypography
import com.minima.chat.core.ui.util.SensitiveClipboard
import com.minima.chat.core.ui.util.UnicodeSafe

/**
 * Lightweight markdown for assistant bubbles: headings, bold, italic, inline code,
 * fenced code blocks (lang + copy), bullets (+, -, *), numbered lists, GFM pipe tables. No HTML.
 */
@Composable
fun MarkdownText(
    markdown: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val colors = LocalMinimaColors.current
    val context = LocalContext.current
    val blocks = remember(markdown) {
        MarkdownParser.parseBlocks(UnicodeSafe.sanitize(markdown))
    }
    SelectionContainer {
        Column(modifier = modifier) {
            blocks.forEach { block ->
                when (block) {
                    is MdBlock.Code -> {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(colors.fillTertiary)
                                .semantics {
                                    contentDescription = "Code block" +
                                        (block.language?.let { " $it" } ?: "")
                                },
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp),
                            ) {
                                Text(
                                    text = block.language?.ifBlank { "code" } ?: "code",
                                    style = MinimaTypography.labelSmall,
                                    color = colors.textTertiary,
                                    modifier = Modifier.weight(1f).padding(start = 4.dp),
                                )
                                TextButton(
                                    onClick = {
                                        if (SensitiveClipboard.looksLikeApiKey(block.code)) return@TextButton
                                        SensitiveClipboard.copyPlain(context, "code", block.code, sensitive = false)
                                    },
                                ) {
                                    Text("Copy", color = colors.accent, style = MinimaTypography.labelSmall)
                                }
                            }
                            Text(
                                text = block.code,
                                style = MinimaTypography.bodySmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    lineHeight = 18.sp,
                                ),
                                color = colors.text,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState())
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                            )
                        }
                    }
                    is MdBlock.Table -> {
                        val scroll = rememberScrollState()
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(colors.fillTertiary)
                                .horizontalScroll(scroll)
                                .semantics { contentDescription = "Markdown table" },
                        ) {
                            MarkdownTableRow(
                                cells = block.headers,
                                color = color,
                                header = true,
                                colCount = block.colCount,
                            )
                            HorizontalDivider(color = colors.borderSecondary, thickness = 1.dp)
                            block.rows.forEachIndexed { idx, row ->
                                MarkdownTableRow(
                                    cells = row,
                                    color = color,
                                    header = false,
                                    colCount = block.colCount,
                                )
                                if (idx < block.rows.lastIndex) {
                                    HorizontalDivider(color = colors.borderSecondary.copy(alpha = 0.5f), thickness = 0.5.dp)
                                }
                            }
                        }
                    }
                    is MdBlock.Heading -> {
                        Text(
                            text = MarkdownParser.inlineMarkdown(block.text),
                            style = when (block.level) {
                                1 -> MinimaTypography.titleLarge
                                2 -> MinimaTypography.titleMedium
                                else -> MinimaTypography.titleSmall
                            },
                            color = color,
                            modifier = Modifier.padding(top = 6.dp, bottom = 2.dp),
                        )
                    }
                    is MdBlock.Bullet -> {
                        Text(
                            text = buildAnnotatedString {
                                append("• ")
                                append(MarkdownParser.inlineMarkdown(block.text))
                            },
                            style = MinimaTypography.bodyMedium,
                            color = color,
                            modifier = Modifier.padding(start = 4.dp + (block.indent * 12).dp, top = 2.dp),
                        )
                    }
                    is MdBlock.Numbered -> {
                        Text(
                            text = buildAnnotatedString {
                                append("${block.number}. ")
                                append(MarkdownParser.inlineMarkdown(block.text))
                            },
                            style = MinimaTypography.bodyMedium,
                            color = color,
                            modifier = Modifier.padding(start = 4.dp, top = 2.dp),
                        )
                    }
                    is MdBlock.Paragraph -> {
                        Text(
                            text = MarkdownParser.inlineMarkdown(block.text),
                            style = MinimaTypography.bodyMedium,
                            color = color,
                            modifier = Modifier.padding(vertical = 2.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MarkdownTableRow(
    cells: List<String>,
    color: Color,
    header: Boolean,
    colCount: Int,
) {
    val colors = LocalMinimaColors.current
    Row(
        modifier = Modifier
            .padding(horizontal = 6.dp, vertical = 4.dp)
            .then(if (header) Modifier.background(colors.fillTertiary) else Modifier),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(colCount) { i ->
            val raw = cells.getOrElse(i) { "" }
            Text(
                text = MarkdownParser.inlineMarkdown(raw),
                style = if (header) {
                    MinimaTypography.labelSmall.copy(fontWeight = FontWeight.SemiBold)
                } else {
                    MinimaTypography.bodySmall
                },
                color = if (header) colors.textSecondary else color,
                modifier = Modifier
                    .widthIn(min = 64.dp, max = 220.dp)
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
    }
}

sealed class MdBlock {
    data class Paragraph(val text: String) : MdBlock()
    data class Heading(val level: Int, val text: String) : MdBlock()
    data class Bullet(val text: String, val indent: Int = 0) : MdBlock()
    data class Numbered(val number: Int, val text: String) : MdBlock()
    data class Code(val code: String, val language: String? = null) : MdBlock()
    data class Table(
        val headers: List<String>,
        val rows: List<List<String>>,
    ) : MdBlock() {
        val colCount: Int get() = maxOf(headers.size, rows.maxOfOrNull { it.size } ?: 0)
    }
}

object MarkdownParser {
    fun parseBlocks(raw: String): List<MdBlock> {
        val src = UnicodeSafe.sanitize(raw)
        val out = mutableListOf<MdBlock>()
        val lines = src.replace("\r\n", "\n").split("\n")
        var i = 0
        val para = StringBuilder()
        fun flushPara() {
            val t = para.toString().trimEnd()
            if (t.isNotEmpty()) out += MdBlock.Paragraph(t)
            para.clear()
        }
        while (i < lines.size) {
            val line = lines[i]
            when {
                line.startsWith("```") -> {
                    flushPara()
                    val lang = line.removePrefix("```").trim().ifBlank { null }
                    i++
                    val code = StringBuilder()
                    while (i < lines.size && !lines[i].startsWith("```")) {
                        if (code.isNotEmpty()) code.append('\n')
                        code.append(lines[i])
                        i++
                    }
                    out += MdBlock.Code(code.toString(), lang)
                    if (i < lines.size) i++
                }
                // GFM pipe table: header + separator (|---|---|) + body rows
                looksLikeTableHeader(line, lines.getOrNull(i + 1)) -> {
                    flushPara()
                    val headers = splitTableRow(line)
                    i += 2 // skip header + separator
                    val rows = mutableListOf<List<String>>()
                    while (i < lines.size && looksLikeTableRow(lines[i])) {
                        rows += padRow(splitTableRow(lines[i]), headers.size)
                        i++
                    }
                    out += MdBlock.Table(headers, rows)
                }
                line.matches(Regex("^#{1,3}\\s+.+")) -> {
                    flushPara()
                    val level = line.takeWhile { it == '#' }.length
                    out += MdBlock.Heading(level, line.drop(level).trim())
                    i++
                }
                line.matches(Regex("^(\\s*)[-*+]\\s+.+")) -> {
                    flushPara()
                    val leading = line.takeWhile { it == ' ' || it == '\t' }.length
                    val indent = leading / 2
                    val trimmed = line.trimStart()
                    val content = trimmed.drop(2).trim() // drop bullet marker and space
                    out += MdBlock.Bullet(content, indent)
                    i++
                    // Soft-consume blank lines that only separate list items
                    while (i < lines.size && lines[i].isBlank()) {
                        val nxt = lines.getOrNull(i + 1) ?: break
                        if (nxt.matches(Regex("^(\\s*)[-*+]\\s+.+")) ||
                            nxt.matches(Regex("^(\\s*)\\d+\\.\\s+.+"))
                        ) {
                            i++
                        } else break
                    }
                }
                line.matches(Regex("^(\\s*)\\d+\\.\\s+.+")) -> {
                    flushPara()
                    val trimmed = line.trimStart()
                    val num = trimmed.takeWhile { it.isDigit() }.toIntOrNull() ?: 1
                    val content = trimmed.substringAfter('.').trim()
                    out += MdBlock.Numbered(num, content)
                    i++
                    while (i < lines.size && lines[i].isBlank()) {
                        val nxt = lines.getOrNull(i + 1) ?: break
                        if (nxt.matches(Regex("^(\\s*)[-*+]\\s+.+")) ||
                            nxt.matches(Regex("^(\\s*)\\d+\\.\\s+.+"))
                        ) {
                            i++
                        } else break
                    }
                }
                line.isBlank() -> {
                    flushPara()
                    i++
                }
                else -> {
                    if (para.isNotEmpty()) para.append('\n')
                    para.append(line)
                    i++
                }
            }
        }
        flushPara()
        return out.ifEmpty { listOf(MdBlock.Paragraph(src)) }
    }

    /** Split a GFM table row into cells (trims outer pipes). */
    fun splitTableRow(line: String): List<String> {
        var s = line.trim()
        if (s.startsWith("|")) s = s.drop(1)
        if (s.endsWith("|")) s = s.dropLast(1)
        return s.split("|").map { it.trim() }
    }

    fun looksLikeTableSeparator(line: String): Boolean {
        if (!line.contains('|') && !line.contains('-')) return false
        val cells = splitTableRow(line)
        if (cells.isEmpty()) return false
        return cells.all { cell ->
            val c = cell.replace(" ", "")
            c.isNotEmpty() && c.matches(Regex("^:?-+:?$"))
        }
    }

    fun looksLikeTableRow(line: String): Boolean {
        val t = line.trim()
        if (t.isEmpty() || !t.contains('|')) return false
        if (looksLikeTableSeparator(t)) return false
        return splitTableRow(t).isNotEmpty()
    }

    fun looksLikeTableHeader(headerLine: String, separatorLine: String?): Boolean {
        if (separatorLine == null) return false
        if (!looksLikeTableRow(headerLine)) return false
        if (!looksLikeTableSeparator(separatorLine)) return false
        val cols = splitTableRow(headerLine).size
        val sepCols = splitTableRow(separatorLine).size
        return cols > 0 && sepCols == cols
    }

    private fun padRow(cells: List<String>, cols: Int): List<String> {
        if (cells.size == cols) return cells
        if (cells.size > cols) return cells.take(cols)
        return cells + List(cols - cells.size) { "" }
    }

    fun inlineMarkdown(text: String) = buildAnnotatedString {
        var i = 0
        while (i < text.length) {
            when {
                text.startsWith("**", i) -> {
                    val end = text.indexOf("**", i + 2)
                    if (end > i) {
                        withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) {
                            append(text.substring(i + 2, end))
                        }
                        i = end + 2
                    } else {
                        append(text[i]); i++
                    }
                }
                text.startsWith("`", i) && !text.startsWith("```", i) -> {
                    val end = text.indexOf('`', i + 1)
                    if (end > i) {
                        withStyle(
                            SpanStyle(
                                fontFamily = FontFamily.Monospace,
                                background = Color(0x22000000),
                            ),
                        ) { append(text.substring(i + 1, end)) }
                        i = end + 1
                    } else {
                        append(text[i]); i++
                    }
                }
                text.startsWith("*", i) && !text.startsWith("**", i) -> {
                    val end = text.indexOf('*', i + 1)
                    if (end > i) {
                        withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                            append(text.substring(i + 1, end))
                        }
                        i = end + 1
                    } else {
                        append(text[i]); i++
                    }
                }
                text.startsWith("[", i) -> {
                    val close = text.indexOf(']', i)
                    val openParen = if (close >= 0) text.indexOf('(', close) else -1
                    val closeParen = if (openParen >= 0) text.indexOf(')', openParen) else -1
                    if (close > i && openParen == close + 1 && closeParen > openParen) {
                        withStyle(SpanStyle(textDecoration = TextDecoration.Underline)) {
                            append(text.substring(i + 1, close))
                        }
                        i = closeParen + 1
                    } else {
                        append(text[i]); i++
                    }
                }
                else -> {
                    append(text[i]); i++
                }
            }
        }
    }
}
