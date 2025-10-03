package com.dlab.myaipiceditor.ai

import android.content.Context
import android.graphics.Bitmap

object FaceRestoration {
    fun restoreFace(context: Context, input: Bitmap): Bitmap {
        val session = AiModelManager.getSession(AiModelManager.ModelType.FACE_RESTORATION)
        val ortEnvironment = AiModelManager.getEnvironment()

        return input
    }
}
