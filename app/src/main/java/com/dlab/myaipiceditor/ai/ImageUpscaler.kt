package com.dlab.myaipiceditor.ai

import android.graphics.Bitmap

object ImageUpscaler {
    fun upscale(input: Bitmap): Bitmap {
        val session = AiModelManager.getSession(AiModelManager.ModelType.IMAGE_UPSCALER)
        val ortEnvironment = AiModelManager.getEnvironment()

        // TODO: Preprocess → Run inference → Postprocess
        return input
    }
}
