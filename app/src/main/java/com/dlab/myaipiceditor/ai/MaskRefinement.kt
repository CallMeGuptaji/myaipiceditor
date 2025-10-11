package com.dlab.myaipiceditor.ai

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.Log
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.FloatBuffer
import java.nio.LongBuffer
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import android.graphics.Path
import com.dlab.myaipiceditor.data.BrushStroke

object MaskRefinement {
    private const val TAG = "MaskRefinement"
    // MobileSAM encoder input size
    private const val MODEL_INPUT_SIZE = 1024

    suspend fun refineMask(
        originalImage: Bitmap,
        roughMask: Bitmap
    ): Bitmap = withContext(Dispatchers.Default) {
        // Declare all disposable resources outside try block for cleanup in finally
        var encoderSessionOutputs: OrtSession.Result? = null
        var decoderSessionOutputs: OrtSession.Result? = null
        var inputTensor: OnnxTensor? = null
        var imageEmbeddingTensor: OnnxTensor? = null
        var pointCoordsTensor: OnnxTensor? = null
        var pointLabelsTensor: OnnxTensor? = null
        var maskInputTensor: OnnxTensor? = null
        var hasMaskInputTensor: OnnxTensor? = null
        var resized: Bitmap? = null
        var segmentationMask: Bitmap? = null
        var refinedMask: Bitmap? = null
        var finalMaskScaled: Bitmap? = null

        // Helper function for returning empty mask on failure or no input
        fun createEmptyMask(width: Int, height: Int): Bitmap =
            Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLACK) }

        try {
            Log.d(TAG, "Starting MobileSAM mask refinement (Encoder + Decoder)")

            val encoderSession = AiModelManager.getSession(AiModelManager.ModelType.MOBILE_SAM_ENCODER)
            val decoderSession = AiModelManager.getSession(AiModelManager.ModelType.MOBILE_SAM_DECODER)
            val env = AiModelManager.getEnvironment()

            // 1. Prompt Generation: Get the center point from the user's rough mask
            val centerPoint = getCenterFromRoughMask(roughMask)
            if (centerPoint.first == 0f) {
                Log.d(TAG, "No rough mask detected, returning empty mask.")
                return@withContext createEmptyMask(originalImage.width, originalImage.height)
            }

            // 2. Preprocessing: Resize image to 1024x1024 for the encoder
            resized = Bitmap.createScaledBitmap(
                originalImage,
                MODEL_INPUT_SIZE,
                MODEL_INPUT_SIZE,
                true
            )

            // 3. Encoder Preprocessing: Prepare the image tensor (NCHW 3x1024x1024)
            inputTensor = preprocessImageForSam(resized)

            // 4. Run Encoder: Generates the image embedding
            Log.d(TAG, "Running SAM Encoder...")
            encoderSessionOutputs = encoderSession.run(mapOf("input" to inputTensor))

            // FIX: Converting the multi-dimensional array output from the encoder into a
            // FloatBuffer to resolve the 'Argument type mismatch' error with OrtEnvironment/OrtAllocator ambiguity.
            val imageEmbeddingOutputTensor = encoderSessionOutputs.get("image_embeddings") as? OnnxTensor
            if (imageEmbeddingOutputTensor == null) throw IllegalStateException("Image embedding OnnxTensor not found.")

            // The raw value is a multi-dimensional float array (1, 256, 64, 64)
            val imageEmbeddingArray = imageEmbeddingOutputTensor.value as? Array<Array<Array<FloatArray>>>
                ?: throw IllegalStateException("Image embedding value is not a 4D float array.")

            // Flatten the 4D array into a FloatBuffer
            val embeddingSize = 1 * 256 * 64 * 64
            val embeddingBuffer = FloatBuffer.allocate(embeddingSize)

            imageEmbeddingArray.forEach { arr3d ->
                arr3d.forEach { arr2d ->
                    arr2d.forEach { arr1d ->
                        embeddingBuffer.put(arr1d)
                    }
                }
            }

            embeddingBuffer.rewind()

            // Image Embedding shape: [1, 256, 64, 64]
            val embeddingShape = longArrayOf(1, 256, 64, 64)

            // Create the tensor using the OrtEnvironment and the FloatBuffer, which is the unambiguous overload.
            imageEmbeddingTensor = OnnxTensor.createTensor(env, embeddingBuffer, embeddingShape)


            // 5. Decoder Preprocessing: Prepare prompt tensors (click point)

            // Scale normalized point coordinates to 1024x1024 space
            val pointCoords = floatArrayOf(
                centerPoint.first * MODEL_INPUT_SIZE,
                centerPoint.second * MODEL_INPUT_SIZE
            )

            // point_coords shape: [1, 1, 2]
            pointCoordsTensor = OnnxTensor.createTensor(
                env,
                FloatBuffer.wrap(pointCoords),
                longArrayOf(1, 1, 2)
            )

            // point_labels shape: [1, 1]. Value 1L for a foreground point.
            pointLabelsTensor = OnnxTensor.createTensor(
                env,
                LongBuffer.wrap(longArrayOf(1L)),
                longArrayOf(1, 1)
            )

            // mask_input shape: [1, 1, 256, 256] float (Zeroed out for first pass)
            val maskInput = FloatArray(256 * 256) { 0f }
            maskInputTensor = OnnxTensor.createTensor(
                env,
                FloatBuffer.wrap(maskInput),
                longArrayOf(1, 1, 256, 256)
            )

            // has_mask_input shape: [1]. Value 0f means no previous mask is provided.
            hasMaskInputTensor = OnnxTensor.createTensor(
                env,
                FloatBuffer.wrap(floatArrayOf(0f)),
                longArrayOf(1)
            )

            // 6. Run Decoder
            val decoderInputs = mapOf(
                "image_embeddings" to imageEmbeddingTensor,
                "point_coords" to pointCoordsTensor,
                "point_labels" to pointLabelsTensor,
                "mask_input" to maskInputTensor,
                "has_mask_input" to hasMaskInputTensor
            )

            Log.d(TAG, "Running SAM Decoder...")
            decoderSessionOutputs = decoderSession.run(decoderInputs)

            // 7. Postprocessing: Get the mask logit tensor
            // Output shape: [1, 1, 256, 256] (mask logits)
            val maskLogitsOutputTensor = decoderSessionOutputs.get("masks") as? OnnxTensor
            val maskLogitsArray = maskLogitsOutputTensor?.value as? Array<Array<Array<FloatArray>>>
                ?: throw IllegalStateException("Mask logits output not found or is not the correct tensor type.")

            // 8. Scale logits back to 1024x1024 bitmap and apply sigmoid
            // Pass the 256x256 logits array: maskLogitsArray[0][0]
            segmentationMask = postprocessSamMaskLogits(maskLogitsArray[0][0])

            // 9. Constrain: Limit the 1024x1024 segmentation mask to the bounds of the user's rough mask
            refinedMask = constrainMaskToRoughBounds(segmentationMask, roughMask)

            // 10. Final Scale: Resize the refined mask back to original image dimensions
            finalMaskScaled = Bitmap.createScaledBitmap(
                refinedMask,
                originalImage.width,
                originalImage.height,
                true
            )

            // RefinedMask and SegmentationMask are intermediate, recycle them
            refinedMask.recycle()
            segmentationMask.recycle()

            Log.d(TAG, "Mask refinement completed successfully")
            return@withContext finalMaskScaled

        } catch (e: Exception) {
            Log.e(TAG, "Error refining mask: ${e.message}", e)
            // On failure, return the original rough mask scaled to match the original size
            return@withContext Bitmap.createScaledBitmap(
                roughMask,
                originalImage.width,
                originalImage.height,
                true
            )
        } finally {
            // Clean up all resources
            inputTensor?.close()
            imageEmbeddingTensor?.close()
            pointCoordsTensor?.close()
            pointLabelsTensor?.close()
            maskInputTensor?.close()
            hasMaskInputTensor?.close()
            encoderSessionOutputs?.close()
            decoderSessionOutputs?.close()
            resized?.recycle()
        }
    }

    /**
     * Finds the center point of the drawn rough mask pixels, normalized to [0, 1].
     * Returns Pair<Float, Float> (x, y). Returns (0f, 0f) if no mask pixels are found.
     */
    private fun getCenterFromRoughMask(roughMask: Bitmap): Pair<Float, Float> {
        val width = roughMask.width
        val height = roughMask.height
        val pixels = IntArray(width * height)
        roughMask.getPixels(pixels, 0, width, 0, 0, width, height)

        var totalX = 0
        var totalY = 0
        var pixelCount = 0

        // Use a threshold to consider a pixel as part of the mask
        val maskThreshold = 25 // Grayscale value > 25 (out of 255)

        for (y in 0 until height) {
            for (x in 0 until width) {
                // Check the red channel (since it's a grayscale mask)
                if (Color.red(pixels[y * width + x]) > maskThreshold) {
                    totalX += x
                    totalY += y
                    pixelCount++
                }
            }
        }

        return if (pixelCount > 0) {
            // Normalized coordinates (0.0 to 1.0)
            val centerX = totalX.toFloat() / pixelCount.toFloat() / width.toFloat()
            val centerY = totalY.toFloat() / pixelCount.toFloat() / height.toFloat()
            Pair(centerX, centerY)
        } else {
            // Return (0f, 0f) to signal that no mask was drawn
            Pair(0f, 0f)
        }
    }

    /**
     * Preprocesses image for MobileSAM encoder: NCHW (3x1024x1024), Normalize.
     * Note: The input bitmap must already be 1024x1024.
     */
    private fun preprocessImageForSam(bitmap: Bitmap): OnnxTensor {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        // MobileSAM/ImageNet normalization stats (non-normalized mean/std)
        val mean = floatArrayOf(123.675f, 116.28f, 103.53f)
        val std = floatArrayOf(58.395f, 57.12f, 57.375f)

        // Buffer size is C * H * W
        val buffer = FloatBuffer.allocate(3 * height * width)

        // Fill buffer in NCHW order (channel major: RRR...GGG...BBB...)
        for (c in 0..2) {
            for (y in 0 until height) {
                for (x in 0 until width) {
                    val pixel = pixels[y * width + x]
                    val colorValue = when (c) {
                        0 -> Color.red(pixel)
                        1 -> Color.green(pixel)
                        else -> Color.blue(pixel)
                    }
                    // Standardization: (Value - Mean) / Std
                    val normalized = (colorValue.toFloat() - mean[c]) / std[c]
                    buffer.put(normalized)
                }
            }
        }

        buffer.rewind()

        val shape = longArrayOf(1, 3, height.toLong(), width.toLong())
        return OnnxTensor.createTensor(AiModelManager.getEnvironment(), buffer, shape)
    }

    /**
     * Post-processes the raw mask logits from the SAM decoder.
     * Scales the 256x256 logits to 1024x1024 (via nearest neighbor scaling), applies sigmoid,
     * and converts to a grayscale bitmap.
     * @param logits 2D array of mask logits (256x256).
     */
    private fun postprocessSamMaskLogits(logits: Array<FloatArray>): Bitmap {
        val logitWidth = logits[0].size
        val logitHeight = logits.size

        // Target size is 1024x1024. SAM decoder output is 256x256.
        val outputWidth = MODEL_INPUT_SIZE
        val outputHeight = MODEL_INPUT_SIZE
        val scaleFactor = outputWidth / logitWidth

        val maskBitmap = Bitmap.createBitmap(outputWidth, outputHeight, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(outputWidth * outputHeight)

        for (y in 0 until outputHeight) {
            // Find corresponding coordinate in the 256x256 logit grid
            val logitY = (y.toFloat() / scaleFactor).toInt().coerceIn(0, logitHeight - 1)
            for (x in 0 until outputWidth) {
                val logitX = (x.toFloat() / scaleFactor).toInt().coerceIn(0, logitWidth - 1)

                // Get the raw logit value
                val logit = logits[logitY][logitX]

                // Apply sigmoid to convert logit output to probability [0, 1]
                val probability = 1f / (1f + exp(-logit))

                val intensity = (probability * 255).roundToInt().coerceIn(0, 255)
                // Create grayscale pixel
                pixels[y * outputWidth + x] = Color.argb(255, intensity, intensity, intensity)
            }
        }

        maskBitmap.setPixels(pixels, 0, outputWidth, 0, 0, outputWidth, outputHeight)
        return maskBitmap
    }

    /**
     * Constrains the AI segmentation mask (1024x1024) to the bounding box of the rough mask (scaled to 1024x1024).
     */
    private fun constrainMaskToRoughBounds(
        segmentationMask: Bitmap,
        roughMask: Bitmap
    ): Bitmap {
        val width = MODEL_INPUT_SIZE
        val height = MODEL_INPUT_SIZE

        // 1. Get bounds from the rough mask (must be resized to 1024x1024 for bounding box calculation)
        val roughMaskResized = Bitmap.createScaledBitmap(roughMask, width, height, true)
        val roughPixels = IntArray(width * height)
        roughMaskResized.getPixels(roughPixels, 0, width, 0, 0, width, height)

        val bounds = getRoughMaskBounds(roughPixels, width, height)

        roughMaskResized.recycle() // Recycle the temporary resized mask

        // If no bounds found, return a transparent mask
        if (bounds == null) {
            return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLACK) }
        }

        // 2. Expand bounds slightly (20 pixels)
        val expansion = 20
        val expandedBounds = expandBounds(bounds, width, height, expansion)
        val (minX, minY, maxX, maxY) = expandedBounds

        // 3. Apply bounds to the segmentation mask
        val segPixels = IntArray(width * height)
        segmentationMask.getPixels(segPixels, 0, width, 0, 0, width, height)

        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val resultPixels = IntArray(width * height)

        for (y in 0 until height) {
            for (x in 0 until width) {
                val idx = y * width + x
                val segValue = Color.red(segPixels[idx]) // Intensity [0-255]

                val finalValue = if (x >= minX && x <= maxX && y >= minY && y <= maxY) {
                    // Inside expanded bounds, use the AI mask value
                    segValue
                } else {
                    // Outside bounds, strongly discourage the mask by setting to 0,
                    // preventing MobileSAM from bleeding far outside the user's intended area.
                    if (segValue > 240) segValue else 0
                }

                resultPixels[idx] = Color.argb(255, finalValue, finalValue, finalValue)
            }
        }

        result.setPixels(resultPixels, 0, width, 0, 0, width, height)
        return result
    }

    private fun getRoughMaskBounds(pixels: IntArray, width: Int, height: Int): IntArray? {
        var minX = width
        var minY = height
        var maxX = 0
        var maxY = 0
        var hasPixels = false

        for (y in 0 until height) {
            for (x in 0 until width) {
                val value = Color.red(pixels[y * width + x])
                if (value > 25) { // Threshold for mask pixels
                    hasPixels = true
                    minX = min(minX, x)
                    minY = min(minY, y)
                    maxX = max(maxX, x)
                    maxY = max(maxY, y)
                }
            }
        }

        return if (hasPixels) intArrayOf(minX, minY, maxX, maxY) else null
    }

    private fun expandBounds(
        bounds: IntArray,
        width: Int,
        height: Int,
        expansion: Int
    ): IntArray {
        val minX = max(0, bounds[0] - expansion)
        val minY = max(0, bounds[1] - expansion)
        val maxX = min(width - 1, bounds[2] + expansion)
        val maxY = min(height - 1, bounds[3] + expansion)
        return intArrayOf(minX, minY, maxX, maxY)
    }

    /**
     * Creates a grayscale mask bitmap from a list of normalized brush strokes.
     * Uses Path for accurate rendering of rounded brush caps and joins.
     */
    fun createMaskFromStrokes(
        width: Int,
        height: Int,
        strokes: List<BrushStroke>
    ): Bitmap {
        val maskBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(maskBitmap)
        canvas.drawColor(Color.BLACK) // Start with a black (empty) mask

        val paint = Paint().apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            isAntiAlias = true
        }

        strokes.forEach { stroke ->
            paint.color = if (stroke.isEraser) {
                Color.BLACK
            } else {
                Color.WHITE
            }
            paint.strokeWidth = stroke.brushSize * 2f

            if (stroke.points.size > 1) {
                val path = Path()
                val firstPoint = stroke.points.first()

                // Scale normalized points [0, 1] back to actual pixel coordinates
                path.moveTo(firstPoint.x * width, firstPoint.y * height)

                for (i in 1 until stroke.points.size) {
                    val point = stroke.points[i]
                    path.lineTo(point.x * width, point.y * height)
                }

                canvas.drawPath(path, paint)
            }
        }

        return maskBitmap
    }
}
