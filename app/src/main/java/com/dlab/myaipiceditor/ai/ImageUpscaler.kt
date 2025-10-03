package com.dlab.myaipiceditor.ai

import android.content.Context
import android.graphics.Bitmap

object ImageUpscaler {
    suspend fun upscale(context: Context, input: Bitmap): Bitmap {
        val session = AiModelManager.getSession(AiModelManager.ModelType.IMAGE_UPSCALER)
        val ortEnvironment = AiModelManager.getEnvironment()

        return input
    }
}
