package com.wallpaper.rotator.ui.crop

import android.graphics.RectF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import java.io.File
import kotlin.math.abs
import kotlin.math.sqrt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CropEditorScreen(
    photoId: Long,
    onNavigateBack: () -> Unit,
    viewModel: CropEditorViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(photoId) { viewModel.loadPhoto(photoId) }
    LaunchedEffect(state.isSaved) { if (state.isSaved) onNavigateBack() }

    val barScrim = Color.Black.copy(alpha = 0.85f)
    val onBar = Color.White

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.subjectType.name.replace('_', ' ')) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.Close, contentDescription = "Cancel")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = barScrim,
                    titleContentColor = onBar,
                    navigationIconContentColor = onBar
                )
            )
        },
        bottomBar = {
            BottomAppBar(
                containerColor = barScrim,
                contentColor = onBar
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = { viewModel.saveCrop() },
                        colors = ButtonDefaults.textButtonColors(contentColor = onBar)
                    ) {
                        Text("Save")
                    }
                    Button(
                        onClick = { viewModel.saveCropAndApply() },
                        enabled = state.photo?.isEnabled == true,
                        shape = RoundedCornerShape(28.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                        )
                    ) {
                        Text("Save & apply")
                    }
                }
            }
        },
        containerColor = Color.Black
    ) { padding ->
        val photo = state.photo
        if (photo != null && state.imageWidth > 0 && state.imageHeight > 0) {
            CropOverlayImage(
                filePath = photo.filePath,
                cropRect = state.cropRect,
                imageWidth = state.imageWidth,
                imageHeight = state.imageHeight,
                aspectRatio = state.aspectRatio,
                onCropChanged = { viewModel.updateCropRect(it) },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            )
        }
    }
}

enum class ResizeCorner {
    NONE, TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT
}

@Composable
@Suppress("UNUSED_PARAMETER")
private fun CropOverlayImage(
    filePath: String,
    cropRect: RectF,
    imageWidth: Float,
    imageHeight: Float,
    aspectRatio: Float,
    onCropChanged: (RectF) -> Unit,
    modifier: Modifier = Modifier
) {
    var viewSize by remember { mutableStateOf(IntSize.Zero) }
    var dragStartCrop by remember { mutableStateOf(RectF()) }
    var scaleX by remember { mutableFloatStateOf(1f) }
    var scaleY by remember { mutableFloatStateOf(1f) }
    var resizeCorner by remember { mutableStateOf(ResizeCorner.NONE) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    val currentCropRect by rememberUpdatedState(cropRect)

    Box(
        modifier = modifier.onSizeChanged { size ->
            viewSize = size
            if (imageWidth > 0 && imageHeight > 0) {
                val fitScale = minOf(
                    size.width / imageWidth,
                    size.height / imageHeight
                )
                scaleX = fitScale
                scaleY = fitScale
                offsetX = (size.width - imageWidth * fitScale) / 2f
                offsetY = (size.height - imageHeight * fitScale) / 2f
            }
        }
    ) {
        AsyncImage(
            model = File(filePath),
            contentDescription = "Photo to crop",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit
        )

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(scaleX, scaleY, offsetX, offsetY) {
                    detectDragGestures(
                        onDragStart = { screenPos ->
                            dragStartCrop = RectF(currentCropRect)
                            resizeCorner = detectTouchedCorner(
                                screenPos,
                                currentCropRect,
                                scaleX,
                                scaleY,
                                offsetX,
                                offsetY
                            )
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            val dx = dragAmount.x / scaleX
                            val dy = dragAmount.y / scaleY

                            val newRect = when (resizeCorner) {
                                ResizeCorner.NONE -> {
                                    // Center drag: move the crop box
                                    val w = dragStartCrop.width()
                                    val h = dragStartCrop.height()
                                    var newLeft = currentCropRect.left + dx
                                    var newTop = currentCropRect.top + dy
                                    newLeft = newLeft.coerceIn(0f, imageWidth - w)
                                    newTop = newTop.coerceIn(0f, imageHeight - h)
                                    RectF(newLeft, newTop, newLeft + w, newTop + h)
                                }
                                ResizeCorner.TOP_LEFT -> {
                                    resizeCornerTopLeft(
                                        currentCropRect,
                                        dx,
                                        dy,
                                        imageWidth,
                                        imageHeight,
                                        aspectRatio
                                    )
                                }
                                ResizeCorner.TOP_RIGHT -> {
                                    resizeCornerTopRight(
                                        currentCropRect,
                                        dx,
                                        dy,
                                        imageWidth,
                                        imageHeight,
                                        aspectRatio
                                    )
                                }
                                ResizeCorner.BOTTOM_LEFT -> {
                                    resizeCornerBottomLeft(
                                        currentCropRect,
                                        dx,
                                        dy,
                                        imageWidth,
                                        imageHeight,
                                        aspectRatio
                                    )
                                }
                                ResizeCorner.BOTTOM_RIGHT -> {
                                    resizeCornerBottomRight(
                                        currentCropRect,
                                        dx,
                                        dy,
                                        imageWidth,
                                        imageHeight,
                                        aspectRatio
                                    )
                                }
                            }
                            onCropChanged(newRect)
                        }
                    )
                }
        ) {
            drawCropOverlay(cropRect, scaleX, scaleY, offsetX, offsetY, imageWidth, imageHeight)
        }
    }
}

