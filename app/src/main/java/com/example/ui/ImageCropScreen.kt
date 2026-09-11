package com.example.ui

import android.graphics.Bitmap
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.CropSquare
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.RotateRight
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
    val cropRect by viewModel.cropRect.collectAsStateWithLifecycle()
    val isAutoDetecting by viewModel.isAutoDetecting.collectAsStateWithLifecycle()
    val detectionMessage by viewModel.cropDetectionMessage.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Crop & Align Puzzle",
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp
                        )
                        Text(
                            text = "Frame the 9x9 grid to remove background noise",
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
                            imageVector = Icons.Default.RotateRight,
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
                        cropRect = cropRect,
                        onCropRectChanged = { viewModel.setCropRect(it) },
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
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilledTonalButton(
                            onClick = { viewModel.runAutoDetectCrop() },
                            modifier = Modifier
                                .weight(1.2f)
                                .testTag("crop_auto_detect_btn"),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CropFree,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Auto-Snap", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }

                        OutlinedButton(
                            onClick = { viewModel.setSquareCrop() },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("crop_square_btn"),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CropSquare,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("1:1 Square", fontSize = 12.sp)
                        }

                        OutlinedButton(
                            onClick = { viewModel.setFullCrop() },
                            modifier = Modifier
                                .weight(0.9f)
                                .testTag("crop_full_btn"),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Fullscreen,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Full", fontSize = 12.sp)
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
                                    imageVector = Icons.Default.KeyboardArrowLeft,
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
                                    imageVector = Icons.Default.KeyboardArrowRight,
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
    cropRect: RectF,
    onCropRectChanged: (RectF) -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    var activeHandle by remember { mutableStateOf(DragHandle.NONE) }

    // Use rememberUpdatedState so pointerInput is never restarted mid-drag gesture
    val currentCropRect by rememberUpdatedState(cropRect)
    val currentOnCropRectChanged by rememberUpdatedState(onCropRectChanged)

    val primaryColor = MaterialTheme.colorScheme.primary
    val guideColor = Color.White.copy(alpha = 0.55f)
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

        // Generous touch targets for effortless resizing on mobile touchscreens and emulators
        val cornerThresholdPx = with(density) { 54.dp.toPx() }
        val edgeThresholdPx = with(density) { 36.dp.toPx() }

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

        // Interactive Crop Overlay Canvas with Gestures
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(displayedW, displayedH, offsetX, offsetY) {
                    detectDragGestures(
                        onDragStart = { startOffset ->
                            val cRect = currentCropRect
                            val sLeft = offsetX + cRect.left * displayedW
                            val sTop = offsetY + cRect.top * displayedH
                            val sRight = offsetX + cRect.right * displayedW
                            val sBottom = offsetY + cRect.bottom * displayedH

                            val x = startOffset.x
                            val y = startOffset.y

                            // Check corners first (highest priority)
                            activeHandle = when {
                                hypot(x - sLeft, y - sTop) <= cornerThresholdPx -> DragHandle.TOP_LEFT
                                hypot(x - sRight, y - sTop) <= cornerThresholdPx -> DragHandle.TOP_RIGHT
                                hypot(x - sLeft, y - sBottom) <= cornerThresholdPx -> DragHandle.BOTTOM_LEFT
                                hypot(x - sRight, y - sBottom) <= cornerThresholdPx -> DragHandle.BOTTOM_RIGHT

                                // Check edges
                                y in sTop..sBottom && kotlin.math.abs(x - sLeft) <= edgeThresholdPx -> DragHandle.EDGE_LEFT
                                y in sTop..sBottom && kotlin.math.abs(x - sRight) <= edgeThresholdPx -> DragHandle.EDGE_RIGHT
                                x in sLeft..sRight && kotlin.math.abs(y - sTop) <= edgeThresholdPx -> DragHandle.EDGE_TOP
                                x in sLeft..sRight && kotlin.math.abs(y - sBottom) <= edgeThresholdPx -> DragHandle.EDGE_BOTTOM

                                // Check inside center pan
                                x in sLeft..sRight && y in sTop..sBottom -> DragHandle.CENTER_PAN

                                else -> DragHandle.NONE
                            }
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            if (activeHandle == DragHandle.NONE || displayedW <= 0f || displayedH <= 0f) return@detectDragGestures

                            val dx = dragAmount.x / displayedW
                            val dy = dragAmount.y / displayedH

                            val cRect = currentCropRect
                            var l = cRect.left
                            var t = cRect.top
                            var r = cRect.right
                            var b = cRect.bottom

                            val minSize = 0.08f

                            when (activeHandle) {
                                DragHandle.TOP_LEFT -> {
                                    l = (l + dx).coerceIn(0f, r - minSize)
                                    t = (t + dy).coerceIn(0f, b - minSize)
                                }
                                DragHandle.TOP_RIGHT -> {
                                    r = (r + dx).coerceIn(l + minSize, 1f)
                                    t = (t + dy).coerceIn(0f, b - minSize)
                                }
                                DragHandle.BOTTOM_LEFT -> {
                                    l = (l + dx).coerceIn(0f, r - minSize)
                                    b = (b + dy).coerceIn(t + minSize, 1f)
                                }
                                DragHandle.BOTTOM_RIGHT -> {
                                    r = (r + dx).coerceIn(l + minSize, 1f)
                                    b = (b + dy).coerceIn(t + minSize, 1f)
                                }
                                DragHandle.EDGE_TOP -> {
                                    t = (t + dy).coerceIn(0f, b - minSize)
                                }
                                DragHandle.EDGE_BOTTOM -> {
                                    b = (b + dy).coerceIn(t + minSize, 1f)
                                }
                                DragHandle.EDGE_LEFT -> {
                                    l = (l + dx).coerceIn(0f, r - minSize)
                                }
                                DragHandle.EDGE_RIGHT -> {
                                    r = (r + dx).coerceIn(l + minSize, 1f)
                                }
                                DragHandle.CENTER_PAN -> {
                                    val currentW = r - l
                                    val currentH = b - t
                                    var newL = l + dx
                                    var newT = t + dy

                                    if (newL < 0f) newL = 0f
                                    if (newL + currentW > 1f) newL = 1f - currentW
                                    if (newT < 0f) newT = 0f
                                    if (newT + currentH > 1f) newT = 1f - currentH

                                    l = newL
                                    r = newL + currentW
                                    t = newT
                                    b = newT + currentH
                                }
                                DragHandle.NONE -> Unit
                            }

                            currentOnCropRectChanged(RectF(l, t, r, b))
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
            val sLeft = offsetX + cropRect.left * displayedW
            val sTop = offsetY + cropRect.top * displayedH
            val sRight = offsetX + cropRect.right * displayedW
            val sBottom = offsetY + cropRect.bottom * displayedH

            // 1. Draw Scrim outside of crop rectangle
            // Top rect
            drawRect(
                color = scrimColor,
                topLeft = Offset(0f, 0f),
                size = Size(containerWidthPx, sTop)
            )
            // Bottom rect
            drawRect(
                color = scrimColor,
                topLeft = Offset(0f, sBottom),
                size = Size(containerWidthPx, containerHeightPx - sBottom)
            )
            // Left rect
            drawRect(
                color = scrimColor,
                topLeft = Offset(0f, sTop),
                size = Size(sLeft, sBottom - sTop)
            )
            // Right rect
            drawRect(
                color = scrimColor,
                topLeft = Offset(sRight, sTop),
                size = Size(containerWidthPx - sRight, sBottom - sTop)
            )

            // 2. Draw 3x3 Grid Guidelines inside the crop box
            val cropW = sRight - sLeft
            val cropH = sBottom - sTop

            val strokeGuideline = Stroke(width = 1.5.dp.toPx())
            // Horizontal lines
            drawLine(
                color = guideColor,
                start = Offset(sLeft, sTop + cropH / 3f),
                end = Offset(sRight, sTop + cropH / 3f),
                strokeWidth = strokeGuideline.width
            )
            drawLine(
                color = guideColor,
                start = Offset(sLeft, sTop + 2 * cropH / 3f),
                end = Offset(sRight, sTop + 2 * cropH / 3f),
                strokeWidth = strokeGuideline.width
            )
            // Vertical lines
            drawLine(
                color = guideColor,
                start = Offset(sLeft + cropW / 3f, sTop),
                end = Offset(sLeft + cropW / 3f, sBottom),
                strokeWidth = strokeGuideline.width
            )
            drawLine(
                color = guideColor,
                start = Offset(sLeft + 2 * cropW / 3f, sTop),
                end = Offset(sLeft + 2 * cropW / 3f, sBottom),
                strokeWidth = strokeGuideline.width
            )

            // 3. Draw Outer Border
            val borderStroke = Stroke(width = 2.5.dp.toPx())
            drawRect(
                color = primaryColor,
                topLeft = Offset(sLeft, sTop),
                size = Size(cropW, cropH),
                style = borderStroke
            )

            // 4. Draw Corner Handles & Brackets
            val bracketLength = min(36.dp.toPx(), min(cropW, cropH) * 0.35f)
            val bracketStroke = Stroke(width = 4.5.dp.toPx())

            // Top-Left bracket
            drawLine(primaryColor, Offset(sLeft - 2f, sTop), Offset(sLeft + bracketLength, sTop), bracketStroke.width)
            drawLine(primaryColor, Offset(sLeft, sTop - 2f), Offset(sLeft, sTop + bracketLength), bracketStroke.width)

            // Top-Right bracket
            drawLine(primaryColor, Offset(sRight + 2f, sTop), Offset(sRight - bracketLength, sTop), bracketStroke.width)
            drawLine(primaryColor, Offset(sRight, sTop - 2f), Offset(sRight, sTop + bracketLength), bracketStroke.width)

            // Bottom-Left bracket
            drawLine(primaryColor, Offset(sLeft - 2f, sBottom), Offset(sLeft + bracketLength, sBottom), bracketStroke.width)
            drawLine(primaryColor, Offset(sLeft, sBottom + 2f), Offset(sLeft, sBottom - bracketLength), bracketStroke.width)

            // Bottom-Right bracket
            drawLine(primaryColor, Offset(sRight + 2f, sBottom), Offset(sRight - bracketLength, sBottom), bracketStroke.width)
            drawLine(primaryColor, Offset(sRight, sBottom + 2f), Offset(sRight, sBottom - bracketLength), bracketStroke.width)

            // 5. Draw Circular Corner Grips with Halo for touch clarity
            val handleRadius = 11.dp.toPx()
            val handleBorder = 3.dp.toPx()

            fun drawCornerKnob(cx: Float, cy: Float, isActive: Boolean) {
                val radius = if (isActive) handleRadius * 1.35f else handleRadius
                // Soft glow halo
                drawCircle(color = primaryColor.copy(alpha = 0.35f), radius = radius + 6.dp.toPx(), center = Offset(cx, cy))
                // Solid knob
                drawCircle(color = Color.White, radius = radius, center = Offset(cx, cy))
                drawCircle(color = primaryColor, radius = radius, center = Offset(cx, cy), style = Stroke(handleBorder))
            }

            drawCornerKnob(sLeft, sTop, activeHandle == DragHandle.TOP_LEFT)
            drawCornerKnob(sRight, sTop, activeHandle == DragHandle.TOP_RIGHT)
            drawCornerKnob(sLeft, sBottom, activeHandle == DragHandle.BOTTOM_LEFT)
            drawCornerKnob(sRight, sBottom, activeHandle == DragHandle.BOTTOM_RIGHT)

            // 6. Draw Edge center pills
            val pillLen = 26.dp.toPx()
            val pillStroke = Stroke(width = 4.dp.toPx())
            // Top edge
            drawLine(Color.White, Offset((sLeft + sRight) / 2f - pillLen / 2, sTop), Offset((sLeft + sRight) / 2f + pillLen / 2, sTop), pillStroke.width)
            // Bottom edge
            drawLine(Color.White, Offset((sLeft + sRight) / 2f - pillLen / 2, sBottom), Offset((sLeft + sRight) / 2f + pillLen / 2, sBottom), pillStroke.width)
            // Left edge
            drawLine(Color.White, Offset(sLeft, (sTop + sBottom) / 2f - pillLen / 2), Offset(sLeft, (sTop + sBottom) / 2f + pillLen / 2), pillStroke.width)
            // Right edge
            drawLine(Color.White, Offset(sRight, (sTop + sBottom) / 2f - pillLen / 2), Offset(sRight, (sTop + sBottom) / 2f + pillLen / 2), pillStroke.width)
        }
    }
}

