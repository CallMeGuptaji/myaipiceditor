package com.dlab.myaipiceditor.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.Log
import ai.onnxruntime.OnnxTensor
import java.nio.FloatBuffer

object ObjectRemoval {
    private const val TAG = "ObjectRemoval"
    private const val MODEL_INPUT_SIZE = 512

    suspend fun removeObject(context: Context, input: Bitmap, mask: Bitmap): Bitmap {
        Log.d(TAG, "Starting object removal - Input: ${input.width}x${input.height}")

        try {
            val session = AiModelManager.getSession(AiModelManager.ModelType.OBJECT_REMOVAL)
            val ortEnvironment = AiModelManager.getEnvironment()

            val originalWidth = input.width
            val originalHeight = input.height

            val resizedImage = resizeBitmap(input, MODEL_INPUT_SIZE, MODEL_INPUT_SIZE)
            val resizedMask = resizeBitmap(mask, MODEL_INPUT_SIZE, MODEL_INPUT_SIZE)

            Log.d(TAG, "Preprocessing - Resized to ${MODEL_INPUT_SIZE}x${MODEL_INPUT_SIZE}")

            val imageBuffer = preprocessImage(resizedImage)
            val maskBuffer = preprocessMask(resizedMask)

            val imageShape = longArrayOf(1, 3, MODEL_INPUT_SIZE.toLong(), MODEL_INPUT_SIZE.toLong())
            val maskShape = longArrayOf(1, 1, MODEL_INPUT_SIZE.toLong(), MODEL_INPUT_SIZE.toLong())

            val imageTensor = OnnxTensor.createTensor(ortEnvironment, imageBuffer, imageShape)
            val maskTensor = OnnxTensor.createTensor(ortEnvironment, maskBuffer, maskShape)

            val inputs = mapOf(
                "l_image_" to imageTensor,
                "l_mask_" to maskTensor
            )

            Log.d(TAG, "Running inference...")
            val outputs = session.run(inputs)

            val outputTensor = outputs[0].value as Array<Array<Array<FloatArray>>>
            val outputBitmap = postprocessOutput(outputTensor, MODEL_INPUT_SIZE, MODEL_INPUT_SIZE)

            imageTensor.close()
            maskTensor.close()
            outputs.close()

            val finalResult = resizeBitmap(outputBitmap, originalWidth, originalHeight)

            val blendedResult = blendWithOriginal(input, finalResult, mask)

            Log.d(TAG, "Object removal completed successfully")
            return blendedResult

        } catch (e: Exception) {
            Log.e(TAG, "Error during object removal", e)
            throw e
        }
    }

    private fun resizeBitmap(bitmap: Bitmap, width: Int, height: Int): Bitmap {
        return Bitmap.createScaledBitmap(bitmap, width, height, true)
    }

    private fun preprocessImage(bitmap: Bitmap): FloatBuffer {
        val buffer = FloatBuffer.allocate(3 * MODEL_INPUT_SIZE * MODEL_INPUT_SIZE)
        val pixels = IntArray(MODEL_INPUT_SIZE * MODEL_INPUT_SIZE)
        bitmap.getPixels(pixels, 0, MODEL_INPUT_SIZE, 0, 0, MODEL_INPUT_SIZE, MODEL_INPUT_SIZE)

        val mean = floatArrayOf(0.485f, 0.456f, 0.406f)
        val std = floatArrayOf(0.229f, 0.224f, 0.225f)

        for (c in 0 until 3) {
            for (i in pixels.indices) {
                val pixel = pixels[i]
                val value = when (c) {
                    0 -> Color.red(pixel) / 255f
                    1 -> Color.green(pixel) / 255f
                    else -> Color.blue(pixel) / 255f
                }
                buffer.put((value - mean[c]) / std[c])
            }
        }

        buffer.rewind()
        return buffer
    }

