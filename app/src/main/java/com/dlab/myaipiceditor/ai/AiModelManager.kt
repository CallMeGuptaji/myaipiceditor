package com.dlab.myaipiceditor.ai

import android.content.Context
import android.util.Log
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

object AiModelManager {
    private const val TAG = "AiModelManager"
    private const val MODELS_CACHE_DIR = "onnx_models"

    private lateinit var ortEnvironment: OrtEnvironment
    private val modelSessions = mutableMapOf<ModelType, OrtSession>()
    private var isInitialized = false

    enum class ModelType(val fileName: String) {
        FACE_RESTORATION("GFPGANv1.4.onnx"),
        OBJECT_REMOVAL("lama.onnx"),
        IMAGE_UPSCALER("Real_ESRGAN_x4plus.onnx")
    }

    suspend fun initialize(context: Context) = withContext(Dispatchers.IO) {
        if (isInitialized) {
            Log.d(TAG, "AiModelManager already initialized")
            return@withContext
        }

        try {
            Log.d(TAG, "Initializing AiModelManager...")
            ortEnvironment = OrtEnvironment.getEnvironment()

            val cacheDir = File(context.filesDir, MODELS_CACHE_DIR)
            if (!cacheDir.exists()) {
                cacheDir.mkdirs()
            }

            ModelType.values().forEach { modelType ->
                loadModel(context, modelType, cacheDir)
            }

            isInitialized = true
            Log.d(TAG, "AiModelManager initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing AiModelManager", e)
            throw e
        }
    }

    private fun loadModel(context: Context, modelType: ModelType, cacheDir: File) {
        try {
            Log.d(TAG, "Loading model: ${modelType.fileName}")

            val cachedModelFile = File(cacheDir, modelType.fileName)

            if (!cachedModelFile.exists()) {
                Log.d(TAG, "Copying ${modelType.fileName} to cache...")
                copyModelFromAssets(context, modelType.fileName, cachedModelFile)
            } else {
                Log.d(TAG, "${modelType.fileName} already cached")
            }

            val sessionOptions = OrtSession.SessionOptions().apply {
                setIntraOpNumThreads(4)
                setInterOpNumThreads(4)
                setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)

                if (modelType == ModelType.OBJECT_REMOVAL) {
                    Log.d(TAG, "Using CPU execution provider for ${modelType.fileName} (NNAPI incompatible)")
                } else {
                    try {
                        addNnapi()
                        Log.d(TAG, "NNAPI execution provider enabled for ${modelType.fileName}")
                    } catch (e: Exception) {
                        Log.w(TAG, "NNAPI not available, using CPU for ${modelType.fileName}")
                    }
                }
            }

            val session = ortEnvironment.createSession(
                cachedModelFile.absolutePath,
                sessionOptions
            )

            modelSessions[modelType] = session
            Log.d(TAG, "Model ${modelType.fileName} loaded successfully")

        } catch (e: Exception) {
            Log.e(TAG, "Failed to load model ${modelType.fileName}: ${e.message}", e)
            throw e
        }
    }

    private fun copyModelFromAssets(context: Context, fileName: String, destination: File) {
        context.assets.open("models/$fileName").use { input ->
            FileOutputStream(destination).use { output ->
                input.copyTo(output)
            }
        }
    }

    fun getSession(modelType: ModelType): OrtSession {
        if (!isInitialized) {
            throw IllegalStateException("AiModelManager not initialized. Call initialize() first.")
        }
        return modelSessions[modelType]
            ?: throw IllegalStateException("Model ${modelType.fileName} not loaded")
    }

    fun getEnvironment(): OrtEnvironment {
        if (!isInitialized) {
            throw IllegalStateException("AiModelManager not initialized. Call initialize() first.")
        }
        return ortEnvironment
    }

    fun isModelLoaded(modelType: ModelType): Boolean {
        return modelSessions.containsKey(modelType)
    }

    fun cleanup() {
        Log.d(TAG, "Cleaning up AiModelManager...")
        modelSessions.values.forEach { session ->
            try {
                session.close()
            } catch (e: Exception) {
                Log.e(TAG, "Error closing session", e)
            }
        }
        modelSessions.clear()
        isInitialized = false
    }
}
