package com.example.testdata

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import com.example.model.SudokuBoard
import java.io.File
import java.io.FileOutputStream
import kotlin.math.roundToInt

data class BundledTestPuzzle(
    val id: String,
    val title: String,
    val difficulty: String,
    val sdm: String,
    val imageWidth: Int = 900,
    val imageHeight: Int = 900,
    val marginX: Float = 45f,
    val marginY: Float = 45f,
    val minAccuracyThreshold: Float = 0.95f
) {
    val groundTruthBoard: SudokuBoard by lazy {
        SudokuBoard.fromString(sdm, markAsGiven = true)
    }

    val expectedClueCount: Int by lazy {
        sdm.count { it in '1'..'9' }
    }
}

object BundledPuzzleDataset {

    val TEST_PUZZLES = listOf(
        BundledTestPuzzle(
            id = "newspaper_easy",
            title = "Daily Newspaper Easy",
            difficulty = "Easy",
            sdm = "000000010400000000020000000000050407008000300001090000300400200050100000000806000",
            imageWidth = 900,
            imageHeight = 900,
            marginX = 45f,
            marginY = 45f,
            minAccuracyThreshold = 1.0f
        ),
        BundledTestPuzzle(
            id = "classic_medium",
            title = "Classic Medium",
            difficulty = "Medium",
            sdm = "003020600900305001001806400008102900700000008006708200002609500800203009005010300",
            imageWidth = 900,
            imageHeight = 900,
            marginX = 45f,
            marginY = 45f,
            minAccuracyThreshold = 1.0f
        ),
        BundledTestPuzzle(
            id = "hard_challenge",
            title = "Hard Challenge With Margins",
            difficulty = "Hard",
            sdm = "800000000003600000070090200050007000000045700000100030001000068008500010090000400",
            imageWidth = 960,
            imageHeight = 960,
            marginX = 75f,
            marginY = 60f,
            minAccuracyThreshold = 1.0f
        ),
        BundledTestPuzzle(
            id = "expert_dense",
            title = "Modern Expert Dense",
            difficulty = "Expert",
            sdm = "530070000600195000098000060800060003400803001700020006060000280000419005000080079",
            imageWidth = 900,
            imageHeight = 900,
            marginX = 36f,
            marginY = 36f,
            minAccuracyThreshold = 1.0f
        )
    )

    /**
     * Renders a high-fidelity Sudoku puzzle bitmap with clear grid lines, 3x3 block borders,
     * and crisp digit glyphs.
     */
    fun renderPuzzleBitmap(puzzle: BundledTestPuzzle): Bitmap {
        val w = puzzle.imageWidth
        val h = puzzle.imageHeight
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)

        val gridSpanX = w - 2 * puzzle.marginX
        val gridSpanY = h - 2 * puzzle.marginY
        val cellW = gridSpanX / 9f
        val cellH = gridSpanY / 9f

        val pixels = IntArray(w * h) { Color.WHITE }

        // Draw horizontal grid lines
        for (i in 0..9) {
            val py = (puzzle.marginY + i * cellH).roundToInt()
            val isBlock = (i == 0 || i == 3 || i == 6 || i == 9)
            val thickness = if (isBlock) 2 else 1
            val left = puzzle.marginX.toInt().coerceIn(0, w - 1)
            val right = (puzzle.marginX + gridSpanX).toInt().coerceIn(0, w - 1)

            for (dy in -thickness..thickness) {
                val y = (py + dy).coerceIn(0, h - 1)
                for (x in left..right) {
                    pixels[y * w + x] = Color.BLACK
                }
            }
        }

        // Draw vertical grid lines
        for (j in 0..9) {
            val px = (puzzle.marginX + j * cellW).roundToInt()
            val isBlock = (j == 0 || j == 3 || j == 6 || j == 9)
            val thickness = if (isBlock) 2 else 1
            val top = puzzle.marginY.toInt().coerceIn(0, h - 1)
            val bottom = (puzzle.marginY + gridSpanY).toInt().coerceIn(0, h - 1)

            for (dx in -thickness..thickness) {
                val x = (px + dx).coerceIn(0, w - 1)
                for (y in top..bottom) {
                    pixels[y * w + x] = Color.BLACK
                }
            }
        }

