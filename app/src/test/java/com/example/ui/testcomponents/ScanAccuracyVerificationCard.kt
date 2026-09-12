package com.example.ui.testcomponents

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.testdata.AccuracyMetricResult
import com.example.ui.components.SudokuGridView
import com.example.ui.theme.MyApplicationTheme

@Composable
fun ScanAccuracyVerificationCard(
    result: AccuracyMetricResult,
    modifier: Modifier = Modifier
) {
    MyApplicationTheme {
        Surface(
            modifier = modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background)
                .padding(16.dp),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header Banner
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = result.puzzleTitle,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = "Roborazzi Scanning Accuracy Verification",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Status Pill
                    val badgeColor = if (result.passed) Color(0xFF2E7D32) else Color(0xFFC62828)
                    Surface(
                        color = badgeColor.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(16.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, badgeColor)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (result.passed) Icons.Default.CheckCircle else Icons.Default.Warning,
                                contentDescription = if (result.passed) "Passed" else "Failed",
                                tint = badgeColor,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (result.passed) "VERIFIED PASS" else "DEGRADATION DETECTED",
                                color = badgeColor,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // KPI Metrics Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MetricChip(
                        title = "Accuracy",
                        value = "%.1f%%".format(result.accuracyPercentage),
                        subtitle = "${result.exactMatches}/${result.totalExpectedClues} clues",
                        modifier = Modifier.weight(1f),
                        isGood = result.accuracyPercentage >= 95f
                    )
                    MetricChip(
                        title = "False Positives",
                        value = "${result.falsePositives}",
                        subtitle = "Target: 0",
                        modifier = Modifier.weight(1f),
                        isGood = result.falsePositives == 0
                    )
                    MetricChip(
                        title = "Grid Bounds",
                        value = if (result.visualGridDetected) "Locked" else "Missed",
                        subtitle = "Err: %.1fpx".format(result.gridBoundsErrorPx),
                        modifier = Modifier.weight(1f),
                        isGood = result.visualGridDetected && result.gridBoundsErrorPx < 15f
                    )
                    MetricChip(
                        title = "Rule Validity",
                        value = if (result.isSudokuRulesValid) "0 Conflicts" else "${result.conflictCount} Err",
                        subtitle = "Sudoku Laws",
                        modifier = Modifier.weight(1f),
                        isGood = result.isSudokuRulesValid
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Reconstructed Scanned Board View
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Scanned Puzzle Output (Rendered Board)",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )

                        SudokuGridView(
                            board = result.resultBoard,
                            selectedIndex = -1,
                            conflictedIndices = emptySet(),
                            onCellSelected = {}
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ScanAccuracySuiteReportCard(
    results: List<AccuracyMetricResult>,
    modifier: Modifier = Modifier
) {
    val totalExpected = results.sumOf { it.totalExpectedClues }
    val totalMatches = results.sumOf { it.exactMatches }
    val totalFalsePositives = results.sumOf { it.falsePositives }
    val avgAccuracy = if (totalExpected > 0) (totalMatches.toFloat() / totalExpected) * 100f else 0f
    val allPassed = results.all { it.passed }

    MyApplicationTheme {
        Surface(
            modifier = modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background)
                .padding(16.dp),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
            ) {
                // Suite Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Sudoku OCR Regression Test Suite",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = "Bundled Test Images & Roborazzi Screenshot Verification",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    val bannerColor = if (allPassed) Color(0xFF2E7D32) else Color(0xFFC62828)
                    Surface(
                        color = bannerColor.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(16.dp),
                        border = androidx.compose.foundation.BorderStroke(1.5.dp, bannerColor)
                    ) {
                        Text(
                            text = if (allPassed) "ALL SUITE TESTS PASSED" else "REGRESSION DETECTED",
                            color = bannerColor,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Suite Aggregate Stats
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    MetricChip(
                        title = "Overall Accuracy",
                        value = "%.1f%%".format(avgAccuracy),
                        subtitle = "$totalMatches/$totalExpected Clues",
                        modifier = Modifier.weight(1f),
                        isGood = avgAccuracy >= 98f
                    )
                    MetricChip(
                        title = "Bundled Images",
                        value = "${results.size} Puzzles",
                        subtitle = "Easy / Med / Hard / Expert",
                        modifier = Modifier.weight(1f),
                        isGood = true
                    )
                    MetricChip(
                        title = "False Positive Rate",
                        value = "$totalFalsePositives",
                        subtitle = "0 Target",
                        modifier = Modifier.weight(1f),
                        isGood = totalFalsePositives == 0
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(14.dp))

                // Per-Puzzle Breakdown
                Text(
                    text = "Individual Test Case Results",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )

                Spacer(modifier = Modifier.height(10.dp))

                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    for (res in results) {
                        PuzzleResultRow(res)
                    }
                }
            }
        }
    }
}

@Composable
private fun PuzzleResultRow(res: AccuracyMetricResult) {
    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = res.puzzleTitle,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Expected ${res.totalExpectedClues} clues • Grid Bounds: [${res.expectedGridBounds.toShortString()}]",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { res.accuracyPercentage / 100f },
                    modifier = Modifier
                        .fillMaxWidth(0.85f)
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = if (res.passed) Color(0xFF2E7D32) else Color(0xFFC62828),
                    trackColor = Color.LightGray.copy(alpha = 0.5f)
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "%.1f%%".format(res.accuracyPercentage),
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (res.passed) Color(0xFF2E7D32) else Color(0xFFC62828)
                )
                Text(
                    text = if (res.passed) "PASSED" else "FAILED",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (res.passed) Color(0xFF2E7D32) else Color(0xFFC62828)
                )
            }
        }
    }
}

@Composable
private fun MetricChip(
    title: String,
    value: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    isGood: Boolean
) {
    val statusColor = if (isGood) Color(0xFF2E7D32) else Color(0xFFC62828)
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = statusColor
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                fontSize = 9.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
