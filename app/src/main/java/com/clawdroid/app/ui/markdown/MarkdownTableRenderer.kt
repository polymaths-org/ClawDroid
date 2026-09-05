package com.clawdroid.app.ui.markdown

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object MarkdownTableRenderer {
    data class RenderedTable(
        val headers: List<String>,
        val rows: List<List<String>>,
        val columnWidths: List<Int>,
    )

    sealed interface Block {
        data class TextBlock(val text: String) : Block
        data class TableBlock(val table: RenderedTable) : Block
    }

    fun parseBlocks(markdown: String): List<Block> {
        val lines = markdown.lines()
        val blocks = mutableListOf<Block>()
        val pendingText = mutableListOf<String>()
        var index = 0

        fun flushText() {
            val text = pendingText.joinToString("\n").trim()
            if (text.isNotBlank()) blocks += Block.TextBlock(text)
            pendingText.clear()
        }

        while (index < lines.size) {
            val line = lines[index]
            val next = lines.getOrNull(index + 1)
            if (isTableHeader(line, next)) {
                flushText()
                val tableLines = mutableListOf(line)
                val rawTableLines = mutableListOf(line, next.orEmpty())
                index += 2
                while (index < lines.size && isTableRow(lines[index])) {
                    tableLines += lines[index]
                    rawTableLines += lines[index]
                    index++
                }
                parseTable(tableLines)?.let { blocks += Block.TableBlock(it) } ?: pendingText.addAll(rawTableLines)
                continue
            }
            pendingText += line
            index++
        }
        flushText()
        return blocks
    }

    private fun parseTable(lines: List<String>): RenderedTable? {
        if (lines.size < 2) return null
        val headers = parseRow(lines[0])
        val rows = lines.drop(1).map(::parseRow).filter { it.isNotEmpty() }
        if (headers.isEmpty() || rows.isEmpty()) return null
        val columnCount = maxOf(headers.size, rows.maxOf { it.size })
        val widths = (0 until columnCount).map { col ->
            maxOf(
                headers.getOrElse(col) { "" }.length.coerceAtLeast(1),
                rows.maxOfOrNull { row -> row.getOrElse(col) { "" }.length.coerceAtLeast(1) } ?: 1,
            )
        }
        return RenderedTable(headers, rows, widths)
    }

    private fun parseRow(line: String): List<String> =
        line.trim().trim('|').split('|').map { it.trim() }

    private fun isTableHeader(line: String, next: String?): Boolean =
        isTableRow(line) && next != null && next.trim().trim('|').split('|').all { cell ->
            cell.trim().matches(Regex(":?-{3,}:?"))
        }

    private fun isTableRow(line: String): Boolean {
        val trimmed = line.trim()
        return trimmed.startsWith("|") && trimmed.endsWith("|") && trimmed.count { it == '|' } >= 2
    }
}

@Composable
fun MarkdownResponseContent(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurface,
    fontSize: TextUnit = 14.sp,
    maxLines: Int = 10,
) {
    val blocks = remember(text) { MarkdownTableRenderer.parseBlocks(text) }
    SelectionContainer {
        Column(
            modifier = modifier,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            blocks.forEach { block ->
                when (block) {
                    is MarkdownTableRenderer.Block.TextBlock -> Text(
                        text = block.text,
                        color = color,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = fontSize,
                            lineHeight = (fontSize.value + 5).sp,
                        ),
                        maxLines = maxLines,
                    )
                    is MarkdownTableRenderer.Block.TableBlock -> RenderedTableComposable(block.table)
                }
            }
        }
    }
}

@Composable
fun RenderedTableComposable(table: MarkdownTableRenderer.RenderedTable) {
    val shape = RoundedCornerShape(8.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.68f), shape)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.64f), shape)
            .padding(4.dp),
    ) {
        TableRow(
            cells = table.headers,
            widths = table.columnWidths,
            bold = true,
            background = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f))
        table.rows.forEachIndexed { index, row ->
            TableRow(
                cells = row,
                widths = table.columnWidths,
                bold = false,
                background = if (index % 2 == 0) Color.Transparent else MaterialTheme.colorScheme.surface.copy(alpha = 0.35f),
            )
        }
    }
}

@Composable
private fun TableRow(
    cells: List<String>,
    widths: List<Int>,
    bold: Boolean,
    background: Color,
) {
    Row(
        modifier = Modifier
            .background(background)
            .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        val count = maxOf(cells.size, widths.size)
        repeat(count) { index ->
            Text(
                text = cells.getOrElse(index) { "" },
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
                fontSize = if (bold) 13.sp else 12.sp,
                lineHeight = 17.sp,
                modifier = Modifier
                    .widthIn(min = (widths.getOrElse(index) { 8 } * 8).coerceIn(72, 220).dp, max = 220.dp)
                    .padding(end = 12.dp),
            )
        }
    }
}
