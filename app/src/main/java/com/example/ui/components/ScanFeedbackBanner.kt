package com.example.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Feedback
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.diagnostics.DifferenceType
import com.example.diagnostics.ScanDiagnosticRecord
import com.example.model.SudokuBoard

/**
 * An interactive, contextual banner displayed on the Sudoku Editor screen
 * informing the user how to provide feedback on missed or misrecognized numbers.
 */
@Composable
fun ScanFeedbackBanner(
    initialBoard: SudokuBoard,
    currentBoard: SudokuBoard,
    onOpenFeedback: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Determine missed numbers and corrections
    val (missedCount, totalDiffs) = remember(initialBoard, currentBoard) {
        var missed = 0
        var total = 0
        for (i in 0 until 81) {
            val s = initialBoard[i]
            val c = currentBoard[i]
            if (s != c) {
                total++
                if (s == 0 && c in 1..9) missed++
            }
        }
        missed to total
    }

    val hasCorrections = totalDiffs > 0

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onOpenFeedback)
            .testTag("scan_feedback_banner"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (hasCorrections) {
                MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.65f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (hasCorrections) {
                        MaterialTheme.colorScheme.tertiary
                    } else {
                        MaterialTheme.colorScheme.primaryContainer
                    },
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        imageVector = if (hasCorrections) Icons.Default.BugReport else Icons.AutoMirrored.Filled.HelpOutline,
                        contentDescription = null,
                        tint = if (hasCorrections) {
                            MaterialTheme.colorScheme.onTertiary
                        } else {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        },
                        modifier = Modifier
                            .padding(7.dp)
                            .size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    if (hasCorrections) {
                        Text(
                            text = if (missedCount > 0) "$missedCount Missed Number${if (missedCount > 1) "s" else ""} Captured" else "$totalDiffs Corrections Detected",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Tap to submit feedback with logs & photo to improve OCR",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Text(
                            text = "Did the scanner miss any numbers?",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Fill them in on the grid, then tap here to submit feedback",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = "Open Feedback",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