        // Render digits using bitmap font matrices to ensure deterministic pixel-level fidelity
        for (r in 0 until 9) {
            for (c in 0 until 9) {
                val digitChar = puzzle.sdm[r * 9 + c]
                if (digitChar in '1'..'9') {
                    val digit = digitChar.digitToInt()
                    val cx = (puzzle.marginX + (c + 0.5f) * cellW).roundToInt()
                    val cy = (puzzle.marginY + (r + 0.5f) * cellH).roundToInt()
                    renderDigitGlyph(pixels, w, h, cx, cy, digit, scale = (cellW / 18f).roundToInt().coerceAtLeast(2))
                }
            }
        }

        bitmap.setPixels(pixels, 0, w, 0, 0, w, h)
        return bitmap
    }

    /**
     * Renders a clean 5x7 bitmap font glyph for digits 1..9 centered at (cx, cy).
     */
    private fun renderDigitGlyph(
        pixels: IntArray,
        w: Int,
        h: Int,
        cx: Int,
        cy: Int,
        digit: Int,
        scale: Int
    ) {
        val glyph = GLYPHS_5X7[digit] ?: return
        val glyphW = 5 * scale
        val glyphH = 7 * scale
        val startX = cx - glyphW / 2
        val startY = cy - glyphH / 2

        for (gy in 0 until 7) {
            val row = glyph[gy]
            for (gx in 0 until 5) {
                val isFilled = (row and (1 shl (4 - gx))) != 0
                if (isFilled) {
                    for (dy in 0 until scale) {
                        val py = startY + gy * scale + dy
                        if (py !in 0 until h) continue
                        for (dx in 0 until scale) {
                            val px = startX + gx * scale + dx
                            if (px in 0 until w) {
                                pixels[py * w + px] = Color.BLACK
                            }
                        }
                    }
                }
            }
        }
    }

    // 5x7 bit patterns for digits 1..9
    private val GLYPHS_5X7 = mapOf(
        1 to intArrayOf(
            0b00100,
            0b01100,
            0b00100,
            0b00100,
            0b00100,
            0b00100,
            0b01110
        ),
        2 to intArrayOf(
            0b01110,
            0b10001,
            0b00001,
            0b00010,
            0b00100,
            0b01000,
            0b11111
        ),
        3 to intArrayOf(
            0b11110,
            0b00001,
            0b00001,
            0b01110,
            0b00001,
            0b00001,
            0b11110
        ),
        4 to intArrayOf(
            0b00010,
            0b00110,
            0b01010,
            0b10010,
            0b11111,
            0b00010,
            0b00010
        ),
        5 to intArrayOf(
            0b11111,
            0b10000,
            0b11110,
            0b00001,
            0b00001,
            0b10001,
            0b01110
        ),
        6 to intArrayOf(
            0b00110,
            0b01000,
            0b10000,
            0b11110,
            0b10001,
            0b10001,
            0b01110
        ),
        7 to intArrayOf(
            0b11111,
            0b00001,
            0b00010,
            0b00100,
            0b01000,
            0b01000,
            0b01000
        ),
        8 to intArrayOf(
            0b01110,
            0b10001,
            0b10001,
            0b01110,
            0b10001,
            0b10001,
            0b01110
        ),
        9 to intArrayOf(
            0b01110,
            0b10001,
            0b10001,
            0b01111,
            0b00001,
            0b00010,
            0b01100
        )
    )

    /**
     * Saves bundled images to disk if not already saved.
     */
    fun saveBundledImagesToDirectory(dir: File) {
        if (!dir.exists()) dir.mkdirs()
        for (puzzle in TEST_PUZZLES) {
            val file = File(dir, "${puzzle.id}.png")
            val bmp = renderPuzzleBitmap(puzzle)
            FileOutputStream(file).use { out ->
                bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
        }
    }

    /**
     * Loads a bundled test bitmap, or generates it if not found on disk.
     */
    fun getOrLoadBitmap(puzzle: BundledTestPuzzle, cacheDir: File? = null): Bitmap {
        if (cacheDir != null) {
            val file = File(cacheDir, "${puzzle.id}.png")
            if (file.exists()) {
                val bmp = BitmapFactory.decodeFile(file.absolutePath)
                if (bmp != null) return bmp
            }
        }
        val generated = renderPuzzleBitmap(puzzle)
        if (cacheDir != null) {
            try {
                if (!cacheDir.exists()) cacheDir.mkdirs()
                val file = File(cacheDir, "${puzzle.id}.png")
                FileOutputStream(file).use { out ->
                    generated.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
            } catch (_: Exception) {}
        }
        return generated
    }
}
