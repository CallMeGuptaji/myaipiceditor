package com.dlab.myaipiceditor.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.dlab.myaipiceditor.data.TextStyle
import android.graphics.Bitmap

data class FontOption(
    val name: String,
    val fontFamily: FontFamily
)

data class TextPosition(
    val x: Float = 0.5f, // Normalized position (0-1)
    val y: Float = 0.5f  // Normalized position (0-1)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TextStylingScreen(
    text: String,
    currentStyle: TextStyle,
    bitmap: Bitmap? = null,
    onStyleChange: (TextStyle) -> Unit,
    onPositionChange: (TextPosition) -> Unit = {},
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    canUndo: Boolean = false,
    canRedo: Boolean = false,
    onUndo: () -> Unit = {},
    onRedo: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var textStyle by remember { mutableStateOf(currentStyle) }
    var textPosition by remember { mutableStateOf(TextPosition()) }
    var imageSize by remember { mutableStateOf(IntSize.Zero) }
    
    val bottomSheetState = rememberBottomSheetScaffoldState()
    
    LaunchedEffect(textStyle) {
        onStyleChange(textStyle)
    }
    
    LaunchedEffect(textPosition) {
        onPositionChange(textPosition)
    }
    
    val fontOptions = remember {
        listOf(
            FontOption("Default", FontFamily.Default),
            FontOption("Serif", FontFamily.Serif),
            FontOption("Sans Serif", FontFamily.SansSerif),
            FontOption("Monospace", FontFamily.Monospace),
            FontOption("Cursive", FontFamily.Cursive)
        )
    }
    
    val colorOptions = remember {
        listOf(
            Color.Black, Color.White, Color.Red, Color.Green, Color.Blue,
            Color.Yellow, Color.Cyan, Color.Magenta, Color.Gray,
            Color(0xFF8E24AA), Color(0xFF1976D2), Color(0xFF388E3C),
            Color(0xFFE64A19), Color(0xFFF57C00), Color(0xFF5D4037)
        )
    }
    
    val highlightOptions = remember {
        listOf(
            Color.Transparent, Color.Yellow.copy(alpha = 0.3f), Color.Green.copy(alpha = 0.3f),
            Color.Blue.copy(alpha = 0.3f), Color.Red.copy(alpha = 0.3f), Color.Cyan.copy(alpha = 0.3f),
            Color.Magenta.copy(alpha = 0.3f), Color.Gray.copy(alpha = 0.3f)
        )
    }

    BottomSheetScaffold(
        scaffoldState = bottomSheetState,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Style Text",
                        fontWeight = FontWeight.Medium
                    )
                },
                navigationIcon = {
                    Row {
                        IconButton(onClick = onCancel) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Cancel")
                        }
                        
                        IconButton(
                            onClick = onUndo,
                            enabled = canUndo
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.Undo,
                                "Undo",
                                tint = if (canUndo) {
                                    MaterialTheme.colorScheme.onSurface
                                } else {
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                }
                            )
                        }
                        
                        IconButton(
                            onClick = onRedo,
                            enabled = canRedo
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.Redo,
                                "Redo",
                                tint = if (canRedo) {
                                    MaterialTheme.colorScheme.onSurface
                                } else {
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                }
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = onConfirm) {
                        Icon(
                            Icons.Default.Check,
                            "Confirm",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        sheetContent = {
            BottomSheetContent(
                textStyle = textStyle,
                onStyleChange = { textStyle = it },
                fontOptions = fontOptions,
                colorOptions = colorOptions,
                highlightOptions = highlightOptions
            )
        },
        sheetPeekHeight = 120.dp,
        modifier = modifier
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Image with text overlay
            if (bitmap != null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .onSizeChanged { size ->
                            imageSize = size
                        }
                        .pointerInput(Unit) {
                            detectTapGestures { offset ->
                                if (imageSize.width > 0 && imageSize.height > 0) {
                                    textPosition = TextPosition(
                                        x = (offset.x / imageSize.width).coerceIn(0f, 1f),
                                        y = (offset.y / imageSize.height).coerceIn(0f, 1f)
                                    )
                                }
                            }
                        }
                ) {
                    // Background image
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "Image to edit",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                    
                    // Text overlay
                    if (imageSize.width > 0 && imageSize.height > 0) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .zIndex(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .offset(
                                        x = (textPosition.x * imageSize.width).dp / LocalDensity.current.density,
                                        y = (textPosition.y * imageSize.height).dp / LocalDensity.current.density
                                    )
                                    .background(
                                        color = textStyle.highlightColor,
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .padding(8.dp)
                            ) {
                                Text(
                                    text = text,
                                    fontSize = textStyle.fontSize.sp,
                                    fontFamily = textStyle.fontFamily,
                                    fontWeight = if (textStyle.isBold) FontWeight.Bold else FontWeight.Normal,
                                    color = textStyle.color.copy(alpha = textStyle.opacity),
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            } else {
                // Fallback when no image is provided
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No image available",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            // Instruction overlay
            Card(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = "Tap anywhere on the image to position your text",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(12.dp),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun BottomSheetContent(
    textStyle: TextStyle,
    onStyleChange: (TextStyle) -> Unit,
    fontOptions: List<FontOption>,
    colorOptions: List<Color>,
    highlightOptions: List<Color>
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // Handle bar
        Box(
            modifier = Modifier
                .width(40.dp)
                .height(4.dp)
                .background(
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    RoundedCornerShape(2.dp)
                )
                .align(Alignment.CenterHorizontally)
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Font Selection
        StylingSection(title = "Font") {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(fontOptions) { fontOption ->
                    FontOptionCard(
                        fontOption = fontOption,
                        isSelected = textStyle.fontFamily == fontOption.fontFamily,
                        onClick = {
                            onStyleChange(textStyle.copy(fontFamily = fontOption.fontFamily))
                        }
                    )
                }
            }
        }
        
        // Font Size
        StylingSection(title = "Size") {
            Column {
                Text(
                    text = "${textStyle.fontSize.toInt()}sp",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Slider(
                    value = textStyle.fontSize,
                    onValueChange = { onStyleChange(textStyle.copy(fontSize = it)) },
                    valueRange = 12f..72f,
                    steps = 59,
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary
                    )
                )
            }
        }
        
        // Bold Toggle
        StylingSection(title = "Style") {
            FilterChip(
                onClick = { onStyleChange(textStyle.copy(isBold = !textStyle.isBold)) },
                label = { Text("Bold") },
                selected = textStyle.isBold,
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
        
        // Text Color
        StylingSection(title = "Text Color") {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(colorOptions) { color ->
                    ColorOption(
                        color = color,
                        isSelected = textStyle.color == color,
                        onClick = {
                            onStyleChange(textStyle.copy(color = color))
                        }
                    )
                }
            }
        }
        
        // Highlight Color
        StylingSection(title = "Highlight") {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(highlightOptions) { color ->
                    ColorOption(
                        color = color,
                        isSelected = textStyle.highlightColor == color,
                        onClick = {
                            onStyleChange(textStyle.copy(highlightColor = color))
                        },
                        showBorder = color == Color.Transparent
                    )
                }
            }
        }
        
        // Opacity
        StylingSection(title = "Opacity") {
            Column {
                Text(
                    text = "${(textStyle.opacity * 100).toInt()}%",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Slider(
                    value = textStyle.opacity,
                    onValueChange = { onStyleChange(textStyle.copy(opacity = it)) },
                    valueRange = 0.1f..1f,
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary
                    )
                )
            }
        }
        
        Spacer(modifier = Modifier.height(100.dp)) // Extra space for better scrolling
    }
}

@Composable
private fun StylingSection(
    title: String,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier.padding(vertical = 8.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(vertical = 8.dp)
        )
        content()
    }
}

@Composable
private fun FontOptionCard(
    fontOption: FontOption,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        border = if (isSelected) {
            null
        } else {
            androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        },
        shape = RoundedCornerShape(12.dp)
    ) {
        Text(
            text = fontOption.name,
            fontFamily = fontOption.fontFamily,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            color = if (isSelected) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurface
            }
        )
    }
}

@Composable
private fun ColorOption(
    color: Color,
    isSelected: Boolean,
    onClick: () -> Unit,
    showBorder: Boolean = false
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(color)
            .then(
                if (showBorder || color == Color.White) {
                    Modifier.border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outline,
                        shape = CircleShape
                    )
                } else Modifier
            )
            .then(
                if (isSelected) {
                    Modifier.border(
                        width = 3.dp,
                        color = MaterialTheme.colorScheme.primary,
                        shape = CircleShape
                    )
                } else Modifier
            )
            .clickable { onClick() }
    )
}