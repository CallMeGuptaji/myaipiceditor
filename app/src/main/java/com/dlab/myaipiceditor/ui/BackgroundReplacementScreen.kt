package com.dlab.myaipiceditor.ui

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dlab.myaipiceditor.data.BackgroundMode
import com.dlab.myaipiceditor.data.BrushStroke

@Composable
fun BackgroundReplacementScreen(
    originalImage: Bitmap?,
    currentImage: Bitmap?,
    currentMask: Bitmap?,
    backgroundMode: BackgroundMode,
    selectedColor: Color,
    brushSize: Float,
    isErasing: Boolean,
    threshold: Float,
    brushStrokes: List<BrushStroke>,
    onBackClick: () -> Unit,
    onResetClick: () -> Unit,
    onConfirmClick: () -> Unit,
    onModeChange: (BackgroundMode) -> Unit,
    onColorChange: (Color) -> Unit,
    onBrushSizeChange: (Float) -> Unit,
    onErasingChange: (Boolean) -> Unit,
    onThresholdChange: (Float) -> Unit,
    onBrushStroke: (Offset, Offset) -> Unit,
    onBackgroundImageSelected: (Bitmap) -> Unit,
    onUndoStroke: () -> Unit
) {
    val context = LocalContext.current

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            try {
                val bitmap = android.provider.MediaStore.Images.Media.getBitmap(
                    context.contentResolver,
                    it
                )
                onBackgroundImageSelected(bitmap)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            TopAppBar(
                title = { Text("Background Removal") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onResetClick) {
                        Icon(Icons.Default.Refresh, "Reset")
                    }
                    IconButton(onClick = onConfirmClick) {
                        Icon(Icons.Default.Check, "Confirm")
                    }
                }
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                currentImage?.let { bitmap ->
                    val imageBitmap = remember(bitmap) { bitmap.asImageBitmap() }

                    var canvasSize by remember { mutableStateOf(androidx.compose.ui.geometry.Size.Zero) }

                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                if (backgroundMode == BackgroundMode.Brush) {
                                    var lastPoint: Offset? = null
                                    detectDragGestures(
                                        onDragStart = { offset ->
                                            lastPoint = offset
                                        },
                                        onDrag = { change, _ ->
                                            val currentPoint = change.position
                                            lastPoint?.let { last ->
                                                onBrushStroke(last, currentPoint)
                                            }
                                            lastPoint = currentPoint
                                            change.consume()
                                        },
                                        onDragEnd = {
                                            lastPoint = null
                                        }
                                    )
                                }
                            }
                    ) {
                        canvasSize = size

                        val imageAspect = bitmap.width.toFloat() / bitmap.height.toFloat()
                        val canvasAspect = size.width / size.height

                        val drawWidth: Float
                        val drawHeight: Float

                        if (imageAspect > canvasAspect) {
                            drawWidth = size.width
                            drawHeight = size.width / imageAspect
                        } else {
                            drawHeight = size.height
                            drawWidth = size.height * imageAspect
                        }

                        val left = (size.width - drawWidth) / 2
                        val top = (size.height - drawHeight) / 2

                        drawImage(
                            image = imageBitmap,
                            dstOffset = androidx.compose.ui.geometry.Offset(left, top),
                            dstSize = androidx.compose.ui.geometry.Size(drawWidth, drawHeight)
                        )

                        if (backgroundMode == BackgroundMode.Brush) {
                            brushStrokes.forEach { stroke ->
                                drawCircle(
                                    color = if (stroke.isErasing) Color.Red.copy(alpha = 0.3f)
                                           else Color.Green.copy(alpha = 0.3f),
                                    radius = brushSize / 2,
                                    center = stroke.point,
                                    style = Stroke(width = 2f)
                                )
                            }
                        }
                    }
                }
            }

            BottomControls(
                backgroundMode = backgroundMode,
                selectedColor = selectedColor,
                brushSize = brushSize,
                isErasing = isErasing,
                threshold = threshold,
                onModeChange = onModeChange,
                onColorChange = onColorChange,
                onBrushSizeChange = onBrushSizeChange,
                onErasingChange = onErasingChange,
                onThresholdChange = onThresholdChange,
                onGalleryClick = { imagePickerLauncher.launch("image/*") },
                onUndoStroke = onUndoStroke
            )
        }
    }
}