    private fun preprocessMask(mask: Bitmap): FloatBuffer {
        val buffer = FloatBuffer.allocate(MODEL_INPUT_SIZE * MODEL_INPUT_SIZE)
        val pixels = IntArray(MODEL_INPUT_SIZE * MODEL_INPUT_SIZE)
        mask.getPixels(pixels, 0, MODEL_INPUT_SIZE, 0, 0, MODEL_INPUT_SIZE, MODEL_INPUT_SIZE)

        for (pixel in pixels) {
            val gray = Color.red(pixel) / 255f
            buffer.put(if (gray > 0.5f) 1f else 0f)
        }

        buffer.rewind()
        return buffer
    }

    private fun postprocessOutput(
        output: Array<Array<Array<FloatArray>>>,
        width: Int,
        height: Int
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(width * height)

        val mean = floatArrayOf(0.485f, 0.456f, 0.406f)
        val std = floatArrayOf(0.229f, 0.224f, 0.225f)

        for (y in 0 until height) {
            for (x in 0 until width) {
                val r = ((output[0][0][y][x] * std[0] + mean[0]) * 255f).coerceIn(0f, 255f).toInt()
                val g = ((output[0][1][y][x] * std[1] + mean[1]) * 255f).coerceIn(0f, 255f).toInt()
                val b = ((output[0][2][y][x] * std[2] + mean[2]) * 255f).coerceIn(0f, 255f).toInt()

                pixels[y * width + x] = Color.rgb(r, g, b)
            }
        }

        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        return bitmap
    }

    private fun blendWithOriginal(original: Bitmap, inpainted: Bitmap, mask: Bitmap): Bitmap {
        val result = Bitmap.createBitmap(original.width, original.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        canvas.drawBitmap(original, 0f, 0f, null)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val maskPixels = IntArray(mask.width * mask.height)
        mask.getPixels(maskPixels, 0, mask.width, 0, 0, mask.width, mask.height)

        val inpaintedPixels = IntArray(inpainted.width * inpainted.height)
        inpainted.getPixels(inpaintedPixels, 0, inpainted.width, 0, 0, inpainted.width, inpainted.height)

        val resultPixels = IntArray(original.width * original.height)
        original.getPixels(resultPixels, 0, original.width, 0, 0, original.width, original.height)

        for (y in 0 until original.height) {
            for (x in 0 until original.width) {
                val maskValue = Color.red(maskPixels[y * mask.width + x]) / 255f

                if (maskValue > 0.1f) {
                    val inpaintedPixel = inpaintedPixels[y * inpainted.width + x]
                    val originalPixel = resultPixels[y * original.width + x]

                    val r = (Color.red(inpaintedPixel) * maskValue + Color.red(originalPixel) * (1f - maskValue)).toInt()
                    val g = (Color.green(inpaintedPixel) * maskValue + Color.green(originalPixel) * (1f - maskValue)).toInt()
                    val b = (Color.blue(inpaintedPixel) * maskValue + Color.blue(originalPixel) * (1f - maskValue)).toInt()

                    resultPixels[y * original.width + x] = Color.rgb(r, g, b)
                }
            }
        }

        result.setPixels(resultPixels, 0, original.width, 0, 0, original.width, original.height)
        return result
    }

    fun createMaskFromStrokes(
        strokes: List<com.dlab.myaipiceditor.data.BrushStroke>,
        width: Int,
        height: Int
    ): Bitmap {
        val mask = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(mask)
        canvas.drawColor(Color.BLACK)

        val paint = Paint().apply {
            color = Color.WHITE
            isAntiAlias = true
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }

        strokes.forEach { stroke ->
            if (stroke.isEraser) {
                paint.color = Color.BLACK
            } else {
                paint.color = Color.WHITE
            }

            paint.strokeWidth = stroke.brushSize * 2

            if (stroke.points.size > 1) {
                val path = android.graphics.Path()
                val firstPoint = stroke.points.first()
                path.moveTo(firstPoint.x * width, firstPoint.y * height)

                for (i in 1 until stroke.points.size) {
                    val point = stroke.points[i]
                    path.lineTo(point.x * width, point.y * height)
                }

                canvas.drawPath(path, paint)
            }
        }

        return mask
    }
}
