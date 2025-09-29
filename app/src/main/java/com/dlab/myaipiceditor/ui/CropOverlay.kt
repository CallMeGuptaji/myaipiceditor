package com.dlab.myaipiceditor.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

data class CropRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
    
    fun toRect(): Rect = Rect(left, top, right, bottom)
}

@Composable
fun CropOverlay(
    imageSize: Size,
    containerSize: Size,
    onCropRectChange: (CropRect) -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    
    // Calculate the scale and offset to fit image in container
    val scale = minOf(
        containerSize.width / imageSize.width,
        containerSize.height / imageSize.height
    )
    
    val scaledImageWidth = imageSize.width * scale
    val scaledImageHeight = imageSize.height * scale
    
    val imageOffsetX = (containerSize.width - scaledImageWidth) / 2f
    val imageOffsetY = (containerSize.height - scaledImageHeight) / 2f
    
    // Initial crop rect (center 80% of image)
    var cropRect by remember {
        mutableStateOf(
            CropRect(
                left = imageOffsetX + scaledImageWidth * 0.1f,
                top = imageOffsetY + scaledImageHeight * 0.1f,
                right = imageOffsetX + scaledImageWidth * 0.9f,
                bottom = imageOffsetY + scaledImageHeight * 0.9f
            )
        )
    }
    
    // Update parent when crop rect changes
    LaunchedEffect(cropRect) {
        // Convert screen coordinates to image coordinates
        val imageLeft = ((cropRect.left - imageOffsetX) / scale).coerceIn(0f, imageSize.width)
        val imageTop = ((cropRect.top - imageOffsetY) / scale).coerceIn(0f, imageSize.height)
        val imageRight = ((cropRect.right - imageOffsetX) / scale).coerceIn(0f, imageSize.width)
        val imageBottom = ((cropRect.bottom - imageOffsetY) / scale).coerceIn(0f, imageSize.height)
        
        onCropRectChange(CropRect(imageLeft, imageTop, imageRight, imageBottom))
    }
    
    Canvas(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    val touchPoint = change.position
                    val handleSize = with(density) { 20.dp.toPx() }
                    
                    // Check which handle or area is being dragged
                    when {
                        // Top-left handle
                        abs(touchPoint.x - cropRect.left) < handleSize && 
                        abs(touchPoint.y - cropRect.top) < handleSize -> {
                            cropRect = cropRect.copy(
                                left = (cropRect.left + dragAmount.x).coerceIn(
                                    imageOffsetX, 
                                    cropRect.right - 50f
                                ),
                                top = (cropRect.top + dragAmount.y).coerceIn(
                                    imageOffsetY, 
                                    cropRect.bottom - 50f
                                )
                            )
                        }
                        
                        // Top-right handle
                        abs(touchPoint.x - cropRect.right) < handleSize && 
                        abs(touchPoint.y - cropRect.top) < handleSize -> {
                            cropRect = cropRect.copy(
                                right = (cropRect.right + dragAmount.x).coerceIn(
                                    cropRect.left + 50f, 
                                    imageOffsetX + scaledImageWidth
                                ),
                                top = (cropRect.top + dragAmount.y).coerceIn(
                                    imageOffsetY, 
                                    cropRect.bottom - 50f
                                )
                            )
                        }
                        
                        // Bottom-left handle
                        abs(touchPoint.x - cropRect.left) < handleSize && 
                        abs(touchPoint.y - cropRect.bottom) < handleSize -> {
                            cropRect = cropRect.copy(
                                left = (cropRect.left + dragAmount.x).coerceIn(
                                    imageOffsetX, 
                                    cropRect.right - 50f
                                ),
                                bottom = (cropRect.bottom + dragAmount.y).coerceIn(
                                    cropRect.top + 50f, 
                                    imageOffsetY + scaledImageHeight
                                )
                            )
                        }
                        
                        // Bottom-right handle
                        abs(touchPoint.x - cropRect.right) < handleSize && 
                        abs(touchPoint.y - cropRect.bottom) < handleSize -> {
                            cropRect = cropRect.copy(
                                right = (cropRect.right + dragAmount.x).coerceIn(
                                    cropRect.left + 50f, 
                                    imageOffsetX + scaledImageWidth
                                ),
                                bottom = (cropRect.bottom + dragAmount.y).coerceIn(
                                    cropRect.top + 50f, 
                                    imageOffsetY + scaledImageHeight
                                )
                            )
                        }
                        
                        // Left edge
                        abs(touchPoint.x - cropRect.left) < handleSize && 
                        touchPoint.y > cropRect.top && touchPoint.y < cropRect.bottom -> {
                            cropRect = cropRect.copy(
                                left = (cropRect.left + dragAmount.x).coerceIn(
                                    imageOffsetX, 
                                    cropRect.right - 50f
                                )
                            )
                        }
                        
                        // Right edge
                        abs(touchPoint.x - cropRect.right) < handleSize && 
                        touchPoint.y > cropRect.top && touchPoint.y < cropRect.bottom -> {
                            cropRect = cropRect.copy(
                                right = (cropRect.right + dragAmount.x).coerceIn(
                                    cropRect.left + 50f, 
                                    imageOffsetX + scaledImageWidth
                                )
                            )
                        }
                        
                        // Top edge
                        abs(touchPoint.y - cropRect.top) < handleSize && 
                        touchPoint.x > cropRect.left && touchPoint.x < cropRect.right -> {
                            cropRect = cropRect.copy(
                                top = (cropRect.top + dragAmount.y).coerceIn(
                                    imageOffsetY, 
                                    cropRect.bottom - 50f
                                )
                            )
                        }
                        
                        // Bottom edge
                        abs(touchPoint.y - cropRect.bottom) < handleSize && 
                        touchPoint.x > cropRect.left && touchPoint.x < cropRect.right -> {
                            cropRect = cropRect.copy(
                                bottom = (cropRect.bottom + dragAmount.y).coerceIn(
                                    cropRect.top + 50f, 
                                    imageOffsetY + scaledImageHeight
                                )
                            )
                        }
                        
                        // Inside crop area - move entire rect
                        touchPoint.x > cropRect.left && touchPoint.x < cropRect.right &&
                        touchPoint.y > cropRect.top && touchPoint.y < cropRect.bottom -> {
                            val newLeft = cropRect.left + dragAmount.x
                            val newTop = cropRect.top + dragAmount.y
                            val newRight = cropRect.right + dragAmount.x
                            val newBottom = cropRect.bottom + dragAmount.y
                            
                            // Ensure the entire rect stays within image bounds
                            if (newLeft >= imageOffsetX && newRight <= imageOffsetX + scaledImageWidth &&
                                newTop >= imageOffsetY && newBottom <= imageOffsetY + scaledImageHeight) {
                                cropRect = CropRect(newLeft, newTop, newRight, newBottom)
                            }
                        }
                    }
                }
            }
    ) {
        drawCropOverlay(
            cropRect = cropRect,
            containerSize = Size(size.width, size.height),
            imageOffset = Offset(imageOffsetX, imageOffsetY),
            imageSize = Size(scaledImageWidth, scaledImageHeight)
        )
    }
}

