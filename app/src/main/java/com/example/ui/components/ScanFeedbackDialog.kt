package com.example.ui.components

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Feedback
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.diagnostics.CellDifference
import com.example.diagnostics.DifferenceType
import com.example.diagnostics.FeedbackHelper
import com.example.diagnostics.ScanDiagnosticRecord
import com.example.model.SudokuBoard

/**
 * A dialog providing detailed guidance on what feedback helps improve OCR accuracy,
 * displaying detected missed numbers, and offering 1-tap submission to GitHub Issues or Android Share.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ScanFeedbackDialog(
    diagnosticRecord: ScanDiagnosticRecord,
    currentBoard: SudokuBoard,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var userNotes by remember { mutableStateOf("") }
    var gitHubRepo by remember { mutableStateOf(FeedbackHelper.DEFAULT_GITHUB_REPO) }
    var showRepoEditor by remember { mutableStateOf(false) }
    var showTelemetryDetails by remember { mutableStateOf(false) }

    val differences = remember(diagnosticRecord, currentBoard) {
        diagnosticRecord.computeDifferences(currentBoard)
    }
    val missedList = remember(differences) {
        differences.filter { it.type == DifferenceType.MISSED_DIGIT }
    }
    val incorrectList = remember(differences) {
        differences.filter { it.type == DifferenceType.INCORRECT_DIGIT }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = modifier
                .fillMaxWidth(0.95f)
                .heightIn(max = 700.dp)
                .testTag("scan_feedback_dialog"),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                // Header with title and close button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.BugReport,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .padding(8.dp)
                                    .size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Submit Scan Feedback",
                                fontWeight = FontWeight.Bold,
                                fontSize = 17.sp
                            )
                            Text(
                                text = "Help improve Sudoku OCR accuracy",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(32.dp).testTag("feedback_dialog_close_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close"
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Spacer(modifier = Modifier.height(12.dp))

                // Scrollable Content
                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Educational Guide: "What Helps Us Most"
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f)
                        )
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "What information is most helpful?",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            HelpTipItem(
                                number = "1",
                                title = "Fill in missed numbers on the grid",
                                desc = "When you tap empty cells to add missing numbers, the app records exactly which digits and coordinates were missed."
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            HelpTipItem(
                                number = "2",
                                title = "Attach or share the puzzle photo",
                                desc = "The original photo lets developers add it to the Roborazzi regression test suite so future updates never miss those numbers."
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            HelpTipItem(
                                number = "3",
                                title = "Automatic diagnostic telemetry",
                                desc = "Device model, resolution, grid line detection profiles, and candidate OCR bounding boxes are automatically bundled."
                            )
                        }
                    }

                    // Missed / Corrected Numbers Summary
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (missedList.isNotEmpty()) {
                                MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f)
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                            }
                        )
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Scan Differences",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (missedList.isNotEmpty()) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.outlineVariant
                                ) {
                                    Text(
                                        text = "${missedList.size} missed • ${differences.size} total diffs",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (missedList.isNotEmpty()) MaterialTheme.colorScheme.onTertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            if (differences.isEmpty()) {
                                Text(
                                    text = "No manual corrections made yet. If numbers were missed, you can tap cells on the board to enter them first!",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    for (d in differences.take(12)) {
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = MaterialTheme.colorScheme.surface,
                                            border = androidx.compose.foundation.BorderStroke(
                                                1.dp,
                                                if (d.type == DifferenceType.MISSED_DIGIT) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.outline
                                            )
                                        ) {
                                            Text(
                                                text = "${d.coordinateLabel}: ${if (d.scannedValue == 0) "missed" else "${d.scannedValue}"} → ${d.correctedValue}",
                                                fontSize = 11.sp,
                                                fontFamily = FontFamily.Monospace,
                                                fontWeight = FontWeight.Medium,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                            )
                                        }
                                    }
                                    if (differences.size > 12) {
                                        Text(
                                            text = "+${differences.size - 12} more",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(vertical = 3.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // ML Kit Telemetry & Confidence Overview
                    if (diagnosticRecord.rawCandidates.isNotEmpty()) {
                        val candidatesWithConf = remember(diagnosticRecord.rawCandidates) {
                            diagnosticRecord.rawCandidates.mapNotNull { it.confidence }
                        }
                        val avgConf = remember(candidatesWithConf) {
                            if (candidatesWithConf.isNotEmpty()) candidatesWithConf.average().toFloat() else null
                        }

                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                            )
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = "ML Kit Optical Telemetry",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp
                                        )
                                        Text(
                                            text = "${diagnosticRecord.rawCandidates.size} raw detections" +
                                                    (if (avgConf != null) " • Avg Conf: %.0f%%".format(avgConf * 100f) else ""),
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    TextButton(
                                        onClick = { showTelemetryDetails = !showTelemetryDetails },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = if (showTelemetryDetails) "Hide" else "Inspect",
                                            fontSize = 11.sp
                                        )
                                    }
                                }

                                if (showTelemetryDetails) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                                    Spacer(modifier = Modifier.height(8.dp))

                                    Column(
                                        verticalArrangement = Arrangement.spacedBy(4.dp),
                                        modifier = Modifier
                                            .heightIn(max = 160.dp)
                                            .verticalScroll(rememberScrollState())
                                    ) {
                                        for ((idx, c) in diagnosticRecord.rawCandidates.take(30).withIndex()) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text(
                                                    text = "#${idx + 1} '${c.originalChar}' (${c.digit})",
                                                    fontSize = 11.sp,
                                                    fontFamily = FontFamily.Monospace,
                                                    fontWeight = FontWeight.Medium
                                                )
                                                Text(
                                                    text = "Conf: ${c.formattedConfidence} • (${c.centerX.toInt()}, ${c.centerY.toInt()})",
                                                    fontSize = 10.sp,
                                                    fontFamily = FontFamily.Monospace,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                        if (diagnosticRecord.rawCandidates.size > 30) {
                                            Text(
                                                text = "+${diagnosticRecord.rawCandidates.size - 30} more in GitHub payload",
                                                fontSize = 10.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Optional User Notes
                    OutlinedTextField(
                        value = userNotes,
                        onValueChange = { userNotes = it },
                        label = { Text("Notes (optional)", fontSize = 12.sp) },
                        placeholder = { Text("e.g. Faint pencil marks, newspaper print, poor lighting...", fontSize = 12.sp) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("feedback_notes_field"),
                        minLines = 2,
                        maxLines = 3,
                        shape = RoundedCornerShape(10.dp)
                    )

                    // Target GitHub Repository (collapsible)
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Target: $gitHubRepo",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            TextButton(
                                onClick = { showRepoEditor = !showRepoEditor },
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                            ) {
                                Text(
                                    text = if (showRepoEditor) "Done" else "Change Repo",
                                    fontSize = 11.sp
                                )
                            }
                        }

                        if (showRepoEditor) {
                            OutlinedTextField(
                                value = gitHubRepo,
                                onValueChange = { gitHubRepo = it },
                                label = { Text("GitHub Repository (owner/repo)", fontSize = 11.sp) },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                shape = RoundedCornerShape(8.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Spacer(modifier = Modifier.height(12.dp))

                // Action Buttons
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Primary 1: Submit to GitHub Issues
                    Button(
                        onClick = {
                            FeedbackHelper.openGitHubIssue(
                                context = context,
                                record = diagnosticRecord,
                                currentBoard = currentBoard,
                                repo = gitHubRepo,
                                userNotes = userNotes.ifBlank { null }
                            )
                            onDismiss()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp)
                            .testTag("submit_github_issue_btn"),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Submit to GitHub Issues",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }

                    Text(
                        text = "Opens GitHub Issues with title, SDM board strings, and OCR telemetry auto-filled. (Requires GitHub login in browser if not already signed in; full report also copied to clipboard).",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 13.sp,
                        modifier = Modifier.padding(horizontal = 2.dp)
                    )

                    // Secondary Action Row: Share Photo + Logs & Copy
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilledTonalButton(
                            onClick = {
                                FeedbackHelper.shareDiagnosticPackage(
                                    context = context,
                                    record = diagnosticRecord,
                                    currentBoard = currentBoard,
                                    userNotes = userNotes.ifBlank { null }
                                )
                                onDismiss()
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(42.dp)
                                .testTag("share_feedback_package_btn"),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Share Photo & Logs", fontSize = 12.sp)
                        }

                        OutlinedButton(
                            onClick = {
                                val report = diagnosticRecord.generateMarkdownReport(
                                    currentBoard = currentBoard,
                                    userNotes = userNotes.ifBlank { null },
                                    repoTarget = gitHubRepo
                                )
                                FeedbackHelper.copyToClipboard(context, report, showToast = true)
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(42.dp)
                                .testTag("copy_diagnostics_btn"),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Copy Report", fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HelpTipItem(number: String, title: String, desc: String) {
    Row(verticalAlignment = Alignment.Top) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = number,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        Column {
            Text(
                text = title,
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = desc,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 15.sp
            )
        }
    }
}
