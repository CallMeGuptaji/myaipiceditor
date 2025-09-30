package com.dlab.myaipiceditor

import android.Manifest
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dlab.myaipiceditor.data.EditorAction
import com.dlab.myaipiceditor.ui.theme.MyAiPicEditorTheme
import com.dlab.myaipiceditor.ui.CropScreen
import com.dlab.myaipiceditor.ui.TextEditorScreen
import com.dlab.myaipiceditor.ui.TextStylingScreen
import com.dlab.myaipiceditor.viewmodel.EditorViewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {

    private val viewModel: EditorViewModel by viewModels()

    @OptIn(ExperimentalPermissionsApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyAiPicEditorTheme {
                val state by viewModel.state.collectAsStateWithLifecycle()

                // Permission handling
                val permissions = rememberMultiplePermissionsState(
                    permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                        listOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.CAMERA)
                    else
                        listOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.CAMERA)
                )

                // Image picker launcher
                val imagePickerLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.GetContent()
                ) { uri: Uri? ->
                    uri?.let {
                        val bitmap = loadAndProcessBitmapFromUri(it)
                        bitmap?.let { bmp -> viewModel.setImage(bmp) }
                    }
                }

                // Camera launcher
                var photoUri by remember { mutableStateOf<Uri?>(null) }
                val cameraLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.TakePicture()
                ) { success ->
                    if (success && photoUri != null) {
                        val bitmap = loadAndProcessBitmapFromUri(photoUri!!)
                        bitmap?.let { bmp -> viewModel.setImage(bmp) }
                    }
                }

                LaunchedEffect(Unit) {
                    permissions.launchMultiplePermissionRequest()
                }

                // Show first screen if no image is loaded, otherwise show editor
                if (state.currentImage == null) {
                    FirstScreen(
                        onSelectFromGallery = {
                            if (permissions.allPermissionsGranted) {
                                imagePickerLauncher.launch("image/*")
                            } else {
                                permissions.launchMultiplePermissionRequest()
                            }
                        },
                        onTakePhoto = {
                            if (permissions.allPermissionsGranted) {
                                photoUri = createImageUri()
                                photoUri?.let { cameraLauncher.launch(it) }
                            } else {
                                permissions.launchMultiplePermissionRequest()
                            }
                        }
                    )
                } else {
                    EditorScreen(
                        state = state,
                        onActionClick = { action -> viewModel.handleAction(action) },
                        onSelectFromGallery = {
                            if (permissions.allPermissionsGranted) {
                                imagePickerLauncher.launch("image/*")
                            } else {
                                permissions.launchMultiplePermissionRequest()
                            }
                        },
                        onTakePhoto = {
                            if (permissions.allPermissionsGranted) {
                                photoUri = createImageUri()
                                photoUri?.let { cameraLauncher.launch(it) }
                            } else {
                                permissions.launchMultiplePermissionRequest()
                            }
                        },
                        onSaveImage = { saveImageToGallery(state.currentImage!!) },
                        onShareImage = { shareImage(state.currentImage!!) }
                    )
                }

                // Show crop screen when cropping
                if (state.isCropping && state.currentImage != null) {
                    CropScreen(
                        bitmap = state.currentImage!!,
                        onCropConfirm = { cropRect ->
                            viewModel.handleAction(EditorAction.ConfirmCrop(cropRect))
                        },
                        onCancel = {
                            viewModel.handleAction(EditorAction.CancelCrop)
                        }
                    )
                }

                // Show text editor screen when adding text
                if (state.isAddingText) {
                    TextEditorScreen(
                        onTextConfirm = { text ->
                            viewModel.handleAction(EditorAction.ConfirmText(text))
                        },
                        onCancel = {
                            viewModel.handleAction(EditorAction.CancelAddText)
                        }
                    )
                }

                // Show text styling screen when styling text
                if (state.isStylingText) {
                    TextStylingScreen(
                        text = state.currentText,
                        bitmap = state.currentImage,
                        currentStyle = state.currentTextStyle,
                        onStyleChange = { style ->
                            viewModel.handleAction(EditorAction.UpdateTextStyle(style))
                        },
                        onPositionChange = { position ->
                            viewModel.handleAction(EditorAction.UpdateTextPosition(position))
                        },
                        onConfirm = {
                            viewModel.handleAction(EditorAction.ConfirmTextStyling)
                        },
                        onCancel = {
                            viewModel.handleAction(EditorAction.CancelTextStyling)
                        },
                        canUndo = state.canUndo,
                        canRedo = state.canRedo,
                        onUndo = { viewModel.handleAction(EditorAction.Undo) },
                        onRedo = { viewModel.handleAction(EditorAction.Redo) }
                    )
                }
            }
        }
    }

    private fun loadAndProcessBitmapFromUri(uri: Uri): Bitmap? {
        return try {
            val inputStream = contentResolver.openInputStream(uri)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()

            // Handle image rotation based on EXIF data
            val rotatedBitmap = handleImageRotation(uri, bitmap)

            // Resize if image is too large (to prevent memory issues)
            resizeIfNeeded(rotatedBitmap)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun handleImageRotation(uri: Uri, bitmap: Bitmap?): Bitmap? {
        if (bitmap == null) return null

        return try {
            val inputStream: InputStream? = contentResolver.openInputStream(uri)
            val exif = ExifInterface(inputStream!!)
            val orientation = exif.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
            inputStream.close()

            val matrix = Matrix()
            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            }

            if (!matrix.isIdentity) {
                Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            } else {
                bitmap
            }
        } catch (e: Exception) {
            e.printStackTrace()
            bitmap
        }
    }

    private fun resizeIfNeeded(bitmap: Bitmap?): Bitmap? {
        if (bitmap == null) return null

        val maxSize = 2048
        val width = bitmap.width
        val height = bitmap.height

        if (width <= maxSize && height <= maxSize) {
            return bitmap
        }

        val ratio = minOf(maxSize.toFloat() / width, maxSize.toFloat() / height)
        val newWidth = (width * ratio).toInt()
        val newHeight = (height * ratio).toInt()

        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }

    private fun createImageUri(): Uri {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val imageFileName = "JPEG_${timeStamp}_"
        val storageDir = File(getExternalFilesDir(null), "Pictures")
        if (!storageDir.exists()) storageDir.mkdirs()

        val imageFile = File.createTempFile(imageFileName, ".jpg", storageDir)
        return FileProvider.getUriForFile(this, "${packageName}.fileprovider", imageFile)
    }

    private fun saveImageToGallery(bitmap: Bitmap) {
        try {
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val fileName = "AI_EDIT_${timeStamp}.jpg"

            val resolver = contentResolver
            val contentValues = android.content.ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/AI Photo Editor")
            }

            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
            uri?.let {
                resolver.openOutputStream(it)?.use { outputStream ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 95, outputStream)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun shareImage(bitmap: Bitmap) {
        try {
            val cachePath = File(cacheDir, "images")
            cachePath.mkdirs()
            val file = File(cachePath, "shared_image.jpg")

            FileOutputStream(file).use { outputStream ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 95, outputStream)
            }

            val uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", file)

            val shareIntent = Intent().apply {
                action = Intent.ACTION_SEND
                type = "image/jpeg"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            startActivity(Intent.createChooser(shareIntent, "Share Image"))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

@Composable
fun FirstScreen(
    onSelectFromGallery: () -> Unit,
    onTakePhoto: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize()
    ) {
        // Background Image
        Image(
            painter = painterResource(id = R.drawable.first_screen_bg),
            contentDescription = "Background",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        // Bottom Buttons
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Select from Gallery Button
            Button(
                onClick = onSelectFromGallery,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(
                    imageVector = Icons.Default.PhotoLibrary,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Select Image from Gallery",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }

            // Camera Button
            Button(
                onClick = onTakePhoto,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary
                )
            ) {
                Icon(
                    imageVector = Icons.Default.CameraAlt,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Take Photo with Camera",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
fun EditorScreen(
    state: com.dlab.myaipiceditor.data.EditorState,
    onActionClick: (EditorAction) -> Unit,
    onSelectFromGallery: () -> Unit,
    onTakePhoto: () -> Unit,
    onSaveImage: () -> Unit,
    onShareImage: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        topBar = {
            EditorTopBar(
                canUndo = state.canUndo && !state.isProcessing,
                canRedo = state.canRedo && !state.isProcessing,
                onBackClick = { /* TODO: Navigate back to first screen */ },
                onUndoClick = { onActionClick(EditorAction.Undo) },
                onRedoClick = { onActionClick(EditorAction.Redo) },
                onSaveClick = onSaveImage
            )
        },
        bottomBar = {
            EditorBottomToolbar(
                onToolClick = { tool ->
                    when (tool) {
                        "crop" -> onActionClick(EditorAction.StartCrop)
                        "rotate" -> onActionClick(EditorAction.RotateImage(90f))
                        "text" -> onActionClick(EditorAction.StartAddText)
                        "adjust" -> { /* TODO: Show adjustment controls */ }
                        "ai_bg_removal" -> { /* TODO: Implement AI Background Removal */ }
                        "ai_object_removal" -> { /* TODO: Implement AI Object Removal */ }
                        "ai_enhancement" -> { /* TODO: Implement AI Photo Enhancement */ }
                        "ai_upscaler" -> { /* TODO: Implement AI Photo Upscaler */ }
                    }
                },
                isProcessing = state.isProcessing
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Main Image Preview Area
            ZoomableImagePreview(
                bitmap = state.currentImage,
                isProcessing = state.isProcessing,
                processingMessage = state.processingMessage,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )

            // Error Display
            state.error?.let { error ->
                ErrorCard(
                    error = error,
                    onDismiss = { onActionClick(EditorAction.ClearError) }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorTopBar(
    canUndo: Boolean,
    canRedo: Boolean,
    onBackClick: () -> Unit,
    onUndoClick: () -> Unit,
    onRedoClick: () -> Unit,
    onSaveClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    TopAppBar(
        title = {
            Text(
                "Edit Photo",
                fontWeight = FontWeight.Medium
            )
        },
        navigationIcon = {
            IconButton(onClick = onBackClick) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
            }
        },
        actions = {
            IconButton(
                onClick = onUndoClick,
                enabled = canUndo
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Undo,
                    "Undo",
                    tint = if (canUndo) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    }
                )
            }

            IconButton(
                onClick = onRedoClick,
                enabled = canRedo
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Redo,
                    "Redo",
                    tint = if (canRedo) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    }
                )
            }

            IconButton(onClick = onSaveClick) {
                Icon(Icons.Default.Save, "Save")
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        modifier = modifier
    )
}

@Composable
fun EditorBottomToolbar(
    onToolClick: (String) -> Unit,
    isProcessing: Boolean,
    modifier: Modifier = Modifier
) {
    val tools = listOf(
        ToolItem("crop", "Crop", Icons.Default.Crop),
        ToolItem("rotate", "Rotate", Icons.AutoMirrored.Filled.RotateRight),
        ToolItem("text", "Text", Icons.Default.TextFields),
        ToolItem("adjust", "Adjust", Icons.Default.Tune),
        ToolItem("ai_bg_removal", "AI Background Removal", Icons.Default.PhotoFilter),
        ToolItem("ai_object_removal", "AI Object Removal", Icons.Default.AutoFixHigh),
        ToolItem("ai_enhancement", "AI Photo Enhancement", Icons.Default.Face),
        ToolItem("ai_upscaler", "AI Photo Upscaler", Icons.Default.ZoomIn)
    )

    Surface(
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 8.dp,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            tools.forEach { tool ->
                ToolButton(
                    tool = tool,
                    onClick = { onToolClick(tool.id) },
                    enabled = !isProcessing
                )
            }
        }
    }
}

data class ToolItem(
    val id: String,
    val name: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
)

@Composable
fun ToolButton(
    tool: ToolItem,
    onClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .widthIn(min = 64.dp)
    ) {
        Icon(
            imageVector = tool.icon,
            contentDescription = tool.name,
            modifier = Modifier.size(24.dp),
            tint = if (enabled) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            }
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = tool.name,
            style = MaterialTheme.typography.labelSmall,
            color = if (enabled) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            }
        )
    }
}

@Composable
fun ZoomableImagePreview(
    bitmap: Bitmap?,
    isProcessing: Boolean,
    processingMessage: String,
    modifier: Modifier = Modifier
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    val transformableState = rememberTransformableState { zoomChange, offsetChange, _ ->
        scale = (scale * zoomChange).coerceIn(0.5f, 5f)
        offset += offsetChange
    }

    Card(
        modifier = modifier.padding(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            when {
                isProcessing -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(48.dp),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = processingMessage,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                bitmap != null -> {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "Image Preview",
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer(
                                scaleX = scale,
                                scaleY = scale,
                                translationX = offset.x,
                                translationY = offset.y
                            )
                            .transformable(state = transformableState),
                        contentScale = ContentScale.Fit
                    )
                }

                else -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.PhotoCamera,
                            contentDescription = "No Image",
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "No image selected",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ErrorCard(
    error: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Error,
                contentDescription = "Error",
                tint = MaterialTheme.colorScheme.onErrorContainer
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = error,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onDismiss) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close",
                    tint = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
    }
}