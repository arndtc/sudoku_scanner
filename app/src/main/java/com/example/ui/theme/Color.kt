package com.example.ui.theme

import androidx.compose.ui.graphics.Color

// Primary brand colors (Indigo & Slate)
val Indigo600 = Color(0xFF4F46E5)
val Indigo700 = Color(0xFF4338CA)
val Indigo400 = Color(0xFF818CF8)
val Indigo200 = Color(0xFFC7D2FE)

val Slate900 = Color(0xFF0F172A)
val Slate800 = Color(0xFF1E293B)
val Slate700 = Color(0xFF334155)
val Slate600 = Color(0xFF475569)
val Slate300 = Color(0xFFCBD5E1)
val Slate200 = Color(0xFFE2E8F0)
val Slate100 = Color(0xFFF1F5F9)
val Slate50 = Color(0xFFF8FAFC)

val Amber500 = Color(0xFFF59E0B)
val Amber100 = Color(0xFFFEF3C7)
val Amber900 = Color(0xFF78350F)

val Red500 = Color(0xFFEF4444)
val Red100 = Color(0xFFFEE2E2)
val Red900 = Color(0xFF7F1D1D)

val Emerald500 = Color(0xFF10B981)
val Emerald100 = Color(0xFFD1FAE5)

// Sudoku Board Specific Styling
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

val LightSudokuColors = SudokuBoardColors(
    gridBackground = Color.White,
    outerBorder = Slate900,
    subgridBorder = Slate800,
    cellBorder = Slate200,
    selectedCell = Color(0xFFC7D2FE), // Soft indigo
    relatedCell = Color(0xFFEEF2FF), // Very soft indigo tint
    sameDigitCell = Color(0xFFFEF3C7), // Warm amber highlight
    conflictCell = Color(0xFFFEE2E2), // Soft red alert
    clueText = Slate900,
    userText = Color(0xFF2563EB), // Vibrant blue for manual/edited digits
    errorText = Color(0xFFDC2626)
)

val DarkSudokuColors = SudokuBoardColors(
    gridBackground = Color(0xFF1E293B),
    outerBorder = Color(0xFF94A3B8),
    subgridBorder = Color(0xFF64748B),
    cellBorder = Color(0xFF334155),
    selectedCell = Color(0xFF3730A3), // Deep indigo
    relatedCell = Color(0xFF1E1B4B).copy(alpha = 0.6f),
    sameDigitCell = Color(0xFF78350F).copy(alpha = 0.7f),
    conflictCell = Color(0xFF7F1D1D).copy(alpha = 0.75f),
    clueText = Color(0xFFF8FAFC),
    userText = Color(0xFF60A5FA), // Bright sky blue
    errorText = Color(0xFFF87171)
)