private fun detectTouchedCorner(
    screenPos: Offset,
    cropRect: RectF,
    scaleX: Float,
    scaleY: Float,
    offsetX: Float,
    offsetY: Float
): ResizeCorner {
    val handleSize = 64f // Touch tolerance in screen pixels
    val left = offsetX + cropRect.left * scaleX
    val top = offsetY + cropRect.top * scaleY
    val right = offsetX + cropRect.right * scaleX
    val bottom = offsetY + cropRect.bottom * scaleY

    val corners = listOf(
        Pair(ResizeCorner.TOP_LEFT, Offset(left, top)),
        Pair(ResizeCorner.TOP_RIGHT, Offset(right, top)),
        Pair(ResizeCorner.BOTTOM_LEFT, Offset(left, bottom)),
        Pair(ResizeCorner.BOTTOM_RIGHT, Offset(right, bottom))
    )

    for ((corner, pos) in corners) {
        val dist = sqrt((screenPos.x - pos.x) * (screenPos.x - pos.x) + (screenPos.y - pos.y) * (screenPos.y - pos.y))
        if (dist < handleSize) {
            return corner
        }
    }
    return ResizeCorner.NONE
}

private fun resizeCornerTopLeft(
    startRect: RectF,
    dx: Float,
    dy: Float,
    imageWidth: Float,
    imageHeight: Float,
    aspectRatio: Float
): RectF {
    // Anchored corner: bottom-right. Dragged corner: top-left.
    // Dragging left (dx<0) or up (dy<0) should grow the box.
    val anchorRight = startRect.right
    val anchorBottom = startRect.bottom

    val dw = if (abs(dx) >= abs(dy)) -dx else -dy / aspectRatio
    var w = (startRect.width() + dw).coerceIn(imageWidth * 0.1f, anchorRight)
    var h = w * aspectRatio

    val maxH = anchorBottom.coerceAtMost(imageHeight)
    if (h > maxH) {
        h = maxH
        w = h / aspectRatio
    }

    val newLeft = anchorRight - w
    val newTop = anchorBottom - h
    return RectF(newLeft.coerceAtLeast(0f), newTop.coerceAtLeast(0f), anchorRight, anchorBottom)
}

private fun resizeCornerTopRight(
    startRect: RectF,
    dx: Float,
    dy: Float,
    imageWidth: Float,
    imageHeight: Float,
    aspectRatio: Float
): RectF {
    // Anchored corner: bottom-left. Dragged corner: top-right.
    // Dragging right (dx>0) or up (dy<0) should grow the box.
    val anchorLeft = startRect.left
    val anchorBottom = startRect.bottom

    val dw = if (abs(dx) >= abs(dy)) dx else -dy / aspectRatio
    var w = (startRect.width() + dw).coerceIn(imageWidth * 0.1f, imageWidth - anchorLeft)
    var h = w * aspectRatio

    val maxH = anchorBottom.coerceAtMost(imageHeight)
    if (h > maxH) {
        h = maxH
        w = h / aspectRatio
    }

    val newRight = anchorLeft + w
    val newTop = anchorBottom - h
    return RectF(anchorLeft, newTop.coerceAtLeast(0f), newRight.coerceAtMost(imageWidth), anchorBottom)
}

