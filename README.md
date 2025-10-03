myaipiceditor

## Recent Fixes

### ONNX Model Loading Crash Fix (LaMa Model)

**Problem:** The app was crashing with `SIGSEGV` when loading the `lama.onnx` model during initialization. NNAPI was only partially supporting LaMa nodes, causing native crashes in `libonnxruntime.so`.

**Solution Implemented:**

1. **Lazy Loading**: LaMa model is no longer loaded at app startup. It's loaded on-demand when the user first accesses the object removal feature.

2. **Conservative Session Options for LaMa**:
   - CPU execution only (NNAPI disabled)
   - Low thread counts (2 intra-op, 1 inter-op)
   - Basic optimization level only
   - Sequential execution mode
   - Memory pattern and CPU memory arena disabled

3. **Sequential Model Loading**: Models are loaded one at a time with mutex protection to prevent resource contention.

4. **Robust Error Handling**: All session creation wrapped in try/catch with automatic fallback to ultra-conservative settings if primary loading fails.

5. **Proper Threading**: All model loading happens on `Dispatchers.IO` to avoid blocking the main thread or binder threads.

**Changes Made:**
- `AiModelManager.kt`: Complete rewrite with lazy loading, fallback mechanisms, and LaMa-specific handling
- `ObjectRemoval.kt`: Made `removeObject()` a suspend function
- `FaceRestoration.kt`: Made `restoreFace()` a suspend function
- `ImageUpscaler.kt`: Made `upscale()` a suspend function

**Result:** The app should now initialize quickly without crashes, and the LaMa model will load safely when needed.
