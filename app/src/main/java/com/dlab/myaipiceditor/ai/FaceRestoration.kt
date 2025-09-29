package com.dlab.myaipiceditor.ai

import android.content.Context
import android.graphics.Bitmap

object FaceRestoration {
    private const val MODEL_NAME = "GFPGANv1.4.onnx"

    fun restoreFace(context: Context, input: Bitmap): Bitmap {
        val session = OnnxModelLoader.loadModel(context, MODEL_NAME)

        // TODO: Preprocess → Run inference → Postprocess
        return input // placeholder
    }
}
