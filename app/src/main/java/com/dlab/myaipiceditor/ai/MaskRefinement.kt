package com.dlab.myaipiceditor.ai

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.Log
import ai.onnxruntime.OnnxTensor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.FloatBuffer
import kotlin.math.max
import kotlin.math.min

object MaskRefinement {
    private const val TAG = "MaskRefinement"
    private const val TARGET_SIZE = 320

    suspend fun refineMask(
        originalImage: Bitmap,
        roughMask: Bitmap
    ): Bitmap = withContext(Dispatchers.Default) {
        try {
            Log.d(TAG, "Starting mask refinement...")

            val session = AiModelManager.getSession(AiModelManager.ModelType.FOR_SEGMENTATION)
            val environment = AiModelManager.getEnvironment()

            val resizedImage = resizeForSegmentation(originalImage, TARGET_SIZE)
            val resizedMask = resizeForSegmentation(roughMask, TARGET_SIZE)

            val inputTensor = preprocessImageForU2Net(resizedImage)

            val inputs = mapOf("input" to inputTensor)
            val outputs = session.run(inputs)

            val outputTensor = outputs[0].value as Array<*>
            val segmentationMask = postprocessU2NetOutput(outputTensor, resizedImage.width, resizedImage.height)

            inputTensor.close()
            outputs.close()

            val mergedMask = mergeMasks(resizedMask, segmentationMask, threshold = 0.5f)

            val finalMask = Bitmap.createScaledBitmap(
                mergedMask,
                originalImage.width,
                originalImage.height,
                true
            )

            resizedImage.recycle()
            resizedMask.recycle()
            segmentationMask.recycle()
            mergedMask.recycle()

            Log.d(TAG, "Mask refinement completed successfully")
            finalMask

        } catch (e: Exception) {
            Log.e(TAG, "Error refining mask: ${e.message}", e)
            Bitmap.createBitmap(roughMask.width, roughMask.height, Bitmap.Config.ARGB_8888).apply {
                val canvas = Canvas(this)
                canvas.drawBitmap(roughMask, 0f, 0f, null)
            }
        }
    }

    private fun resizeForSegmentation(bitmap: Bitmap, targetSize: Int): Bitmap {
        val aspectRatio = bitmap.width.toFloat() / bitmap.height.toFloat()
        val (newWidth, newHeight) = if (aspectRatio > 1f) {
            targetSize to (targetSize / aspectRatio).toInt()
        } else {
            (targetSize * aspectRatio).toInt() to targetSize
        }

        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }

    private fun preprocessImageForU2Net(bitmap: Bitmap): OnnxTensor {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        val mean = floatArrayOf(0.485f, 0.456f, 0.406f)
        val std = floatArrayOf(0.229f, 0.224f, 0.225f)

        val buffer = FloatBuffer.allocate(3 * height * width)

        for (c in 0..2) {
            for (y in 0 until height) {
                for (x in 0 until width) {
                    val pixel = pixels[y * width + x]
                    val value = when (c) {
                        0 -> Color.red(pixel) / 255f
                        1 -> Color.green(pixel) / 255f
                        else -> Color.blue(pixel) / 255f
                    }
                    val normalized = (value - mean[c]) / std[c]
                    buffer.put(normalized)
                }
            }
        }

        buffer.rewind()

        val shape = longArrayOf(1, 3, height.toLong(), width.toLong())
        return OnnxTensor.createTensor(AiModelManager.getEnvironment(), buffer, shape)
    }

    private fun postprocessU2NetOutput(output: Array<*>, width: Int, height: Int): Bitmap {
        val outputArray = output[0] as Array<*>
        val channelData = outputArray[0] as Array<*>

        val maskData = FloatArray(width * height)

        for (y in 0 until height) {
            val row = channelData[y] as FloatArray
            for (x in 0 until width) {
                maskData[y * width + x] = sigmoid(row[x])
            }
        }

        val maskBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(width * height)

        for (i in pixels.indices) {
            val intensity = (maskData[i] * 255).toInt().coerceIn(0, 255)
            pixels[i] = Color.argb(255, intensity, intensity, intensity)
        }

        maskBitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        return maskBitmap
    }

    private fun sigmoid(x: Float): Float {
        return 1f / (1f + kotlin.math.exp(-x))
    }

    private fun mergeMasks(userMask: Bitmap, aiMask: Bitmap, threshold: Float): Bitmap {
        val width = userMask.width
        val height = userMask.height

        val userPixels = IntArray(width * height)
        val aiPixels = IntArray(width * height)

        userMask.getPixels(userPixels, 0, width, 0, 0, width, height)
        aiMask.getPixels(aiPixels, 0, width, 0, 0, width, height)

        val mergedBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val mergedPixels = IntArray(width * height)

        for (i in mergedPixels.indices) {
            val userIntensity = Color.red(userPixels[i]) / 255f
            val aiIntensity = Color.red(aiPixels[i]) / 255f

            val hasUserMark = userIntensity > 0.1f

            val finalIntensity = if (hasUserMark) {
                expandMask(aiIntensity, userIntensity, threshold)
            } else {
                0f
            }

            val intensity = (finalIntensity * 255).toInt().coerceIn(0, 255)
            mergedPixels[i] = Color.argb(255, intensity, intensity, intensity)
        }

        mergedBitmap.setPixels(mergedPixels, 0, width, 0, 0, width, height)
        return mergedBitmap
    }

    private fun expandMask(aiValue: Float, userValue: Float, threshold: Float): Float {
        return if (aiValue >= threshold || userValue > 0.5f) {
            max(aiValue, userValue)
        } else {
            userValue * 0.3f
        }
    }

    fun createMaskFromStrokes(
        width: Int,
        height: Int,
        strokes: List<com.dlab.myaipiceditor.data.BrushStroke>
    ): Bitmap {
        val maskBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(maskBitmap)
        canvas.drawColor(Color.BLACK)

        val paint = Paint().apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            isAntiAlias = true
        }

        strokes.forEach { stroke ->
            if (stroke.points.size > 1) {
                paint.strokeWidth = stroke.brushSize * 2f

                for (i in 0 until stroke.points.size - 1) {
                    val start = stroke.points[i]
                    val end = stroke.points[i + 1]
                    canvas.drawLine(
                        start.x * width,
                        start.y * height,
                        end.x * width,
                        end.y * height,
                        paint
                    )
                }
            }
        }

        return maskBitmap
    }
}
