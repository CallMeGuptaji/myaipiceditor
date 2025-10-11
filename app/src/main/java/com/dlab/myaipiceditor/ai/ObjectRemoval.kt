package com.dlab.myaipiceditor.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.Log
import ai.onnxruntime.OnnxTensor
import java.nio.FloatBuffer
import kotlin.math.pow
import kotlin.math.max
import kotlin.math.min
import kotlin.math.abs

object ObjectRemoval {
    private const val TAG = "ObjectRemoval"
    private const val MODEL_INPUT_SIZE = 512

    suspend fun removeObject(context: Context, input: Bitmap, mask: Bitmap): Bitmap {
        Log.d(TAG, "Starting object removal - Input: ${input.width}x${input.height}")

        // Declare all disposable resources outside the try block so they can be cleaned up
        var imageTensor: OnnxTensor? = null
        var maskTensor: OnnxTensor? = null
        var outputs: ai.onnxruntime.OrtSession.Result? = null
        var resizedImage: Bitmap? = null
        var resizedMask: Bitmap? = null
        var outputBitmap: Bitmap? = null

        try {
            val session = AiModelManager.getSession(AiModelManager.ModelType.OBJECT_REMOVAL)
            val ortEnvironment = AiModelManager.getEnvironment()

            val originalWidth = input.width
            val originalHeight = input.height

            // 1. Preprocessing and Tensor Creation
            resizedImage = resizeBitmap(input, MODEL_INPUT_SIZE, MODEL_INPUT_SIZE)
            resizedMask = resizeBitmap(mask, MODEL_INPUT_SIZE, MODEL_INPUT_SIZE)

            Log.d(TAG, "Preprocessing - Resized to ${MODEL_INPUT_SIZE}x${MODEL_INPUT_SIZE}")

            val imageBuffer = preprocessImage(resizedImage)
            val maskBuffer = preprocessMask(resizedMask)

            // NHWC tensor shape [1, H, W, C]
            val imageShape = longArrayOf(1, MODEL_INPUT_SIZE.toLong(), MODEL_INPUT_SIZE.toLong(), 3)
            // NHWC tensor shape [1, H, W, 1]
            val maskShape = longArrayOf(1, MODEL_INPUT_SIZE.toLong(), MODEL_INPUT_SIZE.toLong(), 1)

            imageTensor = OnnxTensor.createTensor(ortEnvironment, imageBuffer, imageShape)
            maskTensor = OnnxTensor.createTensor(ortEnvironment, maskBuffer, maskShape)

            // Log tensor info
            Log.d(TAG, "Image tensor shape: ${imageTensor.info.shape.contentToString()}")
            Log.d(TAG, "Mask tensor shape: ${maskTensor.info.shape.contentToString()}")

            val inputs = mapOf(
                "image" to imageTensor,
                "mask" to maskTensor
            )

            // 2. Inference
            Log.d(TAG, "Running inference...")
            outputs = session.run(inputs)

            // Log output info
            Log.d(TAG, "Number of outputs: ${outputs.size()}")
            outputs.forEach { output ->
                Log.d(TAG, "Output name: ${output.key}, type: ${output.value.javaClass.simpleName}")
                if (output.value is OnnxTensor) {
                    val tensor = output.value as OnnxTensor
                    Log.d(TAG, "  Shape: ${tensor.info.shape.contentToString()}")
                    Log.d(TAG, "  Type: ${tensor.info.type}")
                }
            }

            // Output tensor: [1, H, W, 3] NHWC
            @Suppress("UNCHECKED_CAST")
            val outputTensor = outputs[0].value as Array<Array<Array<FloatArray>>>

            // Analyze output statistics
            analyzeOutputStats(outputTensor)

            // 3. Postprocessing - try to auto-detect the range
            outputBitmap = postprocessOutput(outputTensor, MODEL_INPUT_SIZE, MODEL_INPUT_SIZE)

            // Resize back to original size
            val finalInpaintedResult = resizeBitmap(outputBitmap, originalWidth, originalHeight)

            // 4. Blend the inpainted result with the original image using the mask
            val blendedResult = blendWithOriginal(input, finalInpaintedResult, mask)

            Log.d(TAG, "Object removal completed successfully")

            // finalInpaintedResult is an intermediate step created from outputBitmap, so we recycle it
            finalInpaintedResult.recycle()

            return blendedResult

        } catch (e: Exception) {
            Log.e(TAG, "Error during object removal", e)
            throw e
        } finally {
            imageTensor?.close()
            maskTensor?.close()
            outputs?.close()
            resizedImage?.recycle()
            resizedMask?.recycle()
            outputBitmap?.recycle()
        }
    }

