package com.example.diagnostics

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import com.example.model.SudokuBoard
import java.io.File
import java.io.FileOutputStream

object FeedbackHelper {

    const val DEFAULT_GITHUB_REPO = "arndtc/sudoku_scanner"

    /**
     * Cleans and normalizes any user-entered or default repository string (e.g.
     * "https://github.com/arndtc/sudoku_scanner" -> "arndtc/sudoku_scanner").
     */
    fun cleanRepoIdentifier(repoInput: String): String {
        val cleaned = repoInput.trim()
            .removePrefix("https://github.com/")
            .removePrefix("http://github.com/")
            .removePrefix("github.com/")
            .removeSuffix(".git")
            .removeSuffix("/")
        return if (cleaned.isBlank()) DEFAULT_GITHUB_REPO else cleaned
    }

    /**
     * Opens GitHub new issue page with pre-populated title and markdown report.
     * Also automatically copies the full diagnostic report to the clipboard.
     */
    fun openGitHubIssue(
        context: Context,
        record: ScanDiagnosticRecord,
        currentBoard: SudokuBoard,
        repo: String = DEFAULT_GITHUB_REPO,
        userNotes: String? = null
    ) {
        val targetRepo = cleanRepoIdentifier(repo)
        val fullReport = record.generateMarkdownReport(currentBoard, userNotes, targetRepo, isForUrl = false)
        copyToClipboard(context, fullReport, showToast = false)

        val url = record.generateGitHubIssueUrl(currentBoard, targetRepo, userNotes)
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            Toast.makeText(
                context,
                "Opening GitHub Issues for $targetRepo... All details pre-populated! (Full report copied to clipboard)",
                Toast.LENGTH_LONG
            ).show()
        } catch (e: Exception) {
            Toast.makeText(
                context,
                "Could not open browser. Full diagnostics copied to clipboard!",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    /**
     * Shares the diagnostic report text and the original scanned image together via Android Share Sheet.
     */
    fun shareDiagnosticPackage(
        context: Context,
        record: ScanDiagnosticRecord,
        currentBoard: SudokuBoard,
        userNotes: String? = null
    ) {
        try {
            val reportText = record.generateMarkdownReport(currentBoard, userNotes)
            val logFile = saveReportToCache(context, reportText)
            val logUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                logFile
            )

            val imageUri = record.imageUri

            val intent = if (imageUri != null) {
                Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                    type = "*/*"
                    putParcelableArrayListExtra(
                        Intent.EXTRA_STREAM,
                        arrayListOf(imageUri, logUri)
                    )
                    putExtra(Intent.EXTRA_SUBJECT, "Sudoku Scan Feedback & Diagnostics")
                    putExtra(Intent.EXTRA_TEXT, reportText)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            } else {
                Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, "Sudoku Scan Feedback & Diagnostics")
                    putExtra(Intent.EXTRA_TEXT, reportText)
                    putExtra(Intent.EXTRA_STREAM, logUri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            }

            val chooser = Intent.createChooser(intent, "Share Scan Feedback & Diagnostics").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        } catch (e: Exception) {
            e.printStackTrace()
            // Fallback: share plain text
            sharePlainText(context, record.generateMarkdownReport(currentBoard, userNotes))
        }
    }

    /**
     * Copies diagnostic report to the Android clipboard.
     */
    fun copyToClipboard(context: Context, text: String, showToast: Boolean = true) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val clip = ClipData.newPlainText("Sudoku Scan Diagnostics", text)
        clipboard?.setPrimaryClip(clip)
        if (showToast) {
            Toast.makeText(context, "Diagnostic report copied to clipboard!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun sharePlainText(context: Context, text: String) {
        try {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "Sudoku Scan Feedback")
                putExtra(Intent.EXTRA_TEXT, text)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(intent, "Share Feedback"))
        } catch (_: Exception) {}
    }

    private fun saveReportToCache(context: Context, content: String): File {
        val file = File(context.cacheDir, "sudoku_scan_diagnostics.txt")
        FileOutputStream(file).use { out ->
            out.write(content.toByteArray(Charsets.UTF_8))
        }
        return file
    }
}
