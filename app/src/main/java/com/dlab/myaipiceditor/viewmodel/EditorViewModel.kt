package com.dlab.myaipiceditor.viewmodel

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Typeface
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dlab.myaipiceditor.ai.BackgroundRemoval
import com.dlab.myaipiceditor.ai.FaceRestoration
import com.dlab.myaipiceditor.ai.ImageUpscaler
import com.dlab.myaipiceditor.ai.ObjectRemoval
import com.dlab.myaipiceditor.data.AdjustmentType
import com.dlab.myaipiceditor.data.AdjustmentValues
import com.dlab.myaipiceditor.data.BackgroundMode
import com.dlab.myaipiceditor.data.BrushStroke
import com.dlab.myaipiceditor.data.EditorAction
import com.dlab.myaipiceditor.data.EditorState
import com.dlab.myaipiceditor.data.TextStyle
import com.dlab.myaipiceditor.data.TextPosition
import com.dlab.myaipiceditor.ui.CropRect
import com.dlab.myaipiceditor.PhotoEditorUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class EditorViewModel(application: Application) : AndroidViewModel(application) {

    private val _state = MutableStateFlow(EditorState())
    val state: StateFlow<EditorState> = _state.asStateFlow()

    private val history = mutableListOf<Bitmap>()
    private var historyIndex = -1

    fun handleAction(action: EditorAction) {
        when (action) {
            is EditorAction.LoadImage -> {
                // This will be handled by the UI when image is selected
            }
            is EditorAction.StartCrop -> startCrop()
            is EditorAction.CancelCrop -> cancelCrop()
            is EditorAction.ConfirmCrop -> confirmCrop(action.cropRect)
            is EditorAction.RemoveBackground -> removeBackground()
            is EditorAction.StartBackgroundRemoval -> startBackgroundRemoval()
            is EditorAction.CancelBackgroundRemoval -> cancelBackgroundRemoval()
            is EditorAction.ConfirmBackgroundRemoval -> confirmBackgroundRemoval()
            is EditorAction.ResetBackgroundRemoval -> resetBackgroundRemoval()
            is EditorAction.UpdateBackgroundMode -> updateBackgroundMode(action.mode)
            is EditorAction.UpdateBackgroundColor -> updateBackgroundColor(action.color)
            is EditorAction.UpdateBackgroundImage -> updateBackgroundImage(action.bitmap)
            is EditorAction.UpdateBrushSize -> updateBrushSize(action.size)
            is EditorAction.UpdateErasing -> updateErasing(action.isErasing)
            is EditorAction.UpdateThreshold -> updateThreshold(action.threshold)
            is EditorAction.ApplyBrushStroke -> applyBrushStroke(action.start, action.end)
            is EditorAction.UndoBrushStroke -> undoBrushStroke()
            is EditorAction.RemoveObject -> {
                // For now, we'll implement a simple version
                // In a full implementation, user would select the object area
                removeObject()
            }
            is EditorAction.RestoreFace -> restoreFace()
            is EditorAction.UpscaleImage -> upscaleImage()
            is EditorAction.ResizeImage -> resizeImage(action.width, action.height)
            is EditorAction.RotateImage -> rotateImage(action.degrees)
            is EditorAction.StartAddText -> startAddText()
            is EditorAction.CancelAddText -> cancelAddText()
            is EditorAction.ConfirmText -> confirmText(action.text)
            is EditorAction.StartTextStyling -> startTextStyling()
            is EditorAction.CancelTextStyling -> cancelTextStyling()
            is EditorAction.UpdateTextStyle -> updateTextStyle(action.style)
            is EditorAction.UpdateTextPosition -> updateTextPosition(action.position)
            is EditorAction.ConfirmTextStyling -> confirmTextStyling()
            is EditorAction.StartAdjust -> startAdjust()
            is EditorAction.CancelAdjust -> cancelAdjust()
            is EditorAction.UpdateAdjustment -> updateAdjustment(action.type, action.value)
            is EditorAction.ConfirmAdjust -> confirmAdjust()
            is EditorAction.Undo -> undo()
            is EditorAction.Redo -> redo()
            is EditorAction.SaveImage -> saveImage()
            is EditorAction.ShareImage -> shareImage()
            is EditorAction.ClearError -> clearError()
            else -> {}
        }
    }

    fun setImage(bitmap: Bitmap) {
        _state.value = _state.value.copy(
            originalImage = bitmap,
            currentImage = bitmap,
            error = null
        )
        addToHistory(bitmap)
    }

    private fun startCrop() {
        _state.value = _state.value.copy(isCropping = true)
    }

    private fun cancelCrop() {
        _state.value = _state.value.copy(isCropping = false)
    }

    private fun confirmCrop(cropRect: CropRect) {
        val currentImage = _state.value.currentImage ?: return

        try {
            val result = PhotoEditorUtils.crop(
                currentImage,
                cropRect.left.toInt(),
                cropRect.top.toInt(),
                cropRect.width.toInt(),
                cropRect.height.toInt()
            )
            _state.value = _state.value.copy(
                currentImage = result,
                isCropping = false
            )
            addToHistory(result)
        } catch (e: Exception) {
            _state.value = _state.value.copy(
                error = "Failed to crop image: ${e.message}",
                isCropping = false
            )
        }
    }

    private fun removeBackground() {
        startBackgroundRemoval()
    }

    private fun startBackgroundRemoval() {
        val currentImage = _state.value.currentImage ?: return

        viewModelScope.launch {
            _state.value = _state.value.copy(
                isProcessing = true,
                processingMessage = "Processing background..."
            )

            try {
                val threshold = _state.value.backgroundRemovalState.threshold
                val result = BackgroundRemoval.removeBackgroundAsync(getApplication(), currentImage, threshold, usePreview = true)
                val mask = withContext(Dispatchers.Default) {
                    extractMaskFromTransparent(result)
                }

                val preview = applyCurrentBackgroundSettings(currentImage, mask)

                _state.value = _state.value.copy(
                    isRemovingBackground = true,
                    isProcessing = false,
                    processingMessage = "",
                    backgroundRemovalState = _state.value.backgroundRemovalState.copy(
                        currentMask = mask
                    ),
                    currentImage = preview
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isProcessing = false,
                    processingMessage = "",
                    error = "Failed to process background: ${e.message}"
                )
            }
        }
    }

    private fun cancelBackgroundRemoval() {
        val originalImage = history.getOrNull(historyIndex)
        _state.value = _state.value.copy(
            isRemovingBackground = false,
            currentImage = originalImage,
            backgroundRemovalState = com.dlab.myaipiceditor.data.BackgroundRemovalState()
        )
    }

    private fun confirmBackgroundRemoval() {
        val currentImage = _state.value.currentImage ?: return
        _state.value = _state.value.copy(
            isRemovingBackground = false,
            backgroundRemovalState = com.dlab.myaipiceditor.data.BackgroundRemovalState()
        )
        addToHistory(currentImage)
    }

    private fun resetBackgroundRemoval() {
        startBackgroundRemoval()
    }

    private fun updateBackgroundMode(mode: BackgroundMode) {
        val currentImage = _state.value.originalImage ?: return
        val mask = _state.value.backgroundRemovalState.currentMask ?: return

        _state.value = _state.value.copy(
            backgroundRemovalState = _state.value.backgroundRemovalState.copy(
                backgroundMode = mode
            )
        )

        updatePreview(currentImage, mask)
    }

    private fun updateBackgroundColor(color: androidx.compose.ui.graphics.Color) {
        val currentImage = _state.value.originalImage ?: return
        val mask = _state.value.backgroundRemovalState.currentMask ?: return

        _state.value = _state.value.copy(
            backgroundRemovalState = _state.value.backgroundRemovalState.copy(
                selectedBackgroundColor = color
            )
        )

        updatePreview(currentImage, mask)
    }

    private fun updateBackgroundImage(bitmap: Bitmap) {
        val currentImage = _state.value.originalImage ?: return
        val mask = _state.value.backgroundRemovalState.currentMask ?: return

        _state.value = _state.value.copy(
            backgroundRemovalState = _state.value.backgroundRemovalState.copy(
                backgroundImage = bitmap,
                backgroundMode = BackgroundMode.Replace
            )
        )

        updatePreview(currentImage, mask)
    }

    private fun updateBrushSize(size: Float) {
        _state.value = _state.value.copy(
            backgroundRemovalState = _state.value.backgroundRemovalState.copy(
                brushSize = size
            )
        )
    }

    private fun updateErasing(isErasing: Boolean) {
        _state.value = _state.value.copy(
            backgroundRemovalState = _state.value.backgroundRemovalState.copy(
                isErasing = isErasing
            )
        )
    }

    private fun updateThreshold(threshold: Float) {
        _state.value = _state.value.copy(
            backgroundRemovalState = _state.value.backgroundRemovalState.copy(
                threshold = threshold
            )
        )

        val currentImage = _state.value.originalImage ?: return
        startBackgroundRemoval()
    }

    private fun applyBrushStroke(start: androidx.compose.ui.geometry.Offset, end: androidx.compose.ui.geometry.Offset) {
        val mask = _state.value.backgroundRemovalState.currentMask ?: return
        val brushSize = _state.value.backgroundRemovalState.brushSize
        val isErasing = _state.value.backgroundRemovalState.isErasing

        viewModelScope.launch {
            try {
                val updatedMask = withContext(Dispatchers.Default) {
                    BackgroundRemoval.refineMaskWithBrush(mask, end.x, end.y, brushSize, isErasing)
                }

                val strokes = _state.value.backgroundRemovalState.brushStrokes.toMutableList()
                strokes.add(BrushStroke(end, isErasing))

                _state.value = _state.value.copy(
                    backgroundRemovalState = _state.value.backgroundRemovalState.copy(
                        currentMask = updatedMask,
                        brushStrokes = strokes
                    )
                )

                val currentImage = _state.value.originalImage ?: return@launch
                updatePreview(currentImage, updatedMask)
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    error = "Failed to apply brush stroke: ${e.message}"
                )
            }
        }
    }

    private fun undoBrushStroke() {
        val strokes = _state.value.backgroundRemovalState.brushStrokes
        if (strokes.isEmpty()) return

        val newStrokes = strokes.dropLast(1)
        _state.value = _state.value.copy(
            backgroundRemovalState = _state.value.backgroundRemovalState.copy(
                brushStrokes = newStrokes
            )
        )
    }

    private fun updatePreview(image: Bitmap, mask: Bitmap) {
        viewModelScope.launch {
            try {
                val preview = withContext(Dispatchers.Default) {
                    applyCurrentBackgroundSettings(image, mask)
                }
                _state.value = _state.value.copy(currentImage = preview)
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    error = "Failed to update preview: ${e.message}"
                )
            }
        }
    }

    private fun applyCurrentBackgroundSettings(image: Bitmap, mask: Bitmap): Bitmap {
        val mode = _state.value.backgroundRemovalState.backgroundMode
        val color = _state.value.backgroundRemovalState.selectedBackgroundColor
        val bgImage = _state.value.backgroundRemovalState.backgroundImage

        return when (mode) {
            BackgroundMode.Auto -> {
                BackgroundRemoval.applyBackgroundColor(image, mask, android.graphics.Color.TRANSPARENT)
            }
            BackgroundMode.Brush -> {
                BackgroundRemoval.applyBackgroundColor(image, mask, android.graphics.Color.TRANSPARENT)
            }
            BackgroundMode.Replace -> {
                if (bgImage != null) {
                    BackgroundRemoval.applyBackgroundImage(image, mask, bgImage)
                } else {
                    val androidColor = android.graphics.Color.rgb(
                        (color.red * 255).toInt(),
                        (color.green * 255).toInt(),
                        (color.blue * 255).toInt()
                    )
                    BackgroundRemoval.applyBackgroundColor(image, mask, androidColor)
                }
            }
        }
    }

    private fun extractMaskFromTransparent(bitmap: Bitmap): Bitmap {
        val mask = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        for (y in 0 until bitmap.height) {
            for (x in 0 until bitmap.width) {
                val pixel = bitmap.getPixel(x, y)
                val alpha = android.graphics.Color.alpha(pixel)
                mask.setPixel(x, y, android.graphics.Color.argb(alpha, 255, 255, 255))
            }
        }
        return mask
    }

    private fun removeObject() {
        val currentImage = _state.value.currentImage ?: return

        viewModelScope.launch {
            _state.value = _state.value.copy(
                isProcessing = true,
                processingMessage = "Removing object..."
            )

            try {
                // For demo purposes, we'll create a simple mask
                // In a real app, user would draw/select the area to remove
                val mask = createDemoMask(currentImage)

                val result = withContext(Dispatchers.Default) {
                    ObjectRemoval.removeObject(getApplication(), currentImage, mask)
                }

                _state.value = _state.value.copy(
                    currentImage = result,
                    isProcessing = false,
                    processingMessage = ""
                )
                addToHistory(result)

            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isProcessing = false,
                    processingMessage = "",
                    error = "Failed to remove object: ${e.message}"
                )
            }
        }
    }

    private fun restoreFace() {
        val currentImage = _state.value.currentImage ?: return

        viewModelScope.launch {
            _state.value = _state.value.copy(
                isProcessing = true,
                processingMessage = "Restoring faces..."
            )

            try {
                val result = withContext(Dispatchers.Default) {
                    FaceRestoration.restoreFace(getApplication(), currentImage)
                }

                _state.value = _state.value.copy(
                    currentImage = result,
                    isProcessing = false,
                    processingMessage = ""
                )
                addToHistory(result)

            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isProcessing = false,
                    processingMessage = "",
                    error = "Failed to restore face: ${e.message}"
                )
            }
        }
    }

    private fun upscaleImage() {
        val currentImage = _state.value.currentImage ?: return

        viewModelScope.launch {
            _state.value = _state.value.copy(
                isProcessing = true,
                processingMessage = "Upscaling image..."
            )

            try {
                val result = withContext(Dispatchers.Default) {
                    ImageUpscaler.upscale(getApplication(), currentImage)
                }

                _state.value = _state.value.copy(
                    currentImage = result,
                    isProcessing = false,
                    processingMessage = ""
                )
                addToHistory(result)

            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isProcessing = false,
                    processingMessage = "",
                    error = "Failed to upscale image: ${e.message}"
                )
            }
        }
    }

    private fun resizeImage(width: Int, height: Int) {
        val currentImage = _state.value.currentImage ?: return

        try {
            val result = PhotoEditorUtils.resize(currentImage, width, height)
            _state.value = _state.value.copy(currentImage = result)
            addToHistory(result)
        } catch (e: Exception) {
            _state.value = _state.value.copy(error = "Failed to resize image: ${e.message}")
        }
    }

    private fun rotateImage(degrees: Float) {
        val currentImage = _state.value.currentImage ?: return

        try {
            val result = PhotoEditorUtils.rotate(currentImage, degrees)
            _state.value = _state.value.copy(currentImage = result)
            addToHistory(result)
        } catch (e: Exception) {
            _state.value = _state.value.copy(error = "Failed to rotate image: ${e.message}")
        }
    }

    private fun startAddText() {
        _state.value = _state.value.copy(
            isAddingText = true,
            currentText = "",
            currentTextStyle = TextStyle()
        )
    }

    private fun cancelAddText() {
        _state.value = _state.value.copy(
            isAddingText = false,
            currentText = "",
            currentTextStyle = TextStyle()
        )
    }

    private fun confirmText(text: String) {
        _state.value = _state.value.copy(
            isAddingText = false,
            isStylingText = true,
            currentText = text
        )
    }

    private fun startTextStyling() {
        _state.value = _state.value.copy(isStylingText = true)
    }

    private fun cancelTextStyling() {
        _state.value = _state.value.copy(
            isStylingText = false,
            currentText = "",
            currentTextStyle = TextStyle()
        )
    }

    private fun updateTextStyle(style: TextStyle) {
        _state.value = _state.value.copy(currentTextStyle = style)
    }

    private fun updateTextPosition(position: TextPosition) {
        _state.value = _state.value.copy(textPosition = position)
    }

    private fun confirmTextStyling() {
        val currentImage = _state.value.currentImage ?: return
        val text = _state.value.currentText
        val style = _state.value.currentTextStyle
        val position = _state.value.textPosition
        val density = _state.value.density

        try {
            // Calculate actual position from normalized position
            val x = position.x * currentImage.width
            val y = position.y * currentImage.height

            val result = PhotoEditorUtils.addStyledText(currentImage, text, x, y, style, density)
            _state.value = _state.value.copy(
                currentImage = result,
                isStylingText = false,
                currentText = "",
                currentTextStyle = TextStyle(),
                textPosition = TextPosition()
            )
            addToHistory(result)
        } catch (e: Exception) {
            _state.value = _state.value.copy(
                error = "Failed to add text: ${e.message}",
                isStylingText = false,
                currentText = "",
                currentTextStyle = TextStyle(),
                textPosition = TextPosition()
            )
        }
    }

    fun setDensity(density: Float) {
        _state.value = _state.value.copy(density = density)
    }

    private fun startAdjust() {
        _state.value = _state.value.copy(
            isAdjusting = true,
            adjustmentValues = AdjustmentValues()
        )
    }

    private fun cancelAdjust() {
        _state.value = _state.value.copy(
            isAdjusting = false,
            adjustmentValues = AdjustmentValues()
        )
    }

    private fun updateAdjustment(type: AdjustmentType, value: Float) {
        val newAdjustments = _state.value.adjustmentValues.setValue(type, value)
        _state.value = _state.value.copy(
            adjustmentValues = newAdjustments
        )
    }

    private fun confirmAdjust() {
        val currentImage = _state.value.currentImage ?: return
        val adjustments = _state.value.adjustmentValues

        if (adjustments == AdjustmentValues()) {
            _state.value = _state.value.copy(
                isAdjusting = false,
                adjustmentValues = AdjustmentValues()
            )
            return
        }

        viewModelScope.launch {
            _state.value = _state.value.copy(
                isProcessing = true,
                processingMessage = "Applying adjustments..."
            )

            try {
                val result = withContext(Dispatchers.Default) {
                    PhotoEditorUtils.applyAdjustments(currentImage, adjustments)
                }

                _state.value = _state.value.copy(
                    currentImage = result,
                    isAdjusting = false,
                    adjustmentValues = AdjustmentValues(),
                    isProcessing = false,
                    processingMessage = ""
                )
                addToHistory(result)
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isProcessing = false,
                    processingMessage = "",
                    error = "Failed to apply adjustments: ${e.message}"
                )
            }
        }
    }

    private fun addToHistory(bitmap: Bitmap) {
        // Remove any items after current index
        while (history.size > historyIndex + 1) {
            history.removeAt(history.size - 1)
        }

        history.add(bitmap)
        historyIndex = history.size - 1

        // Limit history size to prevent memory issues
        if (history.size > 10) {
            history.removeAt(0)
            historyIndex--
        }

        updateHistoryState()
    }

    private fun undo() {
        if (historyIndex > 0) {
            historyIndex--
            _state.value = _state.value.copy(currentImage = history[historyIndex])
            updateHistoryState()
        }
    }

    private fun redo() {
        if (historyIndex < history.size - 1) {
            historyIndex++
            _state.value = _state.value.copy(currentImage = history[historyIndex])
            updateHistoryState()
        }
    }

    private fun updateHistoryState() {
        _state.value = _state.value.copy(
            canUndo = historyIndex > 0,
            canRedo = historyIndex < history.size - 1
        )
    }

    private fun saveImage() {
        // This will be implemented in the UI layer
    }

    private fun shareImage() {
        // This will be implemented in the UI layer
    }

    private fun clearError() {
        _state.value = _state.value.copy(error = null)
    }

    private fun createDemoMask(bitmap: Bitmap): Bitmap {
        // Create a simple circular mask in the center for demo
        val mask = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(mask)
        val paint = android.graphics.Paint().apply {
            color = android.graphics.Color.WHITE
            isAntiAlias = true
        }

        val centerX = bitmap.width / 2f
        val centerY = bitmap.height / 2f
        val radius = minOf(bitmap.width, bitmap.height) / 8f

        canvas.drawCircle(centerX, centerY, radius, paint)
        return mask
    }
}