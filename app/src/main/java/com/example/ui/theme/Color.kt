package com.example.ui.theme

import androidx.compose.ui.graphics.Color

// Open Sudoku Theme Palette
// Dark background and slate surfaces
val OpenSudokuBackground = Color(0xFF13171A)
val OpenSudokuHeader = Color(0xFF20252B)
val OpenSudokuSurface = Color(0xFF192025)
val OpenSudokuSurfaceVariant = Color(0xFF222B31)
val OpenSudokuOutline = Color(0xFF2E3940)

// Iconic Open Sudoku Teal and Cyan Accents
val OpenSudokuTeal = Color(0xFF00897B)
val OpenSudokuTealBright = Color(0xFF26A69A)
val OpenSudokuTealDark = Color(0xFF004D40)
val OpenSudokuCyan = Color(0xFF00ACC1)
val OpenSudokuCyanLight = Color(0xFF80DEEA)

// Text and Highlights
val OpenSudokuTextPrimary = Color(0xFFECEFF1)
val OpenSudokuTextSecondary = Color(0xFF90A4AE)
val OpenSudokuError = Color(0xFFE53935)
val OpenSudokuWarning = Color(0xFFFFA000)

// Legacy alias compatibility
val Indigo600 = OpenSudokuTeal
val Indigo700 = OpenSudokuTealDark
val Indigo400 = OpenSudokuTealBright
val Indigo200 = OpenSudokuCyanLight

val Slate900 = OpenSudokuBackground
val Slate800 = OpenSudokuSurface
val Slate700 = OpenSudokuSurfaceVariant
val Slate600 = OpenSudokuOutline
val Slate300 = OpenSudokuTextSecondary
val Slate200 = OpenSudokuTextPrimary
val Slate100 = Color(0xFFF1F5F9)
val Slate50 = Color(0xFFF8FAFC)

val Amber500 = OpenSudokuWarning
val Amber100 = Color(0xFFFEF3C7)
val Amber900 = Color(0xFF78350F)

val Red500 = OpenSudokuError
val Red100 = Color(0xFFFEE2E2)
val Red900 = Color(0xFF7F1D1D)

val Emerald500 = Color(0xFF10B981)
val Emerald100 = Color(0xFFD1FAE5)

// Sudoku Board Specific Styling (Grid styling directly matching Open Sudoku screenshot)
data class SudokuBoardColors(
    val gridBackground: Color,
    val outerBorder: Color,
    val subgridBorder: Color,
    val cellBorder: Color,
    val selectedCell: Color,
    val relatedCell: Color,
    val sameDigitCell: Color,
    val conflictCell: Color,
    val clueText: Color,
    val userText: Color,
    val errorText: Color
)

val OpenSudokuBoardColors = SudokuBoardColors(
    gridBackground = Color(0xFF0C1013), // Deep black-slate board background
    outerBorder = Color(0xFF00897B),    // Iconic Open Sudoku teal outer border
    subgridBorder = Color(0xFF00897B),  // Iconic Open Sudoku teal 3x3 box borders
    cellBorder = Color(0xFF0F3633),     // Subtle dark teal 1x1 cell grid lines
    selectedCell = Color(0xFF004D40),   // Dark teal selection highlight
    relatedCell = Color(0xFF102628),    // Soft row/col/box crosshair highlight
    sameDigitCell = Color(0xFF173836),  // Matching number highlight
    conflictCell = Color(0xFF4D1414),   // Deep red alert background
    clueText = Color(0xFFE0F2F1),       // High-contrast crisp light teal / white
    userText = Color(0xFF4DD0E1),       // Vivid sky cyan for user edits
    errorText = Color(0xFFEF5350)       // Red error digit
)

val LightSudokuColors = OpenSudokuBoardColors
val DarkSudokuColors = OpenSudokuBoardColors

