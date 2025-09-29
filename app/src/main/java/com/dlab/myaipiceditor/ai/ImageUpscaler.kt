package com.dlab.myaipiceditor.ai

import android.content.Context
import android.graphics.Bitmap

object ImageUpscaler {
    private const val MODEL_NAME = "Real-ESRGAN-x4plus.onnx"

    fun upscale(context: Context, input: Bitmap): Bitmap {
        val session = OnnxModelLoader.loadModel(context, MODEL_NAME)

        // TODO: Preprocess → Run inference → Postprocess
        return input // placeholder
    }
}
