package com.dlab.myaipiceditor.data

import android.graphics.Bitmap
import androidx.compose.runtime.Stable

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
    val canRedo: Boolean = false
)

sealed class EditorAction {
    object LoadImage : EditorAction()
    object TakePhoto : EditorAction()
    object StartCrop : EditorAction()
    object CancelCrop : EditorAction()
    data class ConfirmCrop(val cropRect: com.dlab.myaipiceditor.ui.CropRect) : EditorAction()
    object RemoveBackground : EditorAction()
    object RemoveObject : EditorAction()
    object RestoreFace : EditorAction()
    object UpscaleImage : EditorAction()
    data class ResizeImage(val width: Int, val height: Int) : EditorAction()
    data class RotateImage(val degrees: Float) : EditorAction()
    data class AddText(val text: String, val x: Float, val y: Float) : EditorAction()
    object Undo : EditorAction()
    object Redo : EditorAction()
    object SaveImage : EditorAction()
    object ShareImage : EditorAction()
    object ClearError : EditorAction()
}