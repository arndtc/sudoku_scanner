package com.example.logic

import com.example.model.SudokuBoard
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object SudokuExporter {

    enum class ExportFormat(val extension: String, val mimeType: String, val displayName: String) {
        OPEN_SUDOKU("opensudoku", "application/xml", "OpenSudoku (.opensudoku)"),
        SDM("sdm", "text/plain", "SDM Text (.sdm)")
    }

    /**
     * Converts a SudokuBoard into an OpenSudoku XML string.
     * Compatible with OpenSudoku on Android (https://opensudoku.moire.org/).
     */
    fun toOpenSudokuXml(board: SudokuBoard, puzzleName: String = "Scanned Puzzle"): String {
        val sdm = board.toSdmString(emptyChar = '0')
        val timestamp = System.currentTimeMillis()
        val safeName = escapeXml(puzzleName)

        return buildString {
            appendLine("<?xml version=\"1.0\" encoding=\"utf-8\"?>")
            appendLine("<opensudoku version=\"2\">")
            appendLine("    <folder name=\"$safeName\">")
            appendLine("        <game created=\"$timestamp\" state=\"0\" time=\"0\" data=\"$sdm\" />")
            appendLine("    </folder>")
            appendLine("</opensudoku>")
        }
    }

    /**
     * Converts a SudokuBoard into an SDM format string.
     * SDM is an 81-character line representation where 0 represents empty cells.
     */
    fun toSdmText(board: SudokuBoard): String {
        return board.toSdmString(emptyChar = '0') + "\n"
    }

    /**
     * Generates a timestamped default filename for the export.
     */
    fun generateDefaultFileName(format: ExportFormat, baseName: String = "sudoku"): String {
        val dateStr = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        return "${baseName}_$dateStr.${format.extension}"
    }

    private fun escapeXml(text: String): String {
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }
}
