package com.dlab.myaipiceditor.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CropScreen(
    bitmap: Bitmap,
    onCropConfirm: (CropRect) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    var containerSize by remember { mutableStateOf(Size.Zero) }
    var cropRect by remember { mutableStateOf<CropRect?>(null) }
    
    val imageSize = Size(bitmap.width.toFloat(), bitmap.height.toFloat())
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Crop Image",
                        fontWeight = FontWeight.Medium
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Cancel")
                    }
                },
                actions = {
                    IconButton(onClick = onCancel) {
                        Icon(
                            Icons.Default.Close,
                            "Cancel",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                    
                    IconButton(
                        onClick = {
                            cropRect?.let { onCropConfirm(it) }
                        },
                        enabled = cropRect != null
                    ) {
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
        bottomBar = {
            CropBottomBar(
                onReset = {
                    // Reset to default crop (80% of image)
                    if (containerSize != Size.Zero) {
                        val scale = minOf(
                            containerSize.width / imageSize.width,
                            containerSize.height / imageSize.height
                        )
                        
                        val scaledImageWidth = imageSize.width * scale
                        val scaledImageHeight = imageSize.height * scale
                        
                        val imageOffsetX = (containerSize.width - scaledImageWidth) / 2f
                        val imageOffsetY = (containerSize.height - scaledImageHeight) / 2f
                        
                        cropRect = CropRect(
                            left = imageOffsetX + scaledImageWidth * 0.1f,
                            top = imageOffsetY + scaledImageHeight * 0.1f,
                            right = imageOffsetX + scaledImageWidth * 0.9f,
                            bottom = imageOffsetY + scaledImageHeight * 0.9f
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Image
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .onSizeChanged { size ->
                        containerSize = Size(size.width.toFloat(), size.height.toFloat())
                    },
                contentAlignment = Alignment.Center
            ) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "Image to crop",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
                
                // Crop overlay
                if (containerSize != Size.Zero) {
                    CropOverlay(
                        imageSize = imageSize,
                        containerSize = containerSize,
                        onCropRectChange = { newCropRect ->
                            cropRect = newCropRect
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
            
            // Instructions
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
                    text = "Drag the corners and edges to adjust the crop area",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(12.dp),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
fun CropBottomBar(
    onReset: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 8.dp,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            OutlinedButton(
                onClick = onReset,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Reset")
            }
        }
    }
}