package com.dlab.myaipiceditor.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log
import ai.onnxruntime.OnnxTensor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.FloatBuffer
import kotlin.math.ceil
import kotlin.math.min
import kotlin.math.pow

object PhotoEnhancement {
    private const val TAG = "PhotoEnhancement"
    private const val TILE_SIZE = 128
    private const val OVERLAP = 16

    suspend fun enhance(
        context: Context,
        input: Bitmap,
        onProgress: (Float) -> Unit = {}
    ): Bitmap = withContext(Dispatchers.Default) {
        try {
            Log.d(TAG, "Starting photo enhancement: ${input.width}x${input.height}")
            onProgress(0.05f)

            val session = AiModelManager.getSession(AiModelManager.ModelType.IMAGE_UPSCALER)
            val ortEnvironment = AiModelManager.getEnvironment()

            onProgress(0.1f)

            val scaledInput = resizeForProcessing(input)
            Log.d(TAG, "Processing size: ${scaledInput.width}x${scaledInput.height}")

            val outputWidth = scaledInput.width * 4
            val outputHeight = scaledInput.height * 4
            val result = Bitmap.createBitmap(outputWidth, outputHeight, Bitmap.Config.ARGB_8888)

            val tilesX = ceil(scaledInput.width.toFloat() / TILE_SIZE).toInt()
            val tilesY = ceil(scaledInput.height.toFloat() / TILE_SIZE).toInt()
            val totalTiles = tilesX * tilesY

            Log.d(TAG, "Processing ${totalTiles} tiles (${tilesX}x${tilesY})")
            onProgress(0.15f)

            var processedTiles = 0

            for (tileY in 0 until tilesY) {
                for (tileX in 0 until tilesX) {
                    val startX = (tileX * TILE_SIZE).coerceAtMost(scaledInput.width - TILE_SIZE)
                    val startY = (tileY * TILE_SIZE).coerceAtMost(scaledInput.height - TILE_SIZE)
                    val tileWidth = TILE_SIZE.coerceAtMost(scaledInput.width - startX)
                    val tileHeight = TILE_SIZE.coerceAtMost(scaledInput.height - startY)

                    val tile = Bitmap.createBitmap(scaledInput, startX, startY, tileWidth, tileHeight)

                    val inputTensor = preprocessImage(tile, ortEnvironment)
                    val outputs = session.run(mapOf("input" to inputTensor))
                    val outputTensor = outputs[0].value as Array<Array<Array<FloatArray>>>

                    inputTensor.close()
                    outputs.close()

                    val enhancedTile = postprocessImage(outputTensor, tileWidth * 4, tileHeight * 4)

                    val destX = startX * 4
                    val destY = startY * 4

                    for (y in 0 until enhancedTile.height) {
                        for (x in 0 until enhancedTile.width) {
                            val pixel = enhancedTile.getPixel(x, y)
                            if (destX + x < outputWidth && destY + y < outputHeight) {
                                result.setPixel(destX + x, destY + y, pixel)
                            }
                        }
                    }

                    tile.recycle()
                    enhancedTile.recycle()

                    processedTiles++
                    val progress = 0.15f + (processedTiles.toFloat() / totalTiles) * 0.70f
                    onProgress(progress)

                    System.gc()
                }
            }

            if (scaledInput != input) {
                scaledInput.recycle()
            }

            onProgress(0.90f)

            val finalResult = resizeToOriginalAspect(result, input.width, input.height)
            if (finalResult != result) {
                result.recycle()
            }

            onProgress(0.95f)

            val improvedResult = applyEnhancementBoost(finalResult)
            if (improvedResult != finalResult) {
                finalResult.recycle()
            }

            onProgress(1.0f)
            Log.d(TAG, "Enhancement complete")

            improvedResult
        } catch (e: Exception) {
            Log.e(TAG, "Enhancement failed: ${e.message}", e)
            throw e
        }
    }

    private fun resizeForProcessing(bitmap: Bitmap): Bitmap {
        val maxDimension = 384
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
