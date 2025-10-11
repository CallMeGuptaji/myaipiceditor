package com.dlab.myaipiceditor.data

import androidx.compose.ui.geometry.Offset

data class BrushStroke(
    val points: List<Offset>,
    val brushSize: Float,
    val isEraser: Boolean = false
)

data class ObjectRemovalState(
    val strokes: List<BrushStroke> = emptyList(),
    val brushSize: Float = 20f,
    val isEraserMode: Boolean = false,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val isProcessing: Boolean = false,
    val previewMask: Boolean = false,
    val isRefiningMask: Boolean = false,
    val refinedMaskPreview: android.graphics.Bitmap? = null,
    val showRefinedPreview: Boolean = false,
    val showStrokes: Boolean = true,
    val livePreviewOverlay: android.graphics.Bitmap? = null,
    val showLivePreview: Boolean = false
)
