package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.SudokuBoard
import com.example.ui.theme.sudokuColors

@Composable
fun SudokuGridView(
    board: SudokuBoard,
    selectedIndex: Int?,
    conflictedIndices: Set<Int>,
    onCellSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.sudokuColors
    val selectedValue = if (selectedIndex != null && selectedIndex in 0..80) board[selectedIndex] else 0

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(8.dp))
            .background(colors.gridBackground)
            .testTag("sudoku_grid_view")
    ) {
        // Sudoku grid contents with major 3x3 block separator lines drawn on top
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawWithContent {
                    // 1. Draw the underlying 9x9 cells, backgrounds, and digits
                    drawContent()

                    val w = size.width
                    val h = size.height
                    val colStep = w / 9f
                    val rowStep = h / 9f

                    val majorStrokePx = 3.dp.toPx()
                    val majorColor = colors.subgridBorder

                    // 2. Draw major vertical grid lines: bolder line every 3rd column (at col 3 and col 6)
                    for (i in listOf(3, 6)) {
                        val x = i * colStep
                        drawLine(
                            color = majorColor,
                            start = Offset(x, 0f),
                            end = Offset(x, h),
                            strokeWidth = majorStrokePx
                        )
                    }

                    // 3. Draw major horizontal grid lines: bolder line every 3rd row (at row 3 and row 6)
                    for (i in listOf(3, 6)) {
                        val y = i * rowStep
                        drawLine(
                            color = majorColor,
                            start = Offset(0f, y),
                            end = Offset(w, y),
                            strokeWidth = majorStrokePx
                        )
                    }

                    // 4. Draw outer border frame matching the major grid line stroke
                    val halfStroke = majorStrokePx / 2f
                    drawRect(
                        color = colors.outerBorder,
                        topLeft = Offset(halfStroke, halfStroke),
                        size = Size(w - majorStrokePx, h - majorStrokePx),
                        style = Stroke(width = majorStrokePx)
                    )
                }
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                for (r in 0 until 9) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        for (c in 0 until 9) {
                            val index = r * 9 + c
                            val value = board[index]
                            val isGiven = board.isGiven[index]
                            val isSelected = selectedIndex == index
                            val isConflict = conflictedIndices.contains(index)
                            val isSameValue = selectedValue != 0 && value == selectedValue

                            val selectedRow = selectedIndex?.let { it / 9 }
                            val selectedCol = selectedIndex?.let { it % 9 }
                            val selectedBoxRow = selectedRow?.let { it / 3 }
                            val selectedBoxCol = selectedCol?.let { it / 3 }

                            val isRelated = selectedIndex != null && !isSelected && (
                                r == selectedRow ||
                                c == selectedCol ||
                                (selectedBoxRow != null && selectedBoxCol != null && r / 3 == selectedBoxRow && c / 3 == selectedBoxCol)
                            )

                            val cellBg = when {
                                isConflict -> colors.conflictCell
                                isSelected -> colors.selectedCell
                                isSameValue -> colors.sameDigitCell
                                isRelated -> colors.relatedCell
                                else -> colors.gridBackground
                            }

                            val textColor = when {
                                isConflict -> colors.errorText
                                isGiven -> colors.clueText
                                else -> colors.userText
                            }

                            val textWeight = if (isGiven) FontWeight.Bold else FontWeight.SemiBold

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxSize()
                                    .background(cellBg)
                                    .border(0.5.dp, colors.cellBorder)
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null
                                    ) {
                                        onCellSelected(index)
                                    }
                                    .testTag("cell_${r}_${c}"),
                                contentAlignment = Alignment.Center
                            ) {
                                if (value != 0) {
                                    Text(
                                        text = value.toString(),
                                        fontSize = 20.sp,
                                        fontWeight = textWeight,
                                        fontFamily = FontFamily.SansSerif,
                                        color = textColor
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