    private fun analyzeOutputStats(output: Array<Array<Array<FloatArray>>>) {
        var minR = Float.MAX_VALUE
        var maxR = Float.MIN_VALUE
        var minG = Float.MAX_VALUE
        var maxG = Float.MIN_VALUE
        var minB = Float.MAX_VALUE
        var maxB = Float.MIN_VALUE
        var sumR = 0.0
        var sumG = 0.0
        var sumB = 0.0
        var count = 0

        for (y in output[0].indices) {
            for (x in output[0][y].indices) {
                val r = output[0][y][x][0]
                val g = output[0][y][x][1]
                val b = output[0][y][x][2]

                minR = min(minR, r)
                maxR = max(maxR, r)
                minG = min(minG, g)
                maxG = max(maxG, g)
                minB = min(minB, b)
                maxB = max(maxB, b)

                sumR += r
                sumG += g
                sumB += b
                count++
            }
        }

        val avgR = sumR / count
        val avgG = sumG / count
        val avgB = sumB / count

        Log.d(TAG, "=== OUTPUT STATISTICS ===")
        Log.d(TAG, "R channel - Min: $minR, Max: $maxR, Avg: $avgR")
        Log.d(TAG, "G channel - Min: $minG, Max: $maxG, Avg: $avgG")
        Log.d(TAG, "B channel - Min: $minB, Max: $maxB, Avg: $avgB")

        // Detect likely range
        val overallMin = min(minR, min(minG, minB))
        val overallMax = max(maxR, max(maxG, maxB))

        Log.d(TAG, "Overall range: [$overallMin, $overallMax]")

        when {
            overallMax <= 1.1 && overallMin >= -0.1 -> Log.d(TAG, "Detected range: [0, 1]")
            overallMax <= 1.1 && overallMin >= -1.1 -> Log.d(TAG, "Detected range: [-1, 1]")
            overallMax <= 255.5 && overallMin >= -0.5 -> Log.d(TAG, "Detected range: [0, 255]")
            else -> Log.d(TAG, "Unknown range detected!")
        }
    }

    private fun resizeBitmap(bitmap: Bitmap, width: Int, height: Int): Bitmap {
        return Bitmap.createScaledBitmap(bitmap, width, height, true)
    }

    private fun preprocessImage(bitmap: Bitmap): FloatBuffer {
        val width = MODEL_INPUT_SIZE
        val height = MODEL_INPUT_SIZE
        val buffer = FloatBuffer.allocate(width * height * 3)
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        // Calculate statistics of input image
        var sumR = 0.0
        var sumG = 0.0
        var sumB = 0.0

        // Try simple [0, 1] normalization
        for (y in 0 until height) {
            for (x in 0 until width) {
                val pixel = pixels[y * width + x]

                val r = Color.red(pixel) / 255f
                val g = Color.green(pixel) / 255f
                val b = Color.blue(pixel) / 255f

                sumR += r
                sumG += g
                sumB += b

                buffer.put(r)
                buffer.put(g)
                buffer.put(b)
            }
        }

        val count = width * height
        Log.d(TAG, "Input image avg: R=${sumR/count}, G=${sumG/count}, B=${sumB/count}")

        buffer.rewind()

        // Log sample values from different positions
        Log.d(TAG, "Input sample [0,0]: R=${buffer.get(0)}, G=${buffer.get(1)}, B=${buffer.get(2)}")
        Log.d(TAG, "Input sample [center]: R=${buffer.get((count/2)*3)}, G=${buffer.get((count/2)*3+1)}, B=${buffer.get((count/2)*3+2)}")

        return buffer
    }

    private fun preprocessMask(mask: Bitmap): FloatBuffer {
        val blurredMask = blurMask(mask, radius = 12f)

        val buffer = FloatBuffer.allocate(MODEL_INPUT_SIZE * MODEL_INPUT_SIZE)
        val pixels = IntArray(MODEL_INPUT_SIZE * MODEL_INPUT_SIZE)
        blurredMask.getPixels(pixels, 0, MODEL_INPUT_SIZE, 0, 0, MODEL_INPUT_SIZE, MODEL_INPUT_SIZE)

        var maskSum = 0.0
        var maskMin = 1f
        var maskMax = 0f

        for (pixel in pixels) {
            val gray = Color.red(pixel) / 255f
            buffer.put(gray.coerceIn(0f, 1f))
            maskSum += gray
            maskMin = min(maskMin, gray)
            maskMax = max(maskMax, gray)
        }

        val maskAvg = maskSum / pixels.size
        Log.d(TAG, "Mask stats - Min: $maskMin, Max: $maskMax, Avg: $maskAvg")

        buffer.rewind()
        Log.d(TAG, "Mask sample [0]: ${buffer.get(0)}, [center]: ${buffer.get(pixels.size/2)}")

        blurredMask.recycle()
        return buffer
    }

    private fun blurMask(mask: Bitmap, radius: Float): Bitmap {
        val tempMask = mask.copy(Bitmap.Config.ALPHA_8, true)
        val blurred = Bitmap.createBitmap(mask.width, mask.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(blurred)

        canvas.drawColor(Color.BLACK)

        val paint = Paint().apply {
            isAntiAlias = true
            color = Color.WHITE
            maskFilter = android.graphics.BlurMaskFilter(radius, android.graphics.BlurMaskFilter.Blur.NORMAL)
        }

        canvas.drawBitmap(tempMask, 0f, 0f, paint)

        tempMask.recycle()
        return blurred
    }

    private fun postprocessOutput(
        output: Array<Array<Array<FloatArray>>>,
        width: Int,
        height: Int
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(width * height)

        // Detect the output range and apply appropriate scaling
        var minVal = Float.MAX_VALUE
        var maxVal = Float.MIN_VALUE

        for (y in 0 until height) {
            for (x in 0 until width) {
                minVal = min(minVal, min(output[0][y][x][0], min(output[0][y][x][1], output[0][y][x][2])))
                maxVal = max(maxVal, max(output[0][y][x][0], max(output[0][y][x][1], output[0][y][x][2])))
            }
        }

        Log.d(TAG, "Output value range: [$minVal, $maxVal]")

        // Auto-scale based on detected range
        val scale: Float
        val offset: Float

        when {
            maxVal <= 1.1 && minVal >= -0.1 -> {
                // [0, 1] range
                Log.d(TAG, "Using [0, 1] -> [0, 255] scaling")
                scale = 255f
                offset = 0f
            }
            maxVal <= 1.1 && minVal >= -1.1 -> {
                // [-1, 1] range
                Log.d(TAG, "Using [-1, 1] -> [0, 255] scaling")
                scale = 127.5f
                offset = 127.5f
            }
            else -> {
                // Unknown range - try to normalize
                Log.w(TAG, "Unknown range! Attempting normalization from [$minVal, $maxVal]")
                val range = maxVal - minVal
                scale = if (range > 0) 255f / range else 255f
                offset = -minVal * scale
            }
        }

        for (y in 0 until height) {
            for (x in 0 until width) {
                val r = (output[0][y][x][0] * scale + offset).coerceIn(0f, 255f).toInt()
                val g = (output[0][y][x][1] * scale + offset).coerceIn(0f, 255f).toInt()
                val b = (output[0][y][x][2] * scale + offset).coerceIn(0f, 255f).toInt()

                pixels[y * width + x] = Color.rgb(r, g, b)
            }
        }

        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        return bitmap
    }

    private fun blendWithOriginal(original: Bitmap, inpainted: Bitmap, mask: Bitmap): Bitmap {
        val width = original.width
        val height = original.height
        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

        val maskPixels = IntArray(width * height)
        mask.getPixels(maskPixels, 0, width, 0, 0, width, height)

        val inpaintedPixels = IntArray(width * height)
        inpainted.getPixels(inpaintedPixels, 0, width, 0, 0, width, height)

        val originalPixels = IntArray(width * height)
        original.getPixels(originalPixels, 0, width, 0, 0, width, height)

        val resultPixels = IntArray(width * height)

        var blendedCount = 0
        var originalCount = 0

        for (y in 0 until height) {
            for (x in 0 until width) {
                val idx = y * width + x

                val maskValue = Color.red(maskPixels[idx]) / 255f

                if (maskValue > 0.01f) {
                    val blendWeight = maskValue.pow(1.5f)

                    val inpaintedPixel = inpaintedPixels[idx]
                    val originalPixel = originalPixels[idx]

                    val r = (Color.red(inpaintedPixel) * blendWeight + Color.red(originalPixel) * (1f - blendWeight)).toInt()
                    val g = (Color.green(inpaintedPixel) * blendWeight + Color.green(originalPixel) * (1f - blendWeight)).toInt()
                    val b = (Color.blue(inpaintedPixel) * blendWeight + Color.blue(originalPixel) * (1f - blendWeight)).toInt()

                    resultPixels[idx] = Color.rgb(
                        r.coerceIn(0, 255),
                        g.coerceIn(0, 255),
                        b.coerceIn(0, 255)
                    )
                    blendedCount++
                } else {
                    resultPixels[idx] = originalPixels[idx]
                    originalCount++
                }
            }
        }

        Log.d(TAG, "Blending stats - Blended pixels: $blendedCount, Original pixels: $originalCount")

        result.setPixels(resultPixels, 0, width, 0, 0, width, height)
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