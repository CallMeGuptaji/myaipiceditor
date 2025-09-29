package com.dlab.myaipiceditor.ai

import android.content.Context
import android.graphics.Bitmap

object BackgroundRemoval {
    private const val MODEL_NAME = "u2net.onnx"

    fun removeBackground(context: Context, input: Bitmap): Bitmap {
        val session = OnnxModelLoader.loadModel(context, MODEL_NAME)

        // TODO: Preprocess input → Run inference → Postprocess output
        // Return final Bitmap with transparent background

        return input // placeholder
    }
}
