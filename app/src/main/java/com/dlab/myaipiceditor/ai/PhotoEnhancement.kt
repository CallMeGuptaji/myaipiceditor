package com.dlab.myaipiceditor.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log
import ai.onnxruntime.OnnxTensor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.FloatBuffer
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

object PhotoEnhancement {
    private const val TAG = "PhotoEnhancement"
    private const val TARGET_SIZE = 512
    private const val MAX_SIZE = 1024

    suspend fun enhance(
        context: Context,
        input: Bitmap,
        onProgress: (Float) -> Unit = {}
    ): Bitmap = withContext(Dispatchers.Default) {
        try {
            Log.d(TAG, "Starting photo enhancement: ${input.width}x${input.height}")
            onProgress(0.1f)

            val session = AiModelManager.getSession(AiModelManager.ModelType.IMAGE_UPSCALER)
            val ortEnvironment = AiModelManager.getEnvironment()

            onProgress(0.2f)

            val scaledInput = resizeForProcessing(input)
            Log.d(TAG, "Resized to: ${scaledInput.width}x${scaledInput.height}")
            onProgress(0.3f)

            val inputTensor = preprocessImage(scaledInput, ortEnvironment)
            onProgress(0.4f)

            Log.d(TAG, "Running ESRGAN inference...")
            val outputs = session.run(mapOf("image" to inputTensor))
            onProgress(0.7f)

            val outputTensor = outputs[0].value as Array<Array<Array<FloatArray>>>
            inputTensor.close()
            outputs.close()

            Log.d(TAG, "Post-processing output...")
            val enhanced = postprocessImage(outputTensor, scaledInput.width * 4, scaledInput.height * 4)
            onProgress(0.85f)

            if (scaledInput != input) {
                scaledInput.recycle()
            }

            val finalResult = resizeToOriginalAspect(enhanced, input.width, input.height)
            if (finalResult != enhanced) {
                enhanced.recycle()
            }

            val improvedResult = applyEnhancementBoost(finalResult)
            if (improvedResult != finalResult) {
                finalResult.recycle()
            }

            onProgress(1.0f)
            Log.d(TAG, "Enhancement complete")

            improvedResult
        } catch (e: Exception) {
            Log.e(TAG, "Enhancement failed", e)
            throw e
        }
    }

    private fun resizeForProcessing(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height

        if (width <= MAX_SIZE && height <= MAX_SIZE) {
            val targetDim = if (width > height) {
                if (width <= TARGET_SIZE) return bitmap
                TARGET_SIZE
            } else {
                if (height <= TARGET_SIZE) return bitmap
                TARGET_SIZE
            }

            val scale = targetDim.toFloat() / max(width, height)
            val newWidth = (width * scale).toInt()
            val newHeight = (height * scale).toInt()

            return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
        }

        val scale = MAX_SIZE.toFloat() / max(width, height)
        val newWidth = (width * scale).toInt()
        val newHeight = (height * scale).toInt()

        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }

    private fun preprocessImage(bitmap: Bitmap, ortEnvironment: ai.onnxruntime.OrtEnvironment): OnnxTensor {
        val width = bitmap.width
        val height = bitmap.height

        val floatBuffer = FloatBuffer.allocate(1 * 3 * height * width)

        val rChannel = FloatArray(height * width)
        val gChannel = FloatArray(height * width)
        val bChannel = FloatArray(height * width)

        for (y in 0 until height) {
            for (x in 0 until width) {
                val pixel = bitmap.getPixel(x, y)
                val idx = y * width + x

                rChannel[idx] = Color.red(pixel) / 255.0f
                gChannel[idx] = Color.green(pixel) / 255.0f
                bChannel[idx] = Color.blue(pixel) / 255.0f
            }
        }

        floatBuffer.put(rChannel)
        floatBuffer.put(gChannel)
        floatBuffer.put(bChannel)
        floatBuffer.rewind()

        return OnnxTensor.createTensor(
            ortEnvironment,
            floatBuffer,
            longArrayOf(1, 3, height.toLong(), width.toLong())
        )
    }

    private fun postprocessImage(output: Array<Array<Array<FloatArray>>>, width: Int, height: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

        val rChannel = output[0][0]
        val gChannel = output[0][1]
        val bChannel = output[0][2]

        for (y in 0 until height) {
            for (x in 0 until width) {
                val r = (rChannel[y][x].coerceIn(0f, 1f) * 255).toInt()
                val g = (gChannel[y][x].coerceIn(0f, 1f) * 255).toInt()
                val b = (bChannel[y][x].coerceIn(0f, 1f) * 255).toInt()

                bitmap.setPixel(x, y, Color.rgb(r, g, b))
            }
        }

        return bitmap
    }

    private fun resizeToOriginalAspect(bitmap: Bitmap, targetWidth: Int, targetHeight: Int): Bitmap {
        if (bitmap.width == targetWidth && bitmap.height == targetHeight) {
            return bitmap
        }

        val scale = min(
            targetWidth.toFloat() / bitmap.width,
            targetHeight.toFloat() / bitmap.height
        )

        val scaledWidth = (bitmap.width * scale).toInt()
        val scaledHeight = (bitmap.height * scale).toInt()

        return Bitmap.createScaledBitmap(bitmap, scaledWidth, scaledHeight, true)
    }

    private fun applyEnhancementBoost(bitmap: Bitmap): Bitmap {
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)

        val contrastFactor = 1.1f
        val gamma = 0.95f
        val saturationBoost = 1.15f

        for (y in 0 until result.height) {
            for (x in 0 until result.width) {
                val pixel = result.getPixel(x, y)

                var r = Color.red(pixel) / 255.0f
                var g = Color.green(pixel) / 255.0f
                var b = Color.blue(pixel) / 255.0f

                r = ((r - 0.5f) * contrastFactor + 0.5f).coerceIn(0f, 1f)
                g = ((g - 0.5f) * contrastFactor + 0.5f).coerceIn(0f, 1f)
                b = ((b - 0.5f) * contrastFactor + 0.5f).coerceIn(0f, 1f)

                r = r.pow(gamma)
                g = g.pow(gamma)
                b = b.pow(gamma)

                val gray = 0.299f * r + 0.587f * g + 0.114f * b
                r = (gray + (r - gray) * saturationBoost).coerceIn(0f, 1f)
                g = (gray + (g - gray) * saturationBoost).coerceIn(0f, 1f)
                b = (gray + (b - gray) * saturationBoost).coerceIn(0f, 1f)

                val newR = (r * 255).toInt()
                val newG = (g * 255).toInt()
                val newB = (b * 255).toInt()

                result.setPixel(x, y, Color.rgb(newR, newG, newB))
            }
        }

        return result
    }
}
