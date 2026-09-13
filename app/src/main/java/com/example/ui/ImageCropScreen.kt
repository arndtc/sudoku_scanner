package com.example.ui

import android.graphics.Bitmap
import android.graphics.PointF
import android.graphics.RectF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.CropSquare
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.model.PerspectiveQuad
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

private enum class DragHandle {
    NONE,
    TOP_LEFT,
    TOP_RIGHT,
    BOTTOM_LEFT,
    BOTTOM_RIGHT,
    EDGE_TOP,
    EDGE_BOTTOM,
    EDGE_LEFT,
    EDGE_RIGHT,
    CENTER_PAN
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageCropScreen(
    viewModel: SudokuViewModel,
    onBack: () -> Unit,
    onCropApplied: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bitmap by viewModel.cropSourceBitmap.collectAsStateWithLifecycle()
    val cropQuad by viewModel.cropQuad.collectAsStateWithLifecycle()
    val isAutoDetecting by viewModel.isAutoDetecting.collectAsStateWithLifecycle()
    val detectionMessage by viewModel.cropDetectionMessage.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Perspective Crop & Align",
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp
                        )
                        Text(
                            text = "Drag 4 corners to align with angled puzzle edges",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("crop_back_btn")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.rotateCropImageClockwise() },
                        modifier = Modifier.testTag("crop_rotate_btn")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.RotateRight,
                            contentDescription = "Rotate 90 degrees",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(Color.Black)
        ) {
            // Interactive Crop Viewport
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                if (bitmap != null) {
                    InteractiveCropViewport(
                        bitmap = bitmap!!,
                        cropQuad = cropQuad,
                        onCropQuadChanged = { viewModel.setCropQuad(it) },
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                }

                // Detection status pill
                if (isAutoDetecting || detectionMessage != null) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                        tonalElevation = 4.dp,
                        shadowElevation = 4.dp,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            if (isAutoDetecting) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(14.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                            } else {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            Text(
                                text = detectionMessage ?: "Auto-detecting puzzle...",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            // Bottom Action Controls
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Presets Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilledTonalButton(
                            onClick = { viewModel.runAutoDetectCrop() },
                            modifier = Modifier
                                .weight(1.15f)
                                .testTag("crop_auto_detect_btn"),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CropFree,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text("Auto-Snap", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                        }

                        OutlinedButton(
                            onClick = { viewModel.setSquareCrop() },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("crop_square_btn"),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CropSquare,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text("1:1 Square", fontSize = 11.5.sp)
                        }

                        OutlinedButton(
                            onClick = { viewModel.resetCropCorners() },
                            modifier = Modifier
                                .weight(0.95f)
                                .testTag("crop_reset_btn"),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.RestartAlt,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text("Reset", fontSize = 11.5.sp)
                        }

                        OutlinedButton(
                            onClick = { viewModel.setFullCrop() },
                            modifier = Modifier
                                .weight(0.85f)
                                .testTag("crop_full_btn"),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Fullscreen,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text("Full", fontSize = 11.5.sp)
                        }
                    }

                    // Size & Nudge Fine-Tuning Bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                shape = RoundedCornerShape(10.dp)
                            )
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Size Controls (Shrink / Expand)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "Size:",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            FilledTonalIconButton(
                                onClick = { viewModel.scaleCrop(-0.06f) },
                                modifier = Modifier
                                    .size(32.dp)
                                    .testTag("crop_shrink_btn"),
                                colors = IconButtonDefaults.filledTonalIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.surface
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Remove,
                                    contentDescription = "Shrink selection",
                                    modifier = Modifier.size(16.dp)
                                )
                            }

                            FilledTonalIconButton(
                                onClick = { viewModel.scaleCrop(0.06f) },
                                modifier = Modifier
                                    .size(32.dp)
                                    .testTag("crop_expand_btn"),
                                colors = IconButtonDefaults.filledTonalIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.surface
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "Expand selection",
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        // Directional Nudge Controls
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = "Nudge:",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            IconButton(
                                onClick = { viewModel.nudgeCrop(-0.025f, 0f) },
                                modifier = Modifier
                                    .size(32.dp)
                                    .testTag("crop_nudge_left_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                                    contentDescription = "Nudge left",
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            IconButton(
                                onClick = { viewModel.nudgeCrop(0f, -0.025f) },
                                modifier = Modifier
                                    .size(32.dp)
                                    .testTag("crop_nudge_up_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowUp,
                                    contentDescription = "Nudge up",
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            IconButton(
                                onClick = { viewModel.nudgeCrop(0f, 0.025f) },
                                modifier = Modifier
                                    .size(32.dp)
                                    .testTag("crop_nudge_down_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowDown,
                                    contentDescription = "Nudge down",
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            IconButton(
                                onClick = { viewModel.nudgeCrop(0.025f, 0f) },
                                modifier = Modifier
                                    .size(32.dp)
                                    .testTag("crop_nudge_right_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    contentDescription = "Nudge right",
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }

                    // Primary Action: Apply Crop & Scan
                    Button(
                        onClick = {
                            viewModel.applyCropAndScan(onComplete = onCropApplied)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("crop_and_scan_btn"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Crop & Scan Puzzle",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun InteractiveCropViewport(
    bitmap: Bitmap,
    cropQuad: PerspectiveQuad,
    onCropQuadChanged: (PerspectiveQuad) -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    var activeHandle by remember { mutableStateOf(DragHandle.NONE) }

    // Use rememberUpdatedState so pointerInput is never restarted mid-drag gesture
    val currentCropQuad by rememberUpdatedState(cropQuad)
    val currentOnCropQuadChanged by rememberUpdatedState(onCropQuadChanged)

    val primaryColor = MaterialTheme.colorScheme.primary
    val scrimColor = Color(0x9E000000)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF101010))
    ) {
        val containerWidthPx = with(density) { maxWidth.toPx() }
        val containerHeightPx = with(density) { maxHeight.toPx() }

        val imgW = bitmap.width.toFloat()
        val imgH = bitmap.height.toFloat()

        // Calculate aspect-fit placement
        val imgAspect = imgW / imgH
        val containerAspect = containerWidthPx / containerHeightPx

        val displayedW: Float
        val displayedH: Float
        val offsetX: Float
        val offsetY: Float

        if (imgAspect > containerAspect) {
            displayedW = containerWidthPx
            displayedH = containerWidthPx / imgAspect
            offsetX = 0f
            offsetY = (containerHeightPx - displayedH) / 2f
        } else {
            displayedH = containerHeightPx
            displayedW = containerHeightPx * imgAspect
            offsetX = (containerWidthPx - displayedW) / 2f
            offsetY = 0f
        }

        // Render Image
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = "Image to crop",
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    start = with(density) { offsetX.toDp() },
                    top = with(density) { offsetY.toDp() },
                    end = with(density) { (containerWidthPx - offsetX - displayedW).toDp() },
                    bottom = with(density) { (containerHeightPx - offsetY - displayedH).toDp() }
                )
        )

        // Interactive Perspective Crop Overlay Canvas with Gestures
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(displayedW, displayedH, offsetX, offsetY) {
                    detectDragGestures(
                        onDragStart = { startOffset ->
                            val q = currentCropQuad
                            val sTL = Offset(offsetX + q.topLeft.x * displayedW, offsetY + q.topLeft.y * displayedH)
                            val sTR = Offset(offsetX + q.topRight.x * displayedW, offsetY + q.topRight.y * displayedH)
                            val sBR = Offset(offsetX + q.bottomRight.x * displayedW, offsetY + q.bottomRight.y * displayedH)
                            val sBL = Offset(offsetX + q.bottomLeft.x * displayedW, offsetY + q.bottomLeft.y * displayedH)

                            val x = startOffset.x
                            val y = startOffset.y

                            val cornerTouchRadius = with(density) { 44.dp.toPx() }
                            val edgeTouchRadius = with(density) { 34.dp.toPx() }

                            val distTL = hypot(x - sTL.x, y - sTL.y)
                            val distTR = hypot(x - sTR.x, y - sTR.y)
                            val distBR = hypot(x - sBR.x, y - sBR.y)
                            val distBL = hypot(x - sBL.x, y - sBL.y)

                            val minDist = min(min(distTL, distTR), min(distBR, distBL))

                            // 1. Prioritize free-moving corner knobs
                            if (minDist <= cornerTouchRadius) {
                                activeHandle = when (minDist) {
                                    distTL -> DragHandle.TOP_LEFT
                                    distTR -> DragHandle.TOP_RIGHT
                                    distBR -> DragHandle.BOTTOM_RIGHT
                                    else -> DragHandle.BOTTOM_LEFT
                                }
                                return@detectDragGestures
                            }

                            // 2. Edge midpoints for moving an entire side
                            val midTop = (sTL + sTR) / 2f
                            val midBottom = (sBL + sBR) / 2f
                            val midLeft = (sTL + sBL) / 2f
                            val midRight = (sTR + sBR) / 2f

                            val distTop = hypot(x - midTop.x, y - midTop.y)
                            val distBottom = hypot(x - midBottom.x, y - midBottom.y)
                            val distLeft = hypot(x - midLeft.x, y - midLeft.y)
                            val distRight = hypot(x - midRight.x, y - midRight.y)

                            val minEdgeDist = min(min(distTop, distBottom), min(distLeft, distRight))
                            if (minEdgeDist <= edgeTouchRadius) {
                                activeHandle = when (minEdgeDist) {
                                    distTop -> DragHandle.EDGE_TOP
                                    distBottom -> DragHandle.EDGE_BOTTOM
                                    distLeft -> DragHandle.EDGE_LEFT
                                    else -> DragHandle.EDGE_RIGHT
                                }
                                return@detectDragGestures
                            }

                            // 3. Center pan inside the quadrilateral
                            fun crossProductSign(ax: Float, ay: Float, bx: Float, by: Float, px: Float, py: Float): Float {
                                return (bx - ax) * (py - ay) - (by - ay) * (px - ax)
                            }
                            val cp1 = crossProductSign(sTL.x, sTL.y, sTR.x, sTR.y, x, y)
                            val cp2 = crossProductSign(sTR.x, sTR.y, sBR.x, sBR.y, x, y)
                            val cp3 = crossProductSign(sBR.x, sBR.y, sBL.x, sBL.y, x, y)
                            val cp4 = crossProductSign(sBL.x, sBL.y, sTL.x, sTL.y, x, y)

                            val allPositive = cp1 >= 0f && cp2 >= 0f && cp3 >= 0f && cp4 >= 0f
                            val allNegative = cp1 <= 0f && cp2 <= 0f && cp3 <= 0f && cp4 <= 0f

                            activeHandle = if (allPositive || allNegative) {
                                DragHandle.CENTER_PAN
                            } else {
                                DragHandle.NONE
                            }
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            if (activeHandle == DragHandle.NONE || displayedW <= 0f || displayedH <= 0f) return@detectDragGestures

                            val dx = dragAmount.x / displayedW
                            val dy = dragAmount.y / displayedH
                            val q = currentCropQuad

                            val updated = when (activeHandle) {
                                DragHandle.TOP_LEFT -> q.copy(
                                    topLeft = PointF(
                                        (q.topLeft.x + dx).coerceIn(0f, 1f),
                                        (q.topLeft.y + dy).coerceIn(0f, 1f)
                                    )
                                )
                                DragHandle.TOP_RIGHT -> q.copy(
                                    topRight = PointF(
                                        (q.topRight.x + dx).coerceIn(0f, 1f),
                                        (q.topRight.y + dy).coerceIn(0f, 1f)
                                    )
                                )
                                DragHandle.BOTTOM_RIGHT -> q.copy(
                                    bottomRight = PointF(
                                        (q.bottomRight.x + dx).coerceIn(0f, 1f),
                                        (q.bottomRight.y + dy).coerceIn(0f, 1f)
                                    )
                                )
                                DragHandle.BOTTOM_LEFT -> q.copy(
                                    bottomLeft = PointF(
                                        (q.bottomLeft.x + dx).coerceIn(0f, 1f),
                                        (q.bottomLeft.y + dy).coerceIn(0f, 1f)
                                    )
                                )
                                DragHandle.EDGE_TOP -> q.copy(
                                    topLeft = PointF((q.topLeft.x + dx).coerceIn(0f, 1f), (q.topLeft.y + dy).coerceIn(0f, 1f)),
                                    topRight = PointF((q.topRight.x + dx).coerceIn(0f, 1f), (q.topRight.y + dy).coerceIn(0f, 1f))
                                )
                                DragHandle.EDGE_BOTTOM -> q.copy(
                                    bottomLeft = PointF((q.bottomLeft.x + dx).coerceIn(0f, 1f), (q.bottomLeft.y + dy).coerceIn(0f, 1f)),
                                    bottomRight = PointF((q.bottomRight.x + dx).coerceIn(0f, 1f), (q.bottomRight.y + dy).coerceIn(0f, 1f))
                                )
                                DragHandle.EDGE_LEFT -> q.copy(
                                    topLeft = PointF((q.topLeft.x + dx).coerceIn(0f, 1f), (q.topLeft.y + dy).coerceIn(0f, 1f)),
                                    bottomLeft = PointF((q.bottomLeft.x + dx).coerceIn(0f, 1f), (q.bottomLeft.y + dy).coerceIn(0f, 1f))
                                )
                                DragHandle.EDGE_RIGHT -> q.copy(
                                    topRight = PointF((q.topRight.x + dx).coerceIn(0f, 1f), (q.topRight.y + dy).coerceIn(0f, 1f)),
                                    bottomRight = PointF((q.bottomRight.x + dx).coerceIn(0f, 1f), (q.bottomRight.y + dy).coerceIn(0f, 1f))
                                )
                                DragHandle.CENTER_PAN -> q.nudge(dx, dy)
                                DragHandle.NONE -> q
                            }

                            currentOnCropQuadChanged(updated)
                        },
                        onDragEnd = {
                            activeHandle = DragHandle.NONE
                        },
                        onDragCancel = {
                            activeHandle = DragHandle.NONE
                        }
                    )
                }
        ) {
            val q = cropQuad
            val sTL = Offset(offsetX + q.topLeft.x * displayedW, offsetY + q.topLeft.y * displayedH)
            val sTR = Offset(offsetX + q.topRight.x * displayedW, offsetY + q.topRight.y * displayedH)
            val sBR = Offset(offsetX + q.bottomRight.x * displayedW, offsetY + q.bottomRight.y * displayedH)
            val sBL = Offset(offsetX + q.bottomLeft.x * displayedW, offsetY + q.bottomLeft.y * displayedH)

            // 1. Path of the quadrilateral
            val quadPath = Path().apply {
                moveTo(sTL.x, sTL.y)
                lineTo(sTR.x, sTR.y)
                lineTo(sBR.x, sBR.y)
                lineTo(sBL.x, sBL.y)
                close()
            }

            // 2. Dim/scrim outside the quadrilateral
            clipPath(quadPath, clipOp = ClipOp.Difference) {
                drawRect(scrimColor)
            }

            // 3. Perspective 9x9 guide lines inside the quadrilateral
            for (c in 1..8) {
                val v = c / 9f
                val pTop = Offset(
                    offsetX + (q.topLeft.x + (q.topRight.x - q.topLeft.x) * v) * displayedW,
                    offsetY + (q.topLeft.y + (q.topRight.y - q.topLeft.y) * v) * displayedH
                )
                val pBottom = Offset(
                    offsetX + (q.bottomLeft.x + (q.bottomRight.x - q.bottomLeft.x) * v) * displayedW,
                    offsetY + (q.bottomLeft.y + (q.bottomRight.y - q.bottomLeft.y) * v) * displayedH
                )
                val isSubgrid = (c % 3 == 0)
                val strokeW = if (isSubgrid) 2.dp.toPx() else 1.dp.toPx()
                val color = if (isSubgrid) Color.White.copy(alpha = 0.72f) else Color.White.copy(alpha = 0.32f)
                drawLine(color, pTop, pBottom, strokeWidth = strokeW)
            }

            for (r in 1..8) {
                val u = r / 9f
                val pLeft = Offset(
                    offsetX + (q.topLeft.x + (q.bottomLeft.x - q.topLeft.x) * u) * displayedW,
                    offsetY + (q.topLeft.y + (q.bottomLeft.y - q.topLeft.y) * u) * displayedH
                )
                val pRight = Offset(
                    offsetX + (q.topRight.x + (q.bottomRight.x - q.topRight.x) * u) * displayedW,
                    offsetY + (q.topRight.y + (q.bottomRight.y - q.topRight.y) * u) * displayedH
                )
                val isSubgrid = (r % 3 == 0)
                val strokeW = if (isSubgrid) 2.dp.toPx() else 1.dp.toPx()
                val color = if (isSubgrid) Color.White.copy(alpha = 0.72f) else Color.White.copy(alpha = 0.32f)
                drawLine(color, pLeft, pRight, strokeWidth = strokeW)
            }

            // 4. Outer quadrilateral border
            drawPath(
                path = quadPath,
                color = primaryColor,
                style = Stroke(width = 2.5.dp.toPx())
            )

            // 5. Corner Handles (Photos-app-style free-moving corner knobs)
            val handleRadius = 13.dp.toPx()
            val handleBorder = 3.dp.toPx()

            fun drawCornerHandle(pt: Offset, isActive: Boolean) {
                val radius = if (isActive) handleRadius * 1.35f else handleRadius
                // Soft glow halo
                drawCircle(color = primaryColor.copy(alpha = if (isActive) 0.55f else 0.28f), radius = radius + 7.dp.toPx(), center = pt)
                // Solid knob
                drawCircle(color = Color.White, radius = radius, center = pt)
                drawCircle(color = primaryColor, radius = radius, center = pt, style = Stroke(handleBorder))
                // Inner center point
                drawCircle(color = primaryColor, radius = 3.5.dp.toPx(), center = pt)
            }

            drawCornerHandle(sTL, activeHandle == DragHandle.TOP_LEFT)
            drawCornerHandle(sTR, activeHandle == DragHandle.TOP_RIGHT)
            drawCornerHandle(sBR, activeHandle == DragHandle.BOTTOM_RIGHT)
            drawCornerHandle(sBL, activeHandle == DragHandle.BOTTOM_LEFT)

            // 6. Edge center pills with active highlight
            val pillLen = 26.dp.toPx()
            val pillBaseWidth = 4.5.dp.toPx()

            fun drawEdgePill(isH: Boolean, pt: Offset, isActive: Boolean) {
                val color = if (isActive) primaryColor else Color.White
                val strokeW = if (isActive) pillBaseWidth * 1.35f else pillBaseWidth
                val len = if (isActive) pillLen * 1.25f else pillLen

                if (isActive) {
                    val glowW = strokeW + 6.dp.toPx()
                    val glowColor = primaryColor.copy(alpha = 0.45f)
                    if (isH) {
                        drawLine(glowColor, Offset(pt.x - len / 2f, pt.y), Offset(pt.x + len / 2f, pt.y), glowW, StrokeCap.Round)
                    } else {
                        drawLine(glowColor, Offset(pt.x, pt.y - len / 2f), Offset(pt.x, pt.y + len / 2f), glowW, StrokeCap.Round)
                    }
                }
                // High contrast dark outline
                val outlineW = strokeW + 2.5.dp.toPx()
                if (isH) {
                    drawLine(Color(0xB3000000), Offset(pt.x - len / 2f, pt.y), Offset(pt.x + len / 2f, pt.y), outlineW, StrokeCap.Round)
                    drawLine(color, Offset(pt.x - len / 2f, pt.y), Offset(pt.x + len / 2f, pt.y), strokeW, StrokeCap.Round)
                } else {
                    drawLine(Color(0xB3000000), Offset(pt.x, pt.y - len / 2f), Offset(pt.x, pt.y + len / 2f), outlineW, StrokeCap.Round)
                    drawLine(color, Offset(pt.x, pt.y - len / 2f), Offset(pt.x, pt.y + len / 2f), strokeW, StrokeCap.Round)
                }
            }

            val midTop = (sTL + sTR) / 2f
            val midBottom = (sBL + sBR) / 2f
            val midLeft = (sTL + sBL) / 2f
            val midRight = (sTR + sBR) / 2f

            drawEdgePill(isH = true, pt = midTop, isActive = activeHandle == DragHandle.EDGE_TOP)
            drawEdgePill(isH = true, pt = midBottom, isActive = activeHandle == DragHandle.EDGE_BOTTOM)
            drawEdgePill(isH = false, pt = midLeft, isActive = activeHandle == DragHandle.EDGE_LEFT)
            drawEdgePill(isH = false, pt = midRight, isActive = activeHandle == DragHandle.EDGE_RIGHT)
        }
    }
}

