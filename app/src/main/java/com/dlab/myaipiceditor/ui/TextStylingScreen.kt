package com.dlab.myaipiceditor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dlab.myaipiceditor.data.TextStyle

data class FontOption(
    val name: String,
    val fontFamily: FontFamily
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TextStylingScreen(
    text: String,
    currentStyle: TextStyle,
    onStyleChange: (TextStyle) -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    canUndo: Boolean = false,
    canRedo: Boolean = false,
    onUndo: () -> Unit = {},
    onRedo: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var textStyle by remember { mutableStateOf(currentStyle) }
    
    LaunchedEffect(textStyle) {
        onStyleChange(textStyle)
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
    
    Scaffold(
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
        }
    ) { paddingValues ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
        ) {
            // Text Preview
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp)
                        .background(
                            color = textStyle.highlightColor,
                            shape = RoundedCornerShape(8.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = text,
                        fontSize = textStyle.fontSize.sp,
                        fontFamily = textStyle.fontFamily,
                        fontWeight = if (textStyle.isBold) FontWeight.Bold else FontWeight.Normal,
                        color = textStyle.color.copy(alpha = textStyle.opacity),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }
            
            // Font Selection
            StylingSection(title = "Font") {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp)
                ) {
                    items(fontOptions) { fontOption ->
                        FontOptionCard(
                            fontOption = fontOption,
                            isSelected = textStyle.fontFamily == fontOption.fontFamily,
                            onClick = {
                                textStyle = textStyle.copy(fontFamily = fontOption.fontFamily)
                            }
                        )
                    }
                }
            }
            
            // Font Size
            StylingSection(title = "Size") {
                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    Text(
                        text = "${textStyle.fontSize.toInt()}sp",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Slider(
                        value = textStyle.fontSize,
                        onValueChange = { textStyle = textStyle.copy(fontSize = it) },
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
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp)
                ) {
                    FilterChip(
                        onClick = { textStyle = textStyle.copy(isBold = !textStyle.isBold) },
                        label = { Text("Bold") },
                        selected = textStyle.isBold,
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                        )
                    )
                }
            }
            
            // Text Color
            StylingSection(title = "Text Color") {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp)
                ) {
                    items(colorOptions) { color ->
                        ColorOption(
                            color = color,
                            isSelected = textStyle.color == color,
                            onClick = {
                                textStyle = textStyle.copy(color = color)
                            }
                        )
                    }
                }
            }
            
            // Highlight Color
            StylingSection(title = "Highlight") {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp)
                ) {
                    items(highlightOptions) { color ->
                        ColorOption(
                            color = color,
                            isSelected = textStyle.highlightColor == color,
                            onClick = {
                                textStyle = textStyle.copy(highlightColor = color)
                            },
                            showBorder = color == Color.Transparent
                        )
                    }
                }
            }
            
            // Opacity
            StylingSection(title = "Opacity") {
                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    Text(
                        text = "${(textStyle.opacity * 100).toInt()}%",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Slider(
                        value = textStyle.opacity,
                        onValueChange = { textStyle = textStyle.copy(opacity = it) },
                        valueRange = 0.1f..1f,
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary
                        )
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
        }
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
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
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
            BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
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