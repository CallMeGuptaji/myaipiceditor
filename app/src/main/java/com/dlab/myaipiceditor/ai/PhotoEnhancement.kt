package com.dlab.myaipiceditor.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.nio.FloatBuffer
import kotlin.math.ceil
import kotlin.math.min
import kotlin.math.pow

object PhotoEnhancement {
    private const val TAG = "PhotoEnhancement"
    private const val TILE_SIZE = 256  // EDSR works well with larger tiles
    private const val UPSCALE_FACTOR = 2  // EDSR 2x model

    suspend fun enhance(
        context: Context,
        input: Bitmap,
        onProgress: (Float) -> Unit = {}
    ): Bitmap = withContext(Dispatchers.Default) {
        var scaledInput: Bitmap? = null
        var result: Bitmap? = null
        var finalResult: Bitmap? = null

        try {
            Log.d(TAG, "Starting photo enhancement: ${input.width}x${input.height}")
            onProgress(0.05f)

            val session = AiModelManager.getSession(AiModelManager.ModelType.IMAGE_UPSCALER)
            val ortEnvironment = AiModelManager.getEnvironment()

            onProgress(0.1f)

            scaledInput = resizeForProcessing(input)
            Log.d(TAG, "Processing size: ${scaledInput.width}x${scaledInput.height}")

            val outputWidth = scaledInput.width * UPSCALE_FACTOR
            val outputHeight = scaledInput.height * UPSCALE_FACTOR
            result = Bitmap.createBitmap(outputWidth, outputHeight, Bitmap.Config.ARGB_8888)

            val tilesX = ceil(scaledInput.width.toFloat() / TILE_SIZE).toInt()
            val tilesY = ceil(scaledInput.height.toFloat() / TILE_SIZE).toInt()
            val totalTiles = tilesX * tilesY

            Log.d(TAG, "Processing ${totalTiles} tiles (${tilesX}x${tilesY})")
            onProgress(0.15f)

            var processedTiles = 0

            for (tileY in 0 until tilesY) {
                for (tileX in 0 until tilesX) {
                    // Process tile with proper resource cleanup
                    processTile(
                        scaledInput, result, session, ortEnvironment,
                        tileX, tileY
                    )

                    processedTiles++
                    val progress = 0.15f + (processedTiles.toFloat() / totalTiles) * 0.70f
                    onProgress(progress)

                    // Gentle GC every few tiles
                    if (processedTiles % 3 == 0) {
                        System.gc()
                        delay(30)
                    }
                }
            }

            if (scaledInput != input) {
                scaledInput.recycle()
                scaledInput = null
            }

            onProgress(0.90f)

            finalResult = resizeToOriginalAspect(result, input.width, input.height)
            if (finalResult != result) {
                result.recycle()
                result = null
            }

            onProgress(0.95f)

            val improvedResult = applyEnhancementBoost(finalResult)
            if (improvedResult != finalResult) {
                finalResult.recycle()
                finalResult = null
            }

            onProgress(1.0f)
            Log.d(TAG, "Enhancement complete")

            improvedResult
        } catch (e: Exception) {
            Log.e(TAG, "Enhancement failed: ${e.message}", e)
            // Clean up resources on error
            scaledInput?.recycle()
            result?.recycle()
            finalResult?.recycle()
            throw e
        }
    }

    private fun processTile(
        scaledInput: Bitmap,
        result: Bitmap,
        session: ai.onnxruntime.OrtSession,
        ortEnvironment: ai.onnxruntime.OrtEnvironment,
        tileX: Int,
        tileY: Int
    ) {
        var tile: Bitmap? = null
        var inputTensor: OnnxTensor? = null
        var outputs: OrtSession.Result? = null
        var enhancedTile: Bitmap? = null

        try {
            // Calculate tile boundaries
            val startX = (tileX * TILE_SIZE).coerceIn(0, scaledInput.width - 1)
            val startY = (tileY * TILE_SIZE).coerceIn(0, scaledInput.height - 1)

            // Calculate actual tile dimensions from source
            val actualWidth = TILE_SIZE.coerceAtMost(scaledInput.width - startX)
            val actualHeight = TILE_SIZE.coerceAtMost(scaledInput.height - startY)

            // Ensure valid dimensions
            if (actualWidth <= 0 || actualHeight <= 0) {
                Log.w(TAG, "Skipping invalid tile at ($tileX, $tileY)")
                return
            }

            // Extract tile from source
            tile = Bitmap.createBitmap(scaledInput, startX, startY, actualWidth, actualHeight)

            // EDSR can handle variable input sizes, no padding needed
            inputTensor = preprocessImage(tile, ortEnvironment)
            outputs = session.run(mapOf("input" to inputTensor))

            // EDSR output tensor format
            val outputTensor = outputs?.get(0)?.value as Array<Array<Array<FloatArray>>>

            // Process output (2x upscaled)
            enhancedTile = postprocessImage(outputTensor, actualWidth * UPSCALE_FACTOR, actualHeight * UPSCALE_FACTOR)

            val destX = startX * UPSCALE_FACTOR
            val destY = startY * UPSCALE_FACTOR

            // Copy to result
            copyTileToResult(enhancedTile, result, destX, destY)

        } finally {
            // CRITICAL: Clean up all resources
            tile?.recycle()
            inputTensor?.close()
            outputs?.close()
            enhancedTile?.recycle()
        }
    }

