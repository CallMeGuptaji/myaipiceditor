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
        val inputBuffer = preprocess(resized)
        val shape = longArrayOf(1, 3, MODEL_INPUT_SIZE.toLong(), MODEL_INPUT_SIZE.toLong())
        val inputTensor = OnnxTensor.createTensor(env, inputBuffer, shape)

        val outputs = session.run(mapOf("input" to inputTensor))
        val output = outputs[0].value as Array<Array<Array<FloatArray>>>

        val mask = postprocess(output, resized.width, resized.height)
        inputTensor.close()
        outputs.close()

        Bitmap.createScaledBitmap(mask, bitmap.width, bitmap.height, true)
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
}
