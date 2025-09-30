package com.dlab.myaipiceditor.data

import android.graphics.Bitmap
import androidx.compose.runtime.Stable
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
    val density: Float = 2f
)

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
    object Undo : EditorAction()
    object Redo : EditorAction()
    object SaveImage : EditorAction()
    object ShareImage : EditorAction()
    object ClearError : EditorAction()
}