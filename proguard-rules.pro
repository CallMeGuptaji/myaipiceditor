# ============================
# General Settings
# ============================

# Keep line numbers for better crash reports
-keepattributes SourceFile,LineNumberTable

# Keep annotations (important for Jetpack libraries & Compose)
-keepattributes *Annotation*

# Don’t strip generic signatures (needed for Kotlin/Compose reflection)
-keepattributes Signature

# ============================
# Jetpack Compose
# ============================

# Keep all Compose runtime classes
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**

# ============================
# Kotlin Coroutines
# ============================

-dontwarn kotlinx.coroutines.**
-keep class kotlinx.coroutines.** { *; }

# ============================
# Lifecycle + ViewModel
# ============================

-keep class androidx.lifecycle.** { *; }
-dontwarn androidx.lifecycle.**

# ============================
# Navigation
# ============================

-keep class androidx.navigation.** { *; }
-dontwarn androidx.navigation.**

# ============================
# ONNX Runtime
# ============================

# Keep ONNX runtime classes
-keep class ai.onnxruntime.** { *; }
-dontwarn ai.onnxruntime.**

# ============================
# ExifInterface
# ============================

-keep class androidx.exifinterface.** { *; }
-dontwarn androidx.exifinterface.**

# ============================
# Accompanist Permissions
# ============================

-keep class com.google.accompanist.permissions.** { *; }
-dontwarn com.google.accompanist.permissions.**

# ============================
# Optional Debugging
# ============================

# Don’t rename source files (optional, helps with debugging)
-renamesourcefileattribute SourceFile
