package com.dlab.myaipiceditor.ai

import android.content.Context
import ai.onnxruntime.*

object OnnxModelLoader {
    private val env: OrtEnvironment by lazy { OrtEnvironment.getEnvironment() }

    fun loadModel(context: Context, modelName: String): OrtSession {
        val modelBytes = context.assets.open("models/$modelName").use { it.readBytes() }
        return env.createSession(modelBytes)
    }
}
