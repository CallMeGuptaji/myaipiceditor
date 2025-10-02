package com.dlab.myaipiceditor.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.FloatBuffer

object BackgroundRemoval {
    private const val MODEL_NAME = "u2net.onnx"
    private const val INPUT_SIZE = 320
    private const val PREVIEW_SIZE = 512

    suspend fun removeBackgroundAsync(context: Context, input: Bitmap, threshold: Float = 0.5f, usePreview: Boolean = true): Bitmap =
        withContext(Dispatchers.Default) {
            removeBackground(context, input, threshold, usePreview)
        }

    private fun removeBackground(context: Context, input: Bitmap, threshold: Float = 0.5f, usePreview: Boolean = true): Bitmap {
        val workingImage = if (usePreview && (input.width > PREVIEW_SIZE || input.height > PREVIEW_SIZE)) {
            val scale = PREVIEW_SIZE.toFloat() / maxOf(input.width, input.height)
            Bitmap.createScaledBitmap(
                input,
                (input.width * scale).toInt(),
                (input.height * scale).toInt(),
                true
            )
        } else {
            input
        }

        val session = OnnxModelLoader.loadModel(context, MODEL_NAME)
        val env = OrtEnvironment.getEnvironment()

        val preprocessed = preprocessBitmap(workingImage)
        val inputTensor = OnnxTensor.createTensor(env, preprocessed)

        val output = session.run(mapOf(session.inputNames.first() to inputTensor))
        val outputTensor = output[0].value as Array<Array<Array<FloatArray>>>

        inputTensor.close()
        output.close()

        val mask = postprocessMask(outputTensor, input.width, input.height, threshold)

        return applyMaskToImage(input, mask)
    }

    fun applyBackgroundColor(bitmap: Bitmap, mask: Bitmap, backgroundColor: Int): Bitmap {
        val result = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)

        for (y in 0 until bitmap.height) {
            for (x in 0 until bitmap.width) {
                val maskPixel = mask.getPixel(x, y)
                val alpha = Color.alpha(maskPixel)

                if (alpha > 128) {
                    result.setPixel(x, y, bitmap.getPixel(x, y))
                } else {
                    result.setPixel(x, y, backgroundColor)
                }
            }
        }

        return result
    }

    fun applyBackgroundImage(foreground: Bitmap, mask: Bitmap, background: Bitmap): Bitmap {
        val scaledBackground = Bitmap.createScaledBitmap(background, foreground.width, foreground.height, true)
        val result = Bitmap.createBitmap(foreground.width, foreground.height, Bitmap.Config.ARGB_8888)

        for (y in 0 until foreground.height) {
            for (x in 0 until foreground.width) {
                val maskPixel = mask.getPixel(x, y)
                val alpha = Color.alpha(maskPixel).toFloat() / 255f

                val fgPixel = foreground.getPixel(x, y)
                val bgPixel = scaledBackground.getPixel(x, y)

                val r = (Color.red(fgPixel) * alpha + Color.red(bgPixel) * (1 - alpha)).toInt()
                val g = (Color.green(fgPixel) * alpha + Color.green(bgPixel) * (1 - alpha)).toInt()
                val b = (Color.blue(fgPixel) * alpha + Color.blue(bgPixel) * (1 - alpha)).toInt()

                result.setPixel(x, y, Color.rgb(r, g, b))
            }
        }

        return result
    }

    fun refineMaskWithBrush(mask: Bitmap, x: Float, y: Float, brushSize: Float, isErasing: Boolean): Bitmap {
        val mutableMask = mask.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = android.graphics.Canvas(mutableMask)
        val paint = android.graphics.Paint().apply {
            isAntiAlias = true
            color = if (isErasing) Color.BLACK else Color.WHITE
            strokeWidth = brushSize
            style = android.graphics.Paint.Style.FILL
        }

        canvas.drawCircle(x, y, brushSize / 2, paint)
        return mutableMask
    }

    private fun preprocessBitmap(bitmap: Bitmap): Array<Array<Array<FloatArray>>> {
        val resized = Bitmap.createScaledBitmap(bitmap, INPUT_SIZE, INPUT_SIZE, true)
        val input = Array(1) { Array(3) { Array(INPUT_SIZE) { FloatArray(INPUT_SIZE) } } }

        for (y in 0 until INPUT_SIZE) {
            for (x in 0 until INPUT_SIZE) {
                val pixel = resized.getPixel(x, y)
                input[0][0][y][x] = (Color.red(pixel) / 255f)
                input[0][1][y][x] = (Color.green(pixel) / 255f)
                input[0][2][y][x] = (Color.blue(pixel) / 255f)
            }
        }

        return input
    }

    private fun postprocessMask(
        output: Array<Array<Array<FloatArray>>>,
        targetWidth: Int,
        targetHeight: Int,
        threshold: Float
    ): Bitmap {
        val outputSize = output[0][0].size
        val tempMask = Bitmap.createBitmap(outputSize, outputSize, Bitmap.Config.ARGB_8888)

        var minVal = Float.MAX_VALUE
        var maxVal = Float.MIN_VALUE

        for (y in 0 until outputSize) {
            for (x in 0 until outputSize) {
                val value = output[0][0][y][x]
                minVal = minOf(minVal, value)
                maxVal = maxOf(maxVal, value)
            }
        }

        val range = maxVal - minVal

        for (y in 0 until outputSize) {
            for (x in 0 until outputSize) {
                val normalized = (output[0][0][y][x] - minVal) / range
                val alpha = if (normalized > threshold) 255 else 0
                tempMask.setPixel(x, y, Color.argb(alpha, 255, 255, 255))
            }
        }

        return Bitmap.createScaledBitmap(tempMask, targetWidth, targetHeight, true)
    }

    private fun applyMaskToImage(image: Bitmap, mask: Bitmap): Bitmap {
        val result = Bitmap.createBitmap(image.width, image.height, Bitmap.Config.ARGB_8888)

        for (y in 0 until image.height) {
            for (x in 0 until image.width) {
                val maskPixel = mask.getPixel(x, y)
                val alpha = Color.alpha(maskPixel)

                if (alpha > 0) {
                    val pixel = image.getPixel(x, y)
                    result.setPixel(x, y, Color.argb(alpha, Color.red(pixel), Color.green(pixel), Color.blue(pixel)))
                }
            }
        }

        return result
    }
}