    private fun copyTileToResult(tile: Bitmap, result: Bitmap, destX: Int, destY: Int) {
        val tilePixels = IntArray(tile.width * tile.height)
        tile.getPixels(tilePixels, 0, tile.width, 0, 0, tile.width, tile.height)

        for (y in 0 until tile.height) {
            for (x in 0 until tile.width) {
                val dx = destX + x
                val dy = destY + y
                if (dx < result.width && dy < result.height) {
                    result.setPixel(dx, dy, tilePixels[y * tile.width + x])
                }
            }
        }
    }

    private fun resizeForProcessing(bitmap: Bitmap): Bitmap {
        // EDSR is more efficient, can handle larger inputs
        val maxDimension = 512  // Much better than Real-ESRGAN
        val width = bitmap.width
        val height = bitmap.height

        if (width <= maxDimension && height <= maxDimension) {
            return bitmap
        }

        val scale = maxDimension.toFloat() / kotlin.math.max(width, height)
        val newWidth = (width * scale).toInt()
        val newHeight = (height * scale).toInt()

        Log.d(TAG, "Resizing from ${width}x${height} to ${newWidth}x${newHeight}")
        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }

    private fun preprocessImage(bitmap: Bitmap, ortEnvironment: ai.onnxruntime.OrtEnvironment): OnnxTensor {
        val width = bitmap.width
        val height = bitmap.height

        val floatBuffer = FloatBuffer.allocate(1 * 3 * height * width)

        // EDSR expects normalized RGB values [0, 1]
        for (c in 0 until 3) {
            for (y in 0 until height) {
                for (x in 0 until width) {
                    val pixel = bitmap.getPixel(x, y)
                    val value = when (c) {
                        0 -> Color.red(pixel) / 255.0f
                        1 -> Color.green(pixel) / 255.0f
                        else -> Color.blue(pixel) / 255.0f
                    }
                    floatBuffer.put(value)
                }
            }
        }

        floatBuffer.rewind()

        return OnnxTensor.createTensor(
            ortEnvironment,
            floatBuffer,
            longArrayOf(1, 3, height.toLong(), width.toLong())
        )
    }

    private fun postprocessImage(output: Array<Array<Array<FloatArray>>>, width: Int, height: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(width * height)

        val rChannel = output[0][0]
        val gChannel = output[0][1]
        val bChannel = output[0][2]

        // Convert back from normalized values
        for (y in 0 until height) {
            for (x in 0 until width) {
                val r = (rChannel[y][x].coerceIn(0f, 1f) * 255).toInt()
                val g = (gChannel[y][x].coerceIn(0f, 1f) * 255).toInt()
                val b = (bChannel[y][x].coerceIn(0f, 1f) * 255).toInt()

                pixels[y * width + x] = Color.rgb(r, g, b)
            }
        }

        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
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
        val width = result.width
        val height = result.height
        val pixels = IntArray(width * height)
        result.getPixels(pixels, 0, width, 0, 0, width, height)

        // Gentler enhancement for EDSR output
        val contrastFactor = 1.05f
        val gamma = 0.98f
        val saturationBoost = 1.08f

        // Process pixels in batch
        for (i in pixels.indices) {
            val pixel = pixels[i]

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

            pixels[i] = Color.rgb(newR, newG, newB)
        }

        result.setPixels(pixels, 0, width, 0, 0, width, height)
        return result
    }
}