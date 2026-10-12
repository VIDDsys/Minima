package com.inspiredandroid.kai.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp

/**
 * Column-aligned table that fills the message width when it fits, and scrolls
 * sideways inside that width when a row is wider. Cells are measured at their
 * natural one-line width so columns are not crushed into equal slivers.
 *
 * Two subcompose passes: the first reads each cell's natural width, the second
 * measures again at the resolved column width so alignment and padding apply.
 */
@Composable
internal fun AlignedTable(
    columnCount: Int,
    rowCount: Int,
    modifier: Modifier = Modifier,
    headerRows: Int = 0,
    cell: @Composable (row: Int, column: Int) -> Unit,
) {
    if (columnCount <= 0 || rowCount <= 0) return
    val dividerColor = MaterialTheme.colorScheme.outlineVariant
    val minColumnWidth = 32.dp
    val dividerThickness = 1.dp
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val viewport = if (maxWidth.value.isFinite()) maxWidth else 0.dp
        val scroll = rememberScrollState()
        Box(Modifier.fillMaxWidth().horizontalScroll(scroll)) {
            SubcomposeLayout(Modifier.widthIn(min = viewport)) { constraints ->
                val loose = Constraints(
                    maxWidth = Constraints.Infinity,
                    maxHeight = Constraints.Infinity,
                )
                val probes = subcompose("probe") {
                    for (row in 0 until rowCount) {
                        for (column in 0 until columnCount) {
                            cell(row, column)
                        }
                    }
                }.map { it.measure(loose) }
                val minColPx = minColumnWidth.roundToPx()
                val colW = IntArray(columnCount) { minColPx }
                probes.forEachIndexed { index, placeable ->
                    val column = index % columnCount
                    if (placeable.width > colW[column]) colW[column] = placeable.width
                }
                var width = colW.sum()
                val minWidth = constraints.minWidth.coerceAtLeast(0)
                if (width < minWidth) {
                    val base = (minWidth - width) / columnCount
                    var remainder = (minWidth - width) % columnCount
                    for (column in colW.indices) {
                        val extra = base + if (remainder > 0) 1 else 0
                        if (remainder > 0) remainder -= 1
                        colW[column] += extra
                    }
                    width = colW.sum()
                }
                val showDivider = headerRows > 0
                val slots = subcompose("cells") {
                    for (row in 0 until rowCount) {
                        for (column in 0 until columnCount) {
                            cell(row, column)
                        }
                    }
                    if (showDivider) {
                        Box(Modifier.height(dividerThickness).background(dividerColor))
                    }
                }
                val cellSlots = if (showDivider) slots.dropLast(1) else slots
                val placeables = cellSlots.mapIndexed { index, measurable ->
                    val column = index % columnCount
                    measurable.measure(
                        Constraints(
                            minWidth = colW[column],
                            maxWidth = colW[column],
                            maxHeight = Constraints.Infinity,
                        ),
                    )
                }
                val rowH = IntArray(rowCount)
                placeables.forEachIndexed { index, placeable ->
                    val row = index / columnCount
                    if (placeable.height > rowH[row]) rowH[row] = placeable.height
                }
                val dividerPx = if (showDivider) dividerThickness.roundToPx().coerceAtLeast(1) else 0
                val divider = if (showDivider) {
                    slots.last().measure(Constraints.fixed(width, dividerPx))
                } else {
                    null
                }
                val height = rowH.sum() + dividerPx
                layout(width, height) {
                    var y = 0
                    var cellIndex = 0
                    for (row in 0 until rowCount) {
                        var x = 0
                        for (column in 0 until columnCount) {
                            val placeable = placeables[cellIndex]
                            cellIndex += 1
                            val dy = (rowH[row] - placeable.height) / 2
                            placeable.placeRelative(x, y + dy)
                            x += colW[column]
                        }
                        y += rowH[row]
                        if (divider != null && row == headerRows - 1) {
                            divider.placeRelative(0, y)
                            y += dividerPx
                        }
                    }
                }
            }
        }
    }
}
