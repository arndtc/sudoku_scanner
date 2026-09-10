package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

val LocalSudokuColors = staticCompositionLocalOf { OpenSudokuBoardColors }

private val OpenSudokuColorScheme = darkColorScheme(
    primary = OpenSudokuTealBright,
    onPrimary = Color(0xFF00251A),
    primaryContainer = OpenSudokuTealDark,
    onPrimaryContainer = OpenSudokuCyanLight,
    secondary = OpenSudokuCyan,
    onSecondary = Color(0xFF00363A),
    secondaryContainer = Color(0xFF004D40),
    onSecondaryContainer = Color(0xFFB2EBF2),
    background = OpenSudokuBackground,
    onBackground = OpenSudokuTextPrimary,
    surface = OpenSudokuSurface,
    onSurface = OpenSudokuTextPrimary,
    surfaceVariant = OpenSudokuSurfaceVariant,
    onSurfaceVariant = OpenSudokuTextSecondary,
    outline = OpenSudokuOutline,
    outlineVariant = Color(0xFF1E282D),
    error = OpenSudokuError,
    onError = Color.White
)

val MaterialTheme.sudokuColors: SudokuBoardColors
    @Composable
    @ReadOnlyComposable
    get() = LocalSudokuColors.current

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = OpenSudokuColorScheme
    val sudokuColors = OpenSudokuBoardColors

    CompositionLocalProvider(LocalSudokuColors provides sudokuColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}

