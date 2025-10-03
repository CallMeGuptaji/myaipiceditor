package com.dlab.myaipiceditor.ai

import android.graphics.Bitmap

object FaceRestoration {
    fun restoreFace(input: Bitmap): Bitmap {
        val session = AiModelManager.getSession(AiModelManager.ModelType.FACE_RESTORATION)
        val ortEnvironment = AiModelManager.getEnvironment()

        // TODO: Preprocess → Run inference → Postprocess
        return input
    }
}