private fun resizeCornerBottomLeft(
    startRect: RectF,
    dx: Float,
    dy: Float,
    imageWidth: Float,
    imageHeight: Float,
    aspectRatio: Float
): RectF {
    // Anchored corner: top-right. Dragged corner: bottom-left.
    // Dragging left (dx<0) or down (dy>0) should grow the box.
    val anchorRight = startRect.right
    val anchorTop = startRect.top

    val dw = if (abs(dx) >= abs(dy)) -dx else dy / aspectRatio
    var w = (startRect.width() + dw).coerceIn(imageWidth * 0.1f, anchorRight)
    var h = w * aspectRatio

    if (anchorTop + h > imageHeight) {
        h = imageHeight - anchorTop
        w = h / aspectRatio
    }

    val newLeft = anchorRight - w
    val newBottom = anchorTop + h
    return RectF(newLeft.coerceAtLeast(0f), anchorTop, anchorRight, newBottom.coerceAtMost(imageHeight))
}

private fun resizeCornerBottomRight(
    startRect: RectF,
    dx: Float,
    dy: Float,
    imageWidth: Float,
    imageHeight: Float,
    aspectRatio: Float
): RectF {
    // Anchored corner: top-left. Dragged corner: bottom-right.
    // Dragging right (dx>0) or down (dy>0) should grow the box.
    val anchorLeft = startRect.left
    val anchorTop = startRect.top

    val dw = if (abs(dx) >= abs(dy)) dx else dy / aspectRatio
    var w = (startRect.width() + dw).coerceIn(imageWidth * 0.1f, imageWidth - anchorLeft)
    var h = w * aspectRatio

    if (anchorTop + h > imageHeight) {
        h = imageHeight - anchorTop
        w = h / aspectRatio
    }

    val newRight = anchorLeft + w
    val newBottom = anchorTop + h
    return RectF(anchorLeft, anchorTop, newRight.coerceAtMost(imageWidth), newBottom.coerceAtMost(imageHeight))
}

@Suppress("UNUSED_PARAMETER")
private fun DrawScope.drawCropOverlay(
    cropRect: RectF,
    scaleX: Float,
    scaleY: Float,
    offsetX: Float,
    offsetY: Float,
    imageWidth: Float,
    imageHeight: Float
) {
    val left = offsetX + cropRect.left * scaleX
    val top = offsetY + cropRect.top * scaleY
    val right = offsetX + cropRect.right * scaleX
    val bottom = offsetY + cropRect.bottom * scaleY

    val dimColor = Color.Black.copy(alpha = 0.5f)
    drawRect(dimColor, Offset.Zero, Size(size.width, top))
    drawRect(dimColor, Offset(0f, bottom), Size(size.width, size.height - bottom))
    drawRect(dimColor, Offset(0f, top), Size(left, bottom - top))
    drawRect(dimColor, Offset(right, top), Size(size.width - right, bottom - top))

    drawRect(
        Color.White,
        Offset(left, top),
        Size(right - left, bottom - top),
        style = Stroke(width = 2f)
    )

    val thirdW = (right - left) / 3f
    val thirdH = (bottom - top) / 3f
    val gridColor = Color.White.copy(alpha = 0.4f)
    for (i in 1..2) {
        drawLine(gridColor, Offset(left + thirdW * i, top), Offset(left + thirdW * i, bottom), strokeWidth = 1f)
        drawLine(gridColor, Offset(left, top + thirdH * i), Offset(right, top + thirdH * i), strokeWidth = 1f)
    }

    val handleColor = Color.White
    val handleRadius = 15f
    val corners = listOf(
        Offset(left, top), Offset(right, top),
        Offset(left, bottom), Offset(right, bottom)
    )
    for (corner in corners) {
        drawCircle(handleColor, radius = handleRadius, center = corner)
    }
}