@Composable
private fun BottomControls(
    backgroundMode: BackgroundMode,
    selectedColor: Color,
    brushSize: Float,
    isErasing: Boolean,
    threshold: Float,
    onModeChange: (BackgroundMode) -> Unit,
    onColorChange: (Color) -> Unit,
    onBrushSizeChange: (Float) -> Unit,
    onErasingChange: (Boolean) -> Unit,
    onThresholdChange: (Float) -> Unit,
    onGalleryClick: () -> Unit,
    onUndoStroke: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp)
    ) {
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            item {
                ModeButton(
                    text = "Auto",
                    icon = Icons.Default.AutoAwesome,
                    isSelected = backgroundMode == BackgroundMode.Auto,
                    onClick = { onModeChange(BackgroundMode.Auto) }
                )
            }
            item {
                ModeButton(
                    text = "Brush",
                    icon = Icons.Default.Brush,
                    isSelected = backgroundMode == BackgroundMode.Brush,
                    onClick = { onModeChange(BackgroundMode.Brush) }
                )
            }
            item {
                ModeButton(
                    text = "Replace",
                    icon = Icons.Default.Image,
                    isSelected = backgroundMode == BackgroundMode.Replace,
                    onClick = { onModeChange(BackgroundMode.Replace) }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        when (backgroundMode) {
            BackgroundMode.Auto -> {
                ThresholdControl(
                    threshold = threshold,
                    onThresholdChange = onThresholdChange
                )
            }
            BackgroundMode.Brush -> {
                BrushControls(
                    brushSize = brushSize,
                    isErasing = isErasing,
                    onBrushSizeChange = onBrushSizeChange,
                    onErasingChange = onErasingChange,
                    onUndoStroke = onUndoStroke
                )
            }
            BackgroundMode.Replace -> {
                ReplaceControls(
                    selectedColor = selectedColor,
                    onColorChange = onColorChange,
                    onGalleryClick = onGalleryClick
                )
            }
        }
    }
}

@Composable
private fun ModeButton(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primary
                           else MaterialTheme.colorScheme.surfaceVariant
        ),
        modifier = Modifier.height(56.dp)
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(4.dp))
        Text(text)
    }
}

@Composable
private fun ThresholdControl(
    threshold: Float,
    onThresholdChange: (Float) -> Unit
) {
    Column {
        Text("Threshold: ${(threshold * 100).toInt()}%", fontSize = 14.sp)
        Slider(
            value = threshold,
            onValueChange = onThresholdChange,
            valueRange = 0f..1f,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun BrushControls(
    brushSize: Float,
    isErasing: Boolean,
    onBrushSizeChange: (Float) -> Unit,
    onErasingChange: (Boolean) -> Unit,
    onUndoStroke: () -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { onErasingChange(true) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isErasing) MaterialTheme.colorScheme.primary
                                   else MaterialTheme.colorScheme.surfaceVariant
                ),
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.DeleteOutline, "Erase")
                Spacer(modifier = Modifier.width(4.dp))
                Text("Erase")
            }

            Button(
                onClick = { onErasingChange(false) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (!isErasing) MaterialTheme.colorScheme.primary
                                   else MaterialTheme.colorScheme.surfaceVariant
                ),
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Add, "Restore")
                Spacer(modifier = Modifier.width(4.dp))
                Text("Restore")
            }

            IconButton(
                onClick = onUndoStroke,
                modifier = Modifier
                    .size(56.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
            ) {
                Icon(Icons.Default.Undo, "Undo")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text("Brush Size: ${brushSize.toInt()}px", fontSize = 14.sp)
        Slider(
            value = brushSize,
            onValueChange = onBrushSizeChange,
            valueRange = 10f..100f,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun ReplaceControls(
    selectedColor: Color,
    onColorChange: (Color) -> Unit,
    onGalleryClick: () -> Unit
) {
    Column {
        Text("Background Options", fontSize = 14.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = onGalleryClick,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.PhotoLibrary, "Gallery")
            Spacer(modifier = Modifier.width(8.dp))
            Text("Choose from Gallery")
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text("Solid Colors", fontSize = 14.sp)

        Spacer(modifier = Modifier.height(8.dp))

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(predefinedColors) { color ->
                ColorButton(
                    color = color,
                    isSelected = selectedColor == color,
                    onClick = { onColorChange(color) }
                )
            }
        }
    }
}

@Composable
private fun ColorButton(
    color: Color,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(color)
            .border(
                width = if (isSelected) 3.dp else 1.dp,
                color = if (isSelected) MaterialTheme.colorScheme.primary
                       else MaterialTheme.colorScheme.outline,
                shape = CircleShape
            )
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { onClick() },
                    onDrag = { _, _ -> }
                )
            }
    )
}

private val predefinedColors = listOf(
    Color.White,
    Color.Black,
    Color(0xFFF44336),
    Color(0xFFE91E63),
    Color(0xFF9C27B0),
    Color(0xFF673AB7),
    Color(0xFF3F51B5),
    Color(0xFF2196F3),
    Color(0xFF03A9F4),
    Color(0xFF00BCD4),
    Color(0xFF009688),
    Color(0xFF4CAF50),
    Color(0xFF8BC34A),
    Color(0xFFCDDC39),
    Color(0xFFFFEB3B),
    Color(0xFFFFC107),
    Color(0xFFFF9800),
    Color(0xFFFF5722)
)