private fun DrawScope.drawCropOverlay(
    cropRect: CropRect,
    containerSize: Size,
    imageOffset: Offset,
    imageSize: Size
) {
    val overlayColor = Color.Black.copy(alpha = 0.5f)
    val cropBorderColor = Color.White
    val handleColor = Color.White
    val handleSize = 20f
    
    // Draw dark overlay outside crop area
    // Top
    drawRect(
        color = overlayColor,
        topLeft = Offset(0f, 0f),
        size = Size(containerSize.width, cropRect.top)
    )
    
    // Bottom
    drawRect(
        color = overlayColor,
        topLeft = Offset(0f, cropRect.bottom),
        size = Size(containerSize.width, containerSize.height - cropRect.bottom)
    )
    
    // Left
    drawRect(
        color = overlayColor,
        topLeft = Offset(0f, cropRect.top),
        size = Size(cropRect.left, cropRect.height)
    )
    
    // Right
    drawRect(
        color = overlayColor,
        topLeft = Offset(cropRect.right, cropRect.top),
        size = Size(containerSize.width - cropRect.right, cropRect.height)
    )
    
    // Draw crop border
    drawRect(
        color = cropBorderColor,
        topLeft = Offset(cropRect.left, cropRect.top),
        size = Size(cropRect.width, cropRect.height),
        style = Stroke(width = 2f)
    )
    
    // Draw grid lines (rule of thirds)
    val gridColor = cropBorderColor.copy(alpha = 0.5f)
    
    // Vertical lines
    val verticalLine1 = cropRect.left + cropRect.width / 3f
    val verticalLine2 = cropRect.left + (cropRect.width * 2f) / 3f
    
    drawLine(
        color = gridColor,
        start = Offset(verticalLine1, cropRect.top),
        end = Offset(verticalLine1, cropRect.bottom),
        strokeWidth = 1f
    )
    
    drawLine(
        color = gridColor,
        start = Offset(verticalLine2, cropRect.top),
        end = Offset(verticalLine2, cropRect.bottom),
        strokeWidth = 1f
    )
    
    // Horizontal lines
    val horizontalLine1 = cropRect.top + cropRect.height / 3f
    val horizontalLine2 = cropRect.top + (cropRect.height * 2f) / 3f
    
    drawLine(
        color = gridColor,
        start = Offset(cropRect.left, horizontalLine1),
        end = Offset(cropRect.right, horizontalLine1),
        strokeWidth = 1f
    )
    
    drawLine(
        color = gridColor,
        start = Offset(cropRect.left, horizontalLine2),
        end = Offset(cropRect.right, horizontalLine2),
        strokeWidth = 1f
    )
    
    // Draw corner handles
    val handles = listOf(
        Offset(cropRect.left, cropRect.top),      // Top-left
        Offset(cropRect.right, cropRect.top),     // Top-right
        Offset(cropRect.left, cropRect.bottom),   // Bottom-left
        Offset(cropRect.right, cropRect.bottom)   // Bottom-right
    )
    
    handles.forEach { handle ->
        drawCircle(
            color = handleColor,
            radius = handleSize / 2f,
            center = handle
        )
        drawCircle(
            color = Color.Black,
            radius = handleSize / 2f,
            center = handle,
            style = Stroke(width = 2f)
        )
    }
    
    // Draw edge handles (middle of each side)
    val edgeHandles = listOf(
        Offset(cropRect.left, cropRect.top + cropRect.height / 2f),      // Left
        Offset(cropRect.right, cropRect.top + cropRect.height / 2f),     // Right
        Offset(cropRect.left + cropRect.width / 2f, cropRect.top),       // Top
        Offset(cropRect.left + cropRect.width / 2f, cropRect.bottom)     // Bottom
    )
    
    edgeHandles.forEach { handle ->
        drawRect(
            color = handleColor,
            topLeft = Offset(handle.x - handleSize / 4f, handle.y - handleSize / 4f),
            size = Size(handleSize / 2f, handleSize / 2f)
        )
        drawRect(
            color = Color.Black,
            topLeft = Offset(handle.x - handleSize / 4f, handle.y - handleSize / 4f),
            size = Size(handleSize / 2f, handleSize / 2f),
            style = Stroke(width = 2f)
        )
    }
}