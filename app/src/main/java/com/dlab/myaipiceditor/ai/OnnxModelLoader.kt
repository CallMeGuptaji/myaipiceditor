package com.dlab.myaipiceditor.ai

import android.content.Context
import ai.onnxruntime.*

object OnnxModelLoader {
    private val env: OrtEnvironment by lazy { OrtEnvironment.getEnvironment() }
    private val sessionCache = mutableMapOf<String, OrtSession>()

    fun loadModel(context: Context, modelName: String): OrtSession {
        return sessionCache.getOrPut(modelName) {
            val modelBytes = context.assets.open("models/$modelName").use { it.readBytes() }
            env.createSession(modelBytes)
        }
    }

    fun clearCache() {
        sessionCache.values.forEach { it.close() }
        sessionCache.clear()
    }
}
