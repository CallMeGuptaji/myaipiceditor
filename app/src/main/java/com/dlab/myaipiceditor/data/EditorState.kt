package com.dlab.myaipiceditor.data

import android.graphics.Bitmap
import androidx.compose.runtime.Stable
import androidx.compose.ui.geometry.Offset
import com.dlab.myaipiceditor.ui.CropRect

@Stable
data class EditorState(
    val originalImage: Bitmap? = null,
    val currentImage: Bitmap? = null,
    val isCropping: Boolean = false,
    val isProcessing: Boolean = false,
    val processingMessage: String = "",
    val error: String? = null,
    val history: List<Bitmap> = emptyList(),
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val isAddingText: Boolean = false,
    val isStylingText: Boolean = false,
    val currentText: String = "",
    val currentTextStyle: TextStyle = TextStyle(),
    val textPosition: TextPosition = TextPosition(),
    val density: Float = 2f,
    val isAdjusting: Boolean = false,
    val adjustmentValues: AdjustmentValues = AdjustmentValues(),
    val isRemovingBackground: Boolean = false,
    val backgroundRemovalState: BackgroundRemovalState = BackgroundRemovalState()
)

data class BackgroundRemovalState(
    val currentMask: Bitmap? = null,
    val backgroundMode: BackgroundMode = BackgroundMode.Auto,
    val selectedBackgroundColor: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color.White,
    val backgroundImage: Bitmap? = null,
    val brushSize: Float = 30f,
    val isErasing: Boolean = true,
    val threshold: Float = 0.5f,
    val brushStrokes: List<BrushStroke> = emptyList()
)

data class BrushStroke(
    val point: Offset,
    val isErasing: Boolean
)

enum class BackgroundMode {
    Auto,
    Brush,
    Replace
}

data class TextPosition(
    val x: Float = 0.5f, // Normalized position (0-1)
    val y: Float = 0.5f  // Normalized position (0-1)
)

sealed class EditorAction {
    object LoadImage : EditorAction()
    object TakePhoto : EditorAction()
    object StartCrop : EditorAction()
    object CancelCrop : EditorAction()
    data class ConfirmCrop(val cropRect: CropRect) : EditorAction()
    object RemoveBackground : EditorAction()
    object StartBackgroundRemoval : EditorAction()
    object CancelBackgroundRemoval : EditorAction()
    object ConfirmBackgroundRemoval : EditorAction()
    object ResetBackgroundRemoval : EditorAction()
    data class UpdateBackgroundMode(val mode: BackgroundMode) : EditorAction()
    data class UpdateBackgroundColor(val color: androidx.compose.ui.graphics.Color) : EditorAction()
    data class UpdateBackgroundImage(val bitmap: Bitmap) : EditorAction()
    data class UpdateBrushSize(val size: Float) : EditorAction()
    data class UpdateErasing(val isErasing: Boolean) : EditorAction()
    data class UpdateThreshold(val threshold: Float) : EditorAction()
    data class ApplyBrushStroke(val start: Offset, val end: Offset) : EditorAction()
    object UndoBrushStroke : EditorAction()
    object RemoveObject : EditorAction()
    object RestoreFace : EditorAction()
    object UpscaleImage : EditorAction()
    data class ResizeImage(val width: Int, val height: Int) : EditorAction()
    data class RotateImage(val degrees: Float) : EditorAction()
    object StartAddText : EditorAction()
    object CancelAddText : EditorAction()
    data class ConfirmText(val text: String) : EditorAction()
    object StartTextStyling : EditorAction()
    object CancelTextStyling : EditorAction()
    data class UpdateTextStyle(val style: TextStyle) : EditorAction()
    data class UpdateTextPosition(val position: TextPosition) : EditorAction()
    object ConfirmTextStyling : EditorAction()
    object StartAdjust : EditorAction()
    object CancelAdjust : EditorAction()
    data class UpdateAdjustment(val type: AdjustmentType, val value: Float) : EditorAction()
    object ConfirmAdjust : EditorAction()
    object Undo : EditorAction()
    object Redo : EditorAction()
    object SaveImage : EditorAction()
    object ShareImage : EditorAction()
    object ClearError : EditorAction()
}