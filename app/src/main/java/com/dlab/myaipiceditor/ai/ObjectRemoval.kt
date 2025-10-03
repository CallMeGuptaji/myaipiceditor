package com.dlab.myaipiceditor.ai

import android.graphics.Bitmap

object ObjectRemoval {
    fun removeObject(input: Bitmap, mask: Bitmap): Bitmap {
        val session = AiModelManager.getSession(AiModelManager.ModelType.OBJECT_REMOVAL)
        val ortEnvironment = AiModelManager.getEnvironment()

        // TODO: Preprocess input & mask → Run inference → Postprocess output
        return input
    }
}
