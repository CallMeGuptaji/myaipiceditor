package com.dlab.myaipiceditor.ai

import android.content.Context
import android.graphics.Bitmap

object ObjectRemoval {
    private const val MODEL_NAME = "lama.onnx"

    fun removeObject(context: Context, input: Bitmap, mask: Bitmap): Bitmap {
        val session = OnnxModelLoader.loadModel(context, MODEL_NAME)

        // TODO: Preprocess input & mask → Run inference → Postprocess output
        return input // placeholder
    }
}
