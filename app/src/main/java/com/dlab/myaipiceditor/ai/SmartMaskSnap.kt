package com.dlab.myaipiceditor.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import ai.onnxruntime.OnnxTensor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.FloatBuffer

object SmartMaskSnap {
    private const val MODEL_INPUT_SIZE = 320

    suspend fun snapToObject(context: Context, bitmap: Bitmap, roughMask: Bitmap): Bitmap = withContext(Dispatchers.Default) {
        val session = AiModelManager.getSession(AiModelManager.ModelType.FOR_SEGMENTATION)
        val env = AiModelManager.getEnvironment()

        val resized = Bitmap.createScaledBitmap(bitmap, MODEL_INPUT_SIZE, MODEL_INPUT_SIZE, true)
        val resizedRoughMask = Bitmap.createScaledBitmap(roughMask, MODEL_INPUT_SIZE, MODEL_INPUT_SIZE, true)

        val inputBuffer = preprocess(resized)
        val shape = longArrayOf(1, 3, MODEL_INPUT_SIZE.toLong(), MODEL_INPUT_SIZE.toLong())
        val inputTensor = OnnxTensor.createTensor(env, inputBuffer, shape)

        val outputs = session.run(mapOf("input" to inputTensor))
        val output = outputs[0].value as Array<Array<Array<FloatArray>>>

        val segmentationMask = postprocess(output, MODEL_INPUT_SIZE, MODEL_INPUT_SIZE)
        inputTensor.close()
        outputs.close()

        val refinedMask = combineMasks(segmentationMask, resizedRoughMask)

        resized.recycle()
        resizedRoughMask.recycle()
        segmentationMask.recycle()

        Bitmap.createScaledBitmap(refinedMask, bitmap.width, bitmap.height, true)
    }

    private fun preprocess(bitmap: Bitmap): FloatBuffer {
        val buffer = FloatBuffer.allocate(3 * MODEL_INPUT_SIZE * MODEL_INPUT_SIZE)
        val pixels = IntArray(MODEL_INPUT_SIZE * MODEL_INPUT_SIZE)
        bitmap.getPixels(pixels, 0, MODEL_INPUT_SIZE, 0, 0, MODEL_INPUT_SIZE, MODEL_INPUT_SIZE)
        for (c in 0 until 3) {
            for (i in pixels.indices) {
                val pixel = pixels[i]
                val value = when (c) {
                    0 -> Color.red(pixel) / 255f
                    1 -> Color.green(pixel) / 255f
                    else -> Color.blue(pixel) / 255f
                }
                buffer.put(value)
            }
        }
        buffer.rewind()
        return buffer
    }

    private fun postprocess(output: Array<Array<Array<FloatArray>>>, w: Int, h: Int): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        for (y in 0 until h) {
            for (x in 0 until w) {
                val v = (output[0][0][y][x] * 255).toInt().coerceIn(0, 255)
                bmp.setPixel(x, y, Color.argb(255, v, v, v))
            }
        }
        return bmp
    }

    private fun combineMasks(segmentationMask: Bitmap, roughMask: Bitmap): Bitmap {
        val width = segmentationMask.width
        val height = segmentationMask.height
        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

        val segPixels = IntArray(width * height)
        val roughPixels = IntArray(width * height)

        segmentationMask.getPixels(segPixels, 0, width, 0, 0, width, height)
        roughMask.getPixels(roughPixels, 0, width, 0, 0, width, height)

        val roughBounds = getRoughMaskBounds(roughPixels, width, height)
        if (roughBounds == null) {
            return roughMask.copy(Bitmap.Config.ARGB_8888, true)
        }

        val expandedBounds = expandBounds(roughBounds, width, height, 50)

        val resultPixels = IntArray(width * height)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val idx = y * width + x
                val roughValue = Color.red(roughPixels[idx]) / 255f
                val segValue = Color.red(segPixels[idx]) / 255f

                val finalValue = if (x >= expandedBounds[0] && x <= expandedBounds[2] &&
                                    y >= expandedBounds[1] && y <= expandedBounds[3]) {
                    if (roughValue > 0.1f && segValue > 0.3f) {
                        (segValue * 255).toInt()
                    } else {
                        0
                    }
                } else {
                    0
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
                if (value > 25) {
                    hasPixels = true
                    minX = minX.coerceAtMost(x)
                    minY = minY.coerceAtMost(y)
                    maxX = maxX.coerceAtLeast(x)
                    maxY = maxY.coerceAtLeast(y)
                }
            }
        }

        return if (hasPixels) intArrayOf(minX, minY, maxX, maxY) else null
    }

    private fun expandBounds(bounds: IntArray, width: Int, height: Int, expansion: Int): IntArray {
        return intArrayOf(
            (bounds[0] - expansion).coerceAtLeast(0),
            (bounds[1] - expansion).coerceAtLeast(0),
            (bounds[2] + expansion).coerceAtMost(width - 1),
            (bounds[3] + expansion).coerceAtMost(height - 1)
        )
    }
}
