package com.example.ui

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.RectF
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
import com.example.ocr.DecodeResult
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
    val initialScannedBoard: StateFlow<SudokuBoard> = _initialScannedBoard.asStateFlow()

    private val _latestDiagnosticRecord = MutableStateFlow<com.example.diagnostics.ScanDiagnosticRecord?>(null)
    val latestDiagnosticRecord: StateFlow<com.example.diagnostics.ScanDiagnosticRecord?> = _latestDiagnosticRecord.asStateFlow()

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

    // Interactive Crop State
    private val _cropSourceBitmap = MutableStateFlow<Bitmap?>(null)
    val cropSourceBitmap: StateFlow<Bitmap?> = _cropSourceBitmap.asStateFlow()

    private val _cropSourceUri = MutableStateFlow<Uri?>(null)
    val cropSourceUri: StateFlow<Uri?> = _cropSourceUri.asStateFlow()

    private val _cropRect = MutableStateFlow(RectF(0.08f, 0.08f, 0.92f, 0.92f))
    val cropRect: StateFlow<RectF> = _cropRect.asStateFlow()

    private val _cropRotation = MutableStateFlow(0)
    val cropRotation: StateFlow<Int> = _cropRotation.asStateFlow()

    private val _isAutoDetecting = MutableStateFlow(false)
    val isAutoDetecting: StateFlow<Boolean> = _isAutoDetecting.asStateFlow()

    private val _cropDetectionMessage = MutableStateFlow<String?>(null)
    val cropDetectionMessage: StateFlow<String?> = _cropDetectionMessage.asStateFlow()

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
        _latestDiagnosticRecord.value = com.example.diagnostics.ScanDiagnosticRecord(
            timestamp = System.currentTimeMillis(),
            imageWidth = 900,
            imageHeight = 900,
            initialScannedBoard = board,
            visualGridDetected = true,
            ocrPassUsed = "Bundled Sample (${sample.title})",
            placedCluesCount = board.clueCount
        )
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
                val decodeResult = withContext(Dispatchers.IO) {
                    ImageUtils.decodeImage(context, uri)
                }

                val bitmap = when (decodeResult) {
                    is DecodeResult.Success -> decodeResult.bitmap
                    is DecodeResult.Error -> {
                        _scanStatus.value = ScanStatus.Error(decodeResult.message)
                        return@launch
                    }
                }

                val ocrResult = SudokuOcrEngine.recognizeSudoku(bitmap)
                val diagRecord = com.example.diagnostics.ScanDiagnosticRecord(
                    timestamp = System.currentTimeMillis(),
                    imageWidth = bitmap.width,
                    imageHeight = bitmap.height,
                    cropRect = null,
                    initialScannedBoard = ocrResult.board,
                    visualGridDetected = ocrResult.diagnostics?.visualGridDetected ?: false,
                    gridBounds = ocrResult.gridBounds,
                    ocrPassUsed = ocrResult.diagnostics?.passName ?: "Standard",
                    rawCandidates = ocrResult.diagnostics?.rawCandidates ?: emptyList(),
                    placedCluesCount = ocrResult.detectedCount,
                    imageUri = uri,
                    gridLeft = ocrResult.diagnostics?.gridLeft ?: 0f,
                    gridTop = ocrResult.diagnostics?.gridTop ?: 0f,
                    cellWidth = ocrResult.diagnostics?.cellWidth ?: 0f,
                    cellHeight = ocrResult.diagnostics?.cellHeight ?: 0f
                )
                _latestDiagnosticRecord.value = diagRecord

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
                _scanStatus.value = ScanStatus.Error("OCR failed: ${e.localizedMessage ?: "Unknown error"}")
            }
        }
    }

    fun loadSamplePuzzle(onLoaded: () -> Unit = {}) {
        viewModelScope.launch {
            _scanStatus.value = ScanStatus.Scanning
            val context = getApplication<Application>()
            val sampleUri = withContext(Dispatchers.IO) {
                ImageUtils.createSamplePuzzleImage(context)
            }
            if (sampleUri != null) {
                processImageUri(sampleUri)
                onLoaded()
            } else {
                _scanStatus.value = ScanStatus.Error("Unable to prepare sample puzzle photo.")
            }
        }
    }

    fun rescanCurrentImage() {
        val uri = _currentImageUri.value ?: return
        processImageUri(uri)
    }

    /**
     * Prepares a selected image Uri for cropping. Loads bitmap, initiates
     * auto-crop boundary detection in the background, and triggers callback to open CropScreen.
     */
    fun prepareImageForCrop(uri: Uri, onReady: () -> Unit) {
        viewModelScope.launch {
            _scanStatus.value = ScanStatus.Scanning
            _cropSourceUri.value = uri
            _cropRotation.value = 0
            _cropDetectionMessage.value = null

            val context = getApplication<Application>()
            val decodeResult = withContext(Dispatchers.IO) {
                ImageUtils.decodeImage(context, uri)
            }

            val bitmap = when (decodeResult) {
                is DecodeResult.Success -> decodeResult.bitmap
                is DecodeResult.Error -> {
                    _scanStatus.value = ScanStatus.Error(decodeResult.message)
                    return@launch
                }
            }

            _cropSourceBitmap.value = bitmap
            _cropRect.value = RectF(0.08f, 0.08f, 0.92f, 0.92f)
            _scanStatus.value = ScanStatus.Idle
            onReady()

            // Automatically run puzzle auto-detection
            runAutoDetectCrop()
        }
    }

    /**
     * Prepares the current or original photo for re-cropping from the editor.
     */
    fun prepareExistingImageForCrop(onReady: () -> Unit) {
        val originalUri = _cropSourceUri.value ?: _currentImageUri.value
        if (originalUri != null) {
            prepareImageForCrop(originalUri, onReady)
        } else {
            val existingBitmap = _cropSourceBitmap.value
            if (existingBitmap != null) {
                onReady()
                runAutoDetectCrop()
            }
        }
    }

    fun setCropRect(rect: RectF) {
        _cropRect.value = rect
    }

    /**
     * Runs auto-detection on the current crop bitmap to snap the crop box
     * to the 9x9 Sudoku grid.
     */
    fun runAutoDetectCrop() {
        val bitmap = _cropSourceBitmap.value ?: return
        viewModelScope.launch {
            _isAutoDetecting.value = true
            _cropDetectionMessage.value = "Detecting Sudoku grid..."
            try {
                val detectedRect = SudokuOcrEngine.autoDetectSudokuBoundingBox(bitmap)
                _cropRect.value = detectedRect
                _cropDetectionMessage.value = "Sudoku grid auto-detected!"
            } catch (e: Exception) {
                _cropDetectionMessage.value = "Default crop applied."
            } finally {
                _isAutoDetecting.value = false
            }
        }
    }

    /**
     * Snaps current crop selection to a 1:1 square ratio centered within current selection.
     */
    fun setSquareCrop() {
        val current = _cropRect.value
        val bitmap = _cropSourceBitmap.value ?: return
        val imgWidth = bitmap.width.toFloat()
        val imgHeight = bitmap.height.toFloat()

        val pixelW = (current.right - current.left) * imgWidth
        val pixelH = (current.bottom - current.top) * imgHeight
        val side = kotlin.math.max(pixelW, pixelH)

        val cx = ((current.left + current.right) / 2f) * imgWidth
        val cy = ((current.top + current.bottom) / 2f) * imgHeight

        var left = cx - side / 2f
        var right = cx + side / 2f
        var top = cy - side / 2f
        var bottom = cy + side / 2f

        if (left < 0f) {
            right = kotlin.math.min(imgWidth, right - left)
            left = 0f
        }
        if (right > imgWidth) {
            val overflow = right - imgWidth
            left = kotlin.math.max(0f, left - overflow)
            right = imgWidth
        }
        if (top < 0f) {
            bottom = kotlin.math.min(imgHeight, bottom - top)
            top = 0f
        }
        if (bottom > imgHeight) {
            val overflow = bottom - imgHeight
            top = kotlin.math.max(0f, top - overflow)
            bottom = imgHeight
        }

        _cropRect.value = RectF(
            (left / imgWidth).coerceIn(0f, 0.9f),
            (top / imgHeight).coerceIn(0f, 0.9f),
            (right / imgWidth).coerceIn(0.1f, 1f),
            (bottom / imgHeight).coerceIn(0.1f, 1f)
        )
        _cropDetectionMessage.value = "Set to 1:1 Square"
    }

    /**
     * Resets crop selection to full image.
     */
    fun setFullCrop() {
        _cropRect.value = RectF(0f, 0f, 1f, 1f)
        _cropDetectionMessage.value = "Full image selected"
    }

    /**
     * Nudges crop box horizontally and vertically by normalized delta amounts.
     */
    fun nudgeCrop(dx: Float, dy: Float) {
        val curr = _cropRect.value
        val width = curr.width()
        val height = curr.height()
        val newLeft = (curr.left + dx).coerceIn(0f, 1f - width)
        val newTop = (curr.top + dy).coerceIn(0f, 1f - height)
        val newRight = (newLeft + width).coerceIn(0.1f, 1f)
        val newBottom = (newTop + height).coerceIn(0.1f, 1f)
        _cropRect.value = RectF(newLeft, newTop, newRight, newBottom)
    }

    /**
     * Expands (scaleDelta > 0) or shrinks (scaleDelta < 0) the crop box centered.
     */
    fun scaleCrop(scaleDelta: Float) {
        val curr = _cropRect.value
        val cx = curr.centerX()
        val cy = curr.centerY()
        val halfW = (curr.width() * (1f + scaleDelta) / 2f).coerceIn(0.06f, 0.5f)
        val halfH = (curr.height() * (1f + scaleDelta) / 2f).coerceIn(0.06f, 0.5f)

        var l = (cx - halfW).coerceIn(0f, 1f)
        var r = (cx + halfW).coerceIn(0f, 1f)
        var t = (cy - halfH).coerceIn(0f, 1f)
        var b = (cy + halfH).coerceIn(0f, 1f)

        if (r - l < 0.12f) r = (l + 0.12f).coerceAtMost(1f)
        if (b - t < 0.12f) b = (t + 0.12f).coerceAtMost(1f)

        _cropRect.value = RectF(l, t, r, b)
    }

    /**
     * Rotates current crop bitmap 90 degrees clockwise.
     */
    fun rotateCropImageClockwise() {
        val current = _cropSourceBitmap.value ?: return
        val rotated = ImageUtils.rotateBitmap(current, 90f)
        _cropSourceBitmap.value = rotated
        _cropRotation.value = (_cropRotation.value + 90) % 360
        runAutoDetectCrop()
    }

    /**
     * Crops the bitmap according to the current selection, saves it to cache,
     * processes OCR on the clean cropped image, and completes navigation.
     */
    fun applyCropAndScan(onComplete: () -> Unit) {
        val bitmap = _cropSourceBitmap.value ?: return
        viewModelScope.launch {
            _scanStatus.value = ScanStatus.Scanning
            onComplete()

            try {
                val context = getApplication<Application>()
                val croppedBitmap = withContext(Dispatchers.Default) {
                    ImageUtils.cropBitmap(bitmap, _cropRect.value)
                }

                val croppedUri = withContext(Dispatchers.IO) {
                    ImageUtils.saveCroppedBitmap(context, croppedBitmap)
                }
                if (croppedUri != null) {
                    _currentImageUri.value = croppedUri
                }

                val ocrResult = SudokuOcrEngine.recognizeSudoku(croppedBitmap)
                val diagRecord = com.example.diagnostics.ScanDiagnosticRecord(
                    timestamp = System.currentTimeMillis(),
                    imageWidth = croppedBitmap.width,
                    imageHeight = croppedBitmap.height,
                    cropRect = _cropRect.value,
                    initialScannedBoard = ocrResult.board,
                    visualGridDetected = ocrResult.diagnostics?.visualGridDetected ?: false,
                    gridBounds = ocrResult.gridBounds,
                    ocrPassUsed = ocrResult.diagnostics?.passName ?: "Standard",
                    rawCandidates = ocrResult.diagnostics?.rawCandidates ?: emptyList(),
                    placedCluesCount = ocrResult.detectedCount,
                    imageUri = croppedUri ?: _currentImageUri.value,
                    gridLeft = ocrResult.diagnostics?.gridLeft ?: 0f,
                    gridTop = ocrResult.diagnostics?.gridTop ?: 0f,
                    cellWidth = ocrResult.diagnostics?.cellWidth ?: 0f,
                    cellHeight = ocrResult.diagnostics?.cellHeight ?: 0f
                )
                _latestDiagnosticRecord.value = diagRecord

                if (ocrResult.detectedCount > 0) {
                    _initialScannedBoard.value = ocrResult.board
                    _currentBoard.value = ocrResult.board
                    _currentPuzzleTitle.value = "Cropped Scan (${ocrResult.detectedCount} clues)"
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
                _scanStatus.value = ScanStatus.Error("Crop & OCR failed: ${e.localizedMessage}")
            }
        }
    }

    fun dismissScanStatus() {
        _scanStatus.value = ScanStatus.Idle
    }

    fun processDirectBitmap(bitmap: Bitmap) {
        viewModelScope.launch {
            _scanStatus.value = ScanStatus.Scanning
            try {
                val ocrResult = SudokuOcrEngine.recognizeSudoku(bitmap)
                val diagRecord = com.example.diagnostics.ScanDiagnosticRecord(
                    timestamp = System.currentTimeMillis(),
                    imageWidth = bitmap.width,
                    imageHeight = bitmap.height,
                    cropRect = null,
                    initialScannedBoard = ocrResult.board,
                    visualGridDetected = ocrResult.diagnostics?.visualGridDetected ?: false,
                    gridBounds = ocrResult.gridBounds,
                    ocrPassUsed = ocrResult.diagnostics?.passName ?: "Standard",
                    rawCandidates = ocrResult.diagnostics?.rawCandidates ?: emptyList(),
                    placedCluesCount = ocrResult.detectedCount,
                    imageUri = _currentImageUri.value,
                    gridLeft = ocrResult.diagnostics?.gridLeft ?: 0f,
                    gridTop = ocrResult.diagnostics?.gridTop ?: 0f,
                    cellWidth = ocrResult.diagnostics?.cellWidth ?: 0f,
                    cellHeight = ocrResult.diagnostics?.cellHeight ?: 0f
                )
                _latestDiagnosticRecord.value = diagRecord

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

    fun getDiagnosticRecordOrFallback(): com.example.diagnostics.ScanDiagnosticRecord {
        return _latestDiagnosticRecord.value ?: com.example.diagnostics.ScanDiagnosticRecord(
            timestamp = System.currentTimeMillis(),
            imageWidth = 0,
            imageHeight = 0,
            initialScannedBoard = _initialScannedBoard.value.takeIf { it.clueCount > 0 } ?: _currentBoard.value,
            placedCluesCount = _initialScannedBoard.value.clueCount,
            imageUri = _currentImageUri.value
        )
    }

    fun submitFeedbackToGitHub(
        context: Context,
        repo: String = com.example.diagnostics.FeedbackHelper.DEFAULT_GITHUB_REPO,
        userNotes: String? = null
    ) {
        val record = getDiagnosticRecordOrFallback()
        com.example.diagnostics.FeedbackHelper.openGitHubIssue(
            context = context,
            record = record,
            currentBoard = _currentBoard.value,
            repo = repo,
            userNotes = userNotes
        )
    }

    fun shareDiagnosticPackage(context: Context, userNotes: String? = null) {
        val record = getDiagnosticRecordOrFallback()
        com.example.diagnostics.FeedbackHelper.shareDiagnosticPackage(
            context = context,
            record = record,
            currentBoard = _currentBoard.value,
            userNotes = userNotes
        )
    }

    fun copyDiagnosticReport(context: Context, userNotes: String? = null) {
        val record = getDiagnosticRecordOrFallback()
        val text = record.generateMarkdownReport(
            currentBoard = _currentBoard.value,
            userNotes = userNotes
        )
        com.example.diagnostics.FeedbackHelper.copyToClipboard(context, text, showToast = true)
    }
}
