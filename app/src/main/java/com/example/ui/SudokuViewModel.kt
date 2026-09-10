package com.example.ui

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.PuzzleRepository
import com.example.logic.SamplePuzzles
import com.example.logic.SudokuExporter
import com.example.logic.SudokuSolver
import com.example.logic.SudokuValidator
import com.example.model.PuzzleEntity
import com.example.model.SudokuBoard
import com.example.ocr.ImageUtils
import com.example.ocr.SudokuOcrEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

sealed interface ScanStatus {
    object Idle : ScanStatus
    object Scanning : ScanStatus
    data class Success(val detectedCount: Int, val message: String) : ScanStatus
    data class Error(val message: String) : ScanStatus
}

class SudokuViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: PuzzleRepository

    init {
        val db = AppDatabase.getDatabase(application)
        repository = PuzzleRepository(db.puzzleDao())
    }

    val savedPuzzles: StateFlow<List<PuzzleEntity>> = repository.allPuzzles
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _currentBoard = MutableStateFlow(SudokuBoard.EMPTY)
    val currentBoard: StateFlow<SudokuBoard> = _currentBoard.asStateFlow()

    private val _initialScannedBoard = MutableStateFlow(SudokuBoard.EMPTY)

    private val _selectedIndex = MutableStateFlow<Int?>(0)
    val selectedIndex: StateFlow<Int?> = _selectedIndex.asStateFlow()

    private val _conflictedIndices = MutableStateFlow<Set<Int>>(emptySet())
    val conflictedIndices: StateFlow<Set<Int>> = _conflictedIndices.asStateFlow()

    private val _solveResult = MutableStateFlow<SudokuSolver.SolveResult?>(null)
    val solveResult: StateFlow<SudokuSolver.SolveResult?> = _solveResult.asStateFlow()

    private val _scanStatus = MutableStateFlow<ScanStatus>(ScanStatus.Idle)
    val scanStatus: StateFlow<ScanStatus> = _scanStatus.asStateFlow()

    private val _currentImageUri = MutableStateFlow<Uri?>(null)
    val currentImageUri: StateFlow<Uri?> = _currentImageUri.asStateFlow()

    private val _currentPuzzleTitle = MutableStateFlow("Sudoku Scan")
    val currentPuzzleTitle: StateFlow<String> = _currentPuzzleTitle.asStateFlow()

    fun selectCell(index: Int) {
        _selectedIndex.value = if (index in 0..80) index else null
    }

    fun setNumberInSelectedCell(number: Int) {
        val idx = _selectedIndex.value ?: return
        val current = _currentBoard.value
        val updated = current.setCell(idx, number)
        _currentBoard.value = updated
        recomputeValidationAndSolve(updated)
    }

    fun clearSelectedCell() {
        val idx = _selectedIndex.value ?: return
        val current = _currentBoard.value
        val updated = current.setCell(idx, 0)
        _currentBoard.value = updated
        recomputeValidationAndSolve(updated)
    }

    fun loadSample(index: Int = 0) {
        val sample = SamplePuzzles.samples.getOrElse(index) { SamplePuzzles.samples.first() }
        val board = SudokuBoard.fromString(sample.sdm, markAsGiven = true)
        _initialScannedBoard.value = board
        _currentBoard.value = board
        _currentImageUri.value = null
        _currentPuzzleTitle.value = sample.title
        _scanStatus.value = ScanStatus.Success(
            detectedCount = board.clueCount,
            message = "Loaded sample: ${sample.title} (${board.clueCount} clues)"
        )
        recomputeValidationAndSolve(board)
        autoSavePuzzle()
    }

    fun loadSavedPuzzle(puzzle: PuzzleEntity) {
        val board = puzzle.toBoard()
        _initialScannedBoard.value = board
        _currentBoard.value = board
        _currentImageUri.value = null
        _currentPuzzleTitle.value = puzzle.title
        _scanStatus.value = ScanStatus.Success(
            detectedCount = board.clueCount,
            message = "Loaded ${puzzle.title}"
        )
        recomputeValidationAndSolve(board)
    }

    fun processImageUri(uri: Uri) {
        viewModelScope.launch {
            _scanStatus.value = ScanStatus.Scanning
            _currentImageUri.value = uri

            try {
                val context = getApplication<Application>()
                val bitmap = withContext(Dispatchers.IO) {
                    ImageUtils.loadScaledBitmap(context, uri)
                }

                if (bitmap == null) {
                    _scanStatus.value = ScanStatus.Error("Failed to decode image.")
                    return@launch
                }

                val ocrResult = SudokuOcrEngine.recognizeSudoku(bitmap)
                if (ocrResult.detectedCount > 0) {
                    _initialScannedBoard.value = ocrResult.board
                    _currentBoard.value = ocrResult.board
                    _currentPuzzleTitle.value = "Scanned Puzzle (${ocrResult.detectedCount} clues)"
                    _scanStatus.value = ScanStatus.Success(
                        detectedCount = ocrResult.detectedCount,
                        message = ocrResult.message
                    )
                    recomputeValidationAndSolve(ocrResult.board)
                    autoSavePuzzle()
                } else {
                    _scanStatus.value = ScanStatus.Error(ocrResult.message)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _scanStatus.value = ScanStatus.Error("OCR failed: ${e.localizedMessage}")
            }
        }
    }

    fun processDirectBitmap(bitmap: Bitmap) {
        viewModelScope.launch {
            _scanStatus.value = ScanStatus.Scanning
            try {
                val ocrResult = SudokuOcrEngine.recognizeSudoku(bitmap)
                if (ocrResult.detectedCount > 0) {
                    _initialScannedBoard.value = ocrResult.board
                    _currentBoard.value = ocrResult.board
                    _currentPuzzleTitle.value = "Camera Scan (${ocrResult.detectedCount} clues)"
                    _scanStatus.value = ScanStatus.Success(
                        detectedCount = ocrResult.detectedCount,
                        message = ocrResult.message
                    )
                    recomputeValidationAndSolve(ocrResult.board)
                    autoSavePuzzle()
                } else {
                    _scanStatus.value = ScanStatus.Error(ocrResult.message)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _scanStatus.value = ScanStatus.Error("Scan failed: ${e.localizedMessage}")
            }
        }
    }

    fun resetToInitialScan() {
        val initial = _initialScannedBoard.value
        _currentBoard.value = initial
        recomputeValidationAndSolve(initial)
    }

    fun solvePuzzle() {
        viewModelScope.launch(Dispatchers.Default) {
            val board = _currentBoard.value
            val result = SudokuSolver.analyzeAndSolve(board)
            withContext(Dispatchers.Main) {
                when (result.solvability) {
                    SudokuSolver.Solvability.SOLVABLE_UNIQUE,
                    SudokuSolver.Solvability.SOLVABLE_MULTIPLE -> {
                        if (result.solutionBoard != null) {
                            _currentBoard.value = result.solutionBoard
                            recomputeValidationAndSolve(result.solutionBoard)
                            val msg = if (result.solvability == SudokuSolver.Solvability.SOLVABLE_UNIQUE) {
                                "Puzzle solved with unique solution!"
                            } else {
                                "Puzzle solved (multiple valid solutions exist)!"
                            }
                            Toast.makeText(getApplication(), msg, Toast.LENGTH_SHORT).show()
                        }
                    }
                    SudokuSolver.Solvability.INVALID_DUPLICATES -> {
                        Toast.makeText(
                            getApplication(),
                            "Cannot solve: Duplicate numbers detected on the board.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                    SudokuSolver.Solvability.UNSOLVABLE -> {
                        Toast.makeText(
                            getApplication(),
                            "Puzzle is unsolvable. Please check digits against original photo.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        }
    }

    fun deletePuzzle(puzzle: PuzzleEntity) {
        viewModelScope.launch {
            repository.deletePuzzle(puzzle)
        }
    }

    fun autoSavePuzzle() {
        viewModelScope.launch {
            val board = _currentBoard.value
            if (board.clueCount > 0) {
                val entity = PuzzleEntity.fromBoard(
                    title = _currentPuzzleTitle.value,
                    board = board
                )
                repository.insertPuzzle(entity)
            }
        }
    }

    private fun recomputeValidationAndSolve(board: SudokuBoard) {
        val validation = SudokuValidator.findConflicts(board)
        _conflictedIndices.value = validation.conflictedIndices

        if (validation.isValid && board.clueCount >= 10) {
            viewModelScope.launch(Dispatchers.Default) {
                val result = SudokuSolver.analyzeAndSolve(board)
                _solveResult.value = result
            }
        } else {
            _solveResult.value = null
        }
    }

    // Export helpers
    fun exportToStorageUri(uri: Uri, format: SudokuExporter.ExportFormat) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val context = getApplication<Application>()
                val content = when (format) {
                    SudokuExporter.ExportFormat.OPEN_SUDOKU -> SudokuExporter.toOpenSudokuXml(
                        _currentBoard.value,
                        _currentPuzzleTitle.value
                    )
                    SudokuExporter.ExportFormat.SDM -> SudokuExporter.toSdmText(_currentBoard.value)
                }

                context.contentResolver.openOutputStream(uri)?.use { stream ->
                    stream.write(content.toByteArray(Charsets.UTF_8))
                }

                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        context,
                        "Successfully exported ${format.displayName} to device storage!",
                        Toast.LENGTH_LONG
                    ).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        getApplication(),
                        "Export failed: ${e.localizedMessage}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    fun shareExport(context: Context, format: SudokuExporter.ExportFormat) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val content = when (format) {
                    SudokuExporter.ExportFormat.OPEN_SUDOKU -> SudokuExporter.toOpenSudokuXml(
                        _currentBoard.value,
                        _currentPuzzleTitle.value
                    )
                    SudokuExporter.ExportFormat.SDM -> SudokuExporter.toSdmText(_currentBoard.value)
                }

                val fileName = SudokuExporter.generateDefaultFileName(format)
                val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
                val exportFile = File(exportDir, fileName)
                FileOutputStream(exportFile).use { it.write(content.toByteArray(Charsets.UTF_8)) }

                val authority = "${context.packageName}.fileprovider"
                val fileUri = FileProvider.getUriForFile(context, authority, exportFile)

                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = format.mimeType
                    putExtra(Intent.EXTRA_STREAM, fileUri)
                    putExtra(Intent.EXTRA_SUBJECT, "Sudoku Puzzle Export")
                    putExtra(Intent.EXTRA_TEXT, "Exported Sudoku puzzle for OpenSudoku.")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }

                withContext(Dispatchers.Main) {
                    context.startActivity(Intent.createChooser(intent, "Export via OpenSudoku / Apps"))
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Share failed: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun copyToClipboard(context: Context, format: SudokuExporter.ExportFormat) {
        val content = when (format) {
            SudokuExporter.ExportFormat.OPEN_SUDOKU -> SudokuExporter.toOpenSudokuXml(
                _currentBoard.value,
                _currentPuzzleTitle.value
            )
            SudokuExporter.ExportFormat.SDM -> _currentBoard.value.toSdmString('0')
        }

        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Sudoku Puzzle", content)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, "Copied ${format.displayName} to clipboard!", Toast.LENGTH_SHORT).show()
    }
}
