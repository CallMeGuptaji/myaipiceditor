package com.dlab.myaipiceditor.ai

import android.content.Context
import android.util.Log
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

object AiModelManager {
    private const val TAG = "AiModelManager"
    private const val MODELS_CACHE_DIR = "onnx_models"

    private lateinit var ortEnvironment: OrtEnvironment
    private val modelSessions = mutableMapOf<ModelType, OrtSession>()
    private val loadingMutex = Mutex()
    private var isInitialized = false
    private lateinit var appContext: Context
    private lateinit var cacheDir: File

    enum class ModelType(val fileName: String) {
        FACE_RESTORATION("GFPGANv1.4.onnx"),
        OBJECT_REMOVAL("lama.onnx"),
        IMAGE_UPSCALER("edsr_onnxsim_2x.onnx"),
        FOR_SEGMENTATION("u2net.onnx")
    }

    suspend fun initialize(context: Context) = withContext(Dispatchers.IO) {
        if (isInitialized) {
            Log.d(TAG, "AiModelManager already initialized")
            return@withContext
        }

        try {
            Log.d(TAG, "Initializing AiModelManager...")
            appContext = context.applicationContext
            ortEnvironment = OrtEnvironment.getEnvironment()

            cacheDir = File(appContext.filesDir, MODELS_CACHE_DIR)
            if (!cacheDir.exists()) {
                cacheDir.mkdirs()
            }

            loadModelSequentially(ModelType.FACE_RESTORATION)
            loadModelSequentially(ModelType.FOR_SEGMENTATION)

            isInitialized = true
            Log.d(TAG, "AiModelManager initialized (preloaded: Face Restoration, U2Net Segmentation)")
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing AiModelManager: ${e.message}", e)
            throw e
        }
    }

    private suspend fun loadModelSequentially(modelType: ModelType) = withContext(Dispatchers.IO) {
        loadingMutex.withLock {
            if (modelSessions.containsKey(modelType)) {
                Log.d(TAG, "Model ${modelType.fileName} already loaded")
                return@withContext
            }

            try {
                Log.d(TAG, "Loading model: ${modelType.fileName}")

                val cachedModelFile = File(cacheDir, modelType.fileName)

                if (!cachedModelFile.exists()) {
                    Log.d(TAG, "Copying ${modelType.fileName} to cache...")
                    copyModelFromAssets(appContext, modelType.fileName, cachedModelFile)
                } else {
                    Log.d(TAG, "${modelType.fileName} already cached")
                }

                val session = createSessionWithFallback(modelType, cachedModelFile)

                if (session != null) {
                    modelSessions[modelType] = session
                    Log.d(TAG, "Model ${modelType.fileName} loaded successfully")
                } else {
                    Log.e(TAG, "Failed to create session for ${modelType.fileName}")
                    throw IllegalStateException("Unable to load model ${modelType.fileName}")
                }

            } catch (e: Exception) {
                Log.e(TAG, "Failed to load model ${modelType.fileName}: ${e.message}", e)
                throw e
            }
        }
    }

    private fun createSessionWithFallback(modelType: ModelType, modelFile: File): OrtSession? {
        return try {
            when (modelType) {
                ModelType.OBJECT_REMOVAL -> createLamaSession(modelFile)
                ModelType.FOR_SEGMENTATION -> createU2NetSession(modelFile)
                else -> createStandardSession(modelType, modelFile)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Primary session creation failed for ${modelType.fileName}, trying fallback: ${e.message}", e)
            try {
                createConservativeSession(modelFile)
            } catch (fallbackException: Exception) {
                Log.e(TAG, "Fallback session creation also failed for ${modelType.fileName}: ${fallbackException.message}", fallbackException)
                null
            }
        }
    }

    private fun createLamaSession(modelFile: File): OrtSession {
        Log.d(TAG, "Creating LaMa session with conservative CPU-only settings")

        val sessionOptions = OrtSession.SessionOptions().apply {
            setIntraOpNumThreads(2)
            setInterOpNumThreads(1)
            setOptimizationLevel(OrtSession.SessionOptions.OptLevel.BASIC_OPT)
            setExecutionMode(OrtSession.SessionOptions.ExecutionMode.SEQUENTIAL)
        }

        return ortEnvironment.createSession(modelFile.absolutePath, sessionOptions)
    }

    private fun createU2NetSession(modelFile: File): OrtSession {
        Log.d(TAG, "Creating U2Net session with optimized settings")

        val sessionOptions = OrtSession.SessionOptions().apply {
            setIntraOpNumThreads(3)
            setInterOpNumThreads(2)
            setOptimizationLevel(OrtSession.SessionOptions.OptLevel.BASIC_OPT)
            setExecutionMode(OrtSession.SessionOptions.ExecutionMode.SEQUENTIAL)
        }

        return ortEnvironment.createSession(modelFile.absolutePath, sessionOptions)
    }

    private fun createStandardSession(modelType: ModelType, modelFile: File): OrtSession {
        val sessionOptions = OrtSession.SessionOptions().apply {
            setIntraOpNumThreads(4)
            setInterOpNumThreads(4)
            setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)

            try {
                addNnapi()
                Log.d(TAG, "NNAPI execution provider enabled for ${modelType.fileName}")
            } catch (e: Exception) {
                Log.w(TAG, "NNAPI not available for ${modelType.fileName}, using CPU")
            }
        }

        return ortEnvironment.createSession(modelFile.absolutePath, sessionOptions)
    }

    private fun createConservativeSession(modelFile: File): OrtSession {
        Log.d(TAG, "Creating conservative fallback session")

        val sessionOptions = OrtSession.SessionOptions().apply {
            setIntraOpNumThreads(1)
            setInterOpNumThreads(1)
            setOptimizationLevel(OrtSession.SessionOptions.OptLevel.NO_OPT)
            setExecutionMode(OrtSession.SessionOptions.ExecutionMode.SEQUENTIAL)
        }

        return ortEnvironment.createSession(modelFile.absolutePath, sessionOptions)
    }

    private fun copyModelFromAssets(context: Context, fileName: String, destination: File) {
        context.assets.open("models/$fileName").use { input ->
            FileOutputStream(destination).use { output ->
                input.copyTo(output)
            }
        }
    }

    suspend fun getSession(modelType: ModelType): OrtSession {
        if (!isInitialized) {
            throw IllegalStateException("AiModelManager not initialized. Call initialize() first.")
        }

        if (!modelSessions.containsKey(modelType)) {
            Log.d(TAG, "Lazy loading ${modelType.fileName} on first use...")
            loadModelSequentially(modelType)
        }

        return modelSessions[modelType]
            ?: throw IllegalStateException("Model ${modelType.fileName} could not be loaded")
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
