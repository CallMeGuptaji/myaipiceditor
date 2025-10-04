package com.dlab.myaipiceditor.viewmodel

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Typeface
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dlab.myaipiceditor.ai.FaceRestoration
import com.dlab.myaipiceditor.ai.ImageUpscaler
import com.dlab.myaipiceditor.ai.MaskRefinement
import com.dlab.myaipiceditor.ai.ObjectRemoval
import com.dlab.myaipiceditor.data.AdjustmentType
import com.dlab.myaipiceditor.data.AdjustmentValues
import com.dlab.myaipiceditor.data.BrushStroke
import com.dlab.myaipiceditor.data.EditorAction
import com.dlab.myaipiceditor.data.EditorState
import com.dlab.myaipiceditor.data.ObjectRemovalState
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

    private val removalStrokeHistory = mutableListOf<List<BrushStroke>>()
    private var removalStrokeIndex = -1

    fun handleAction(action: EditorAction) {
        when (action) {
            is EditorAction.LoadImage -> {
                // This will be handled by the UI when image is selected
            }
            is EditorAction.StartCrop -> startCrop()
            is EditorAction.CancelCrop -> cancelCrop()
            is EditorAction.ConfirmCrop -> confirmCrop(action.cropRect)
            is EditorAction.StartObjectRemoval -> startObjectRemoval()
            is EditorAction.CancelObjectRemoval -> cancelObjectRemoval()
            is EditorAction.ConfirmObjectRemoval -> confirmObjectRemoval()
            is EditorAction.AddRemovalStroke -> addRemovalStroke(action.stroke)
            is EditorAction.UndoRemovalStroke -> undoRemovalStroke()
            is EditorAction.RedoRemovalStroke -> redoRemovalStroke()
            is EditorAction.ResetRemovalStrokes -> resetRemovalStrokes()
            is EditorAction.UpdateBrushSize -> updateBrushSize(action.size)
            is EditorAction.ApplyObjectRemoval -> applyObjectRemoval()
            is EditorAction.RefineAndPreviewMask -> refineAndPreviewMask()
            is EditorAction.AcceptRefinedMask -> acceptRefinedMask()
            is EditorAction.RejectRefinedMask -> rejectRefinedMask()
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

    private fun startObjectRemoval() {
        _state.value = _state.value.copy(
            isRemovingObject = true,
            objectRemovalState = ObjectRemovalState()
        )
        removalStrokeHistory.clear()
        removalStrokeHistory.add(emptyList())
        removalStrokeIndex = 0
    }

    private fun cancelObjectRemoval() {
        _state.value = _state.value.copy(
            isRemovingObject = false,
            objectRemovalState = ObjectRemovalState()
        )
        removalStrokeHistory.clear()
        removalStrokeIndex = -1
    }

    private fun confirmObjectRemoval() {
        _state.value = _state.value.copy(
            isRemovingObject = false,
            objectRemovalState = ObjectRemovalState()
        )
        removalStrokeHistory.clear()
        removalStrokeIndex = -1
    }

    private fun addRemovalStroke(stroke: BrushStroke) {
        val currentStrokes = _state.value.objectRemovalState.strokes
        val newStrokes = currentStrokes + stroke

        while (removalStrokeHistory.size > removalStrokeIndex + 1) {
            removalStrokeHistory.removeAt(removalStrokeHistory.size - 1)
        }

        removalStrokeHistory.add(newStrokes)
        removalStrokeIndex = removalStrokeHistory.size - 1

        if (removalStrokeHistory.size > 50) {
            removalStrokeHistory.removeAt(0)
            removalStrokeIndex--
        }

        _state.value = _state.value.copy(
            objectRemovalState = _state.value.objectRemovalState.copy(
                strokes = newStrokes,
                canUndo = removalStrokeIndex > 0,
                canRedo = removalStrokeIndex < removalStrokeHistory.size - 1
            )
        )
    }

    private fun undoRemovalStroke() {
        if (removalStrokeIndex > 0) {
            removalStrokeIndex--
            val strokes = removalStrokeHistory[removalStrokeIndex]
            _state.value = _state.value.copy(
                objectRemovalState = _state.value.objectRemovalState.copy(
                    strokes = strokes,
                    canUndo = removalStrokeIndex > 0,
                    canRedo = removalStrokeIndex < removalStrokeHistory.size - 1
                )
            )
        }
    }

    private fun redoRemovalStroke() {
        if (removalStrokeIndex < removalStrokeHistory.size - 1) {
            removalStrokeIndex++
            val strokes = removalStrokeHistory[removalStrokeIndex]
            _state.value = _state.value.copy(
                objectRemovalState = _state.value.objectRemovalState.copy(
                    strokes = strokes,
                    canUndo = removalStrokeIndex > 0,
                    canRedo = removalStrokeIndex < removalStrokeHistory.size - 1
                )
            )
        }
    }

    private fun resetRemovalStrokes() {
        removalStrokeHistory.clear()
        removalStrokeHistory.add(emptyList())
        removalStrokeIndex = 0

        _state.value = _state.value.copy(
            objectRemovalState = _state.value.objectRemovalState.copy(
                strokes = emptyList(),
                canUndo = false,
                canRedo = false
            )
        )
    }

    private fun updateBrushSize(size: Float) {
        _state.value = _state.value.copy(
            objectRemovalState = _state.value.objectRemovalState.copy(
                brushSize = size
            )
        )
    }

    private fun toggleEraserMode() {
        _state.value = _state.value.copy(
            objectRemovalState = _state.value.objectRemovalState.copy(
                isEraserMode = !_state.value.objectRemovalState.isEraserMode
            )
        )
    }

    private fun refineAndPreviewMask() {
        val currentImage = _state.value.currentImage ?: return
        val strokes = _state.value.objectRemovalState.strokes

        if (strokes.isEmpty()) return

        viewModelScope.launch {
            _state.value = _state.value.copy(
                objectRemovalState = _state.value.objectRemovalState.copy(
                    isRefiningMask = true,
                    showStrokes = false
                )
            )

            try {
                val roughMask = withContext(Dispatchers.Default) {
                    MaskRefinement.createMaskFromStrokes(
                        currentImage.width,
                        currentImage.height,
                        strokes
                    )
                }

                val refinedMask = withContext(Dispatchers.Default) {
                    MaskRefinement.refineMask(currentImage, roughMask)
                }

                roughMask.recycle()

                _state.value = _state.value.copy(
                    objectRemovalState = _state.value.objectRemovalState.copy(
                        isRefiningMask = false,
                        refinedMaskPreview = refinedMask,
                        showRefinedPreview = true,
                        showStrokes = false
                    )
                )

            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    objectRemovalState = _state.value.objectRemovalState.copy(
                        isRefiningMask = false,
                        showStrokes = true
                    ),
                    error = "Failed to refine mask: ${e.message}"
                )
            }
        }
    }

    private fun acceptRefinedMask() {
        val currentImage = _state.value.currentImage ?: return
        val refinedMask = _state.value.objectRemovalState.refinedMaskPreview ?: return

        viewModelScope.launch {
            _state.value = _state.value.copy(
                objectRemovalState = _state.value.objectRemovalState.copy(
                    isProcessing = true,
                    showRefinedPreview = false
                )
            )

            try {
                val result = withContext(Dispatchers.Default) {
                    ObjectRemoval.removeObject(getApplication(), currentImage, refinedMask)
                }

                _state.value = _state.value.copy(
                    currentImage = result,
                    objectRemovalState = _state.value.objectRemovalState.copy(
                        strokes = emptyList(),
                        isProcessing = false,
                        refinedMaskPreview = null,
                        canUndo = false,
                        canRedo = false
                    )
                )
                addToHistory(result)
                removalStrokeHistory.clear()
                removalStrokeHistory.add(emptyList())
                removalStrokeIndex = 0

            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    objectRemovalState = _state.value.objectRemovalState.copy(
                        isProcessing = false,
                        showRefinedPreview = false,
                        refinedMaskPreview = null
                    ),
                    error = "Failed to remove object: ${e.message}"
                )
            }
        }
    }

    private fun rejectRefinedMask() {
        _state.value.objectRemovalState.refinedMaskPreview?.recycle()
        _state.value = _state.value.copy(
            objectRemovalState = _state.value.objectRemovalState.copy(
                showRefinedPreview = false,
                refinedMaskPreview = null,
                showStrokes = true
            )
        )
    }

    private fun applyObjectRemoval() {
        val currentImage = _state.value.currentImage ?: return
        val strokes = _state.value.objectRemovalState.strokes

        if (strokes.isEmpty()) return

        viewModelScope.launch {
            _state.value = _state.value.copy(
                objectRemovalState = _state.value.objectRemovalState.copy(
                    isProcessing = true
                )
            )

            try {
                val mask = withContext(Dispatchers.Default) {
                    ObjectRemoval.createMaskFromStrokes(
                        strokes,
                        currentImage.width,
                        currentImage.height
                    )
                }

                val result = withContext(Dispatchers.Default) {
                    ObjectRemoval.removeObject(getApplication(), currentImage, mask)
                }

                _state.value = _state.value.copy(
                    currentImage = result,
                    objectRemovalState = _state.value.objectRemovalState.copy(
                        strokes = emptyList(),
                        isProcessing = false,
                        canUndo = false,
                        canRedo = false
                    )
                )
                addToHistory(result)
                removalStrokeHistory.clear()
                removalStrokeHistory.add(emptyList())
                removalStrokeIndex = 0

            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    objectRemovalState = _state.value.objectRemovalState.copy(
                        isProcessing = false
                    ),
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

}