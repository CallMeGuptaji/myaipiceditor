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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay

class EditorViewModel(application: Application) : AndroidViewModel(application) {

    private val _state = MutableStateFlow(EditorState())
    val state: StateFlow<EditorState> = _state.asStateFlow()

    private val history = mutableListOf<Bitmap>()
    private var historyIndex = -1

    private var adjustmentJob: Job? = null
    private var pendingAdjustments: AdjustmentValues? = null
    
    fun handleAction(action: EditorAction) {
        when (action) {
            is EditorAction.LoadImage -> {
                // This will be handled by the UI when image is selected
            }
            is EditorAction.StartCrop -> startCrop()
            is EditorAction.CancelCrop -> cancelCrop()
            is EditorAction.ConfirmCrop -> confirmCrop(action.cropRect)
            is EditorAction.RemoveBackground -> removeBackground()
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
        val currentImage = _state.value.currentImage ?: return
        
        viewModelScope.launch {
            _state.value = _state.value.copy(
                isProcessing = true,
                processingMessage = "Removing background..."
            )
            
            try {
                val result = withContext(Dispatchers.Default) {
                    BackgroundRemoval.removeBackground(getApplication(), currentImage)
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
                    error = "Failed to remove background: ${e.message}"
                )
            }
        }
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
            adjustmentValues = AdjustmentValues(),
            previewImage = _state.value.currentImage
        )
    }

    private fun cancelAdjust() {
        _state.value = _state.value.copy(
            isAdjusting = false,
            adjustmentValues = AdjustmentValues(),
            previewImage = null
        )
    }

    private fun updateAdjustment(type: AdjustmentType, value: Float) {
        val currentImage = _state.value.currentImage ?: return
        val newAdjustments = _state.value.adjustmentValues.setValue(type, value)

        _state.value = _state.value.copy(
            adjustmentValues = newAdjustments
        )

        pendingAdjustments = newAdjustments

        adjustmentJob?.cancel()
        adjustmentJob = viewModelScope.launch {
            delay(50)

            val adjustmentsToApply = pendingAdjustments ?: return@launch

            try {
                val result = withContext(Dispatchers.Default) {
                    PhotoEditorUtils.applyAdjustments(currentImage, adjustmentsToApply)
                }

                if (adjustmentsToApply == pendingAdjustments) {
                    _state.value = _state.value.copy(
                        previewImage = result
                    )
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    error = "Failed to apply adjustment: ${e.message}"
                )
            }
        }
    }

    private fun confirmAdjust() {
        val previewImage = _state.value.previewImage ?: _state.value.currentImage ?: return

        _state.value = _state.value.copy(
            currentImage = previewImage,
            isAdjusting = false,
            adjustmentValues = AdjustmentValues(),
            previewImage = null
        )
        addToHistory(previewImage)
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