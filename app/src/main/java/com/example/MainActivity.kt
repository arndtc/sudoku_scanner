package com.example

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.example.logic.SudokuExporter
import com.example.ui.HomeScreen
import com.example.ui.SudokuEditorScreen
import com.example.ui.SudokuViewModel
import com.example.ui.components.ExportDialog
import com.example.ui.theme.MyApplicationTheme

enum class AppScreen {
    HOME,
    EDITOR
}

class MainActivity : ComponentActivity() {

    private val viewModel: SudokuViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MyApplicationTheme {
                MainContent(viewModel = viewModel)
            }
        }
    }
}

@Composable
fun MainContent(viewModel: SudokuViewModel) {
    val context = LocalContext.current
    var currentScreen by remember { mutableStateOf(AppScreen.HOME) }
    var showExportDialog by remember { mutableStateOf(false) }
    var pendingExportFormat by remember { mutableStateOf(SudokuExporter.ExportFormat.OPEN_SUDOKU) }

    // Launcher for saving export file to device storage
    val createDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/*")
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.exportToStorageUri(uri, pendingExportFormat)
        }
    }

    // Main App Navigation between Home & Graphical Sudoku Editor
    AnimatedContent(
        targetState = currentScreen,
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        label = "screen_transition",
        modifier = Modifier.fillMaxSize()
    ) { screen ->
        when (screen) {
            AppScreen.HOME -> {
                HomeScreen(
                    viewModel = viewModel,
                    onNavigateToEditor = { currentScreen = AppScreen.EDITOR }
                )
            }
            AppScreen.EDITOR -> {
                SudokuEditorScreen(
                    viewModel = viewModel,
                    onBack = { currentScreen = AppScreen.HOME },
                    onOpenExport = { showExportDialog = true }
                )
            }
        }
    }

    // Export Dialog
    if (showExportDialog) {
        ExportDialog(
            onDismiss = { showExportDialog = false },
            onSaveToStorage = { format ->
                pendingExportFormat = format
                showExportDialog = false
                val defaultFileName = SudokuExporter.generateDefaultFileName(format)
                createDocumentLauncher.launch(defaultFileName)
            },
            onShare = { format ->
                showExportDialog = false
                viewModel.shareExport(context, format)
            },
            onCopyToClipboard = { format ->
                showExportDialog = false
                viewModel.copyToClipboard(context, format)
            }
        )
    }
}
