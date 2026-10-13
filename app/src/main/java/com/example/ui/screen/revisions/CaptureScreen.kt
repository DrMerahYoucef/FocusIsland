package com.example.ui.screen.revisions

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FlashAuto
import androidx.compose.material.icons.rounded.FlashOff
import androidx.compose.material.icons.rounded.FlashOn
import androidx.compose.material.icons.rounded.NoPhotography
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.ui.designsystem.ThemePreviews
import com.example.ui.theme.CameraPalette
import com.example.ui.theme.FocusIslandTheme
import com.example.ui.theme.LagoonDarkColors
import com.example.ui.theme.Spacing
import com.example.ui.theme.ThemeMode
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale
import kotlinx.coroutines.launch

/**
 * Camera and crop screens are always dark (the photo is the content). Material components
 * inside use the Lagoon dark scheme so buttons and chips keep the right contrast.
 */
@Composable
fun CameraTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LagoonDarkColors.copy(background = CameraPalette.background, surface = CameraPalette.background),
        typography = MaterialTheme.typography,
        shapes = MaterialTheme.shapes,
        content = content
    )
}

private val flashModes = listOf(ImageCapture.FLASH_MODE_OFF, ImageCapture.FLASH_MODE_AUTO, ImageCapture.FLASH_MODE_ON)

/**
 * "Scan with camera": full-screen viewfinder, shutter, flash and "Import a photo".
 * Typed and voice cards are created from the Study tab's "New card" sheet.
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun CaptureScreen(
    viewModel: RevisionsViewModel,
    onImageCaptured: (String) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    var askedOnce by rememberSaveable { mutableStateOf(false) }
    val permission = rememberPermissionState(Manifest.permission.CAMERA) { askedOnce = true }
    val granted = permission.status.isGranted
    // Asked before and Android no longer shows the prompt: only the system settings can help.
    val blocked = askedOnce && !granted && !permission.status.shouldShowRationale

    var flashIndex by rememberSaveable { mutableIntStateOf(0) }
    var capturing by remember { mutableStateOf(false) }
    var cameraFailed by remember { mutableStateOf(false) }
    var camera by remember { mutableStateOf<Camera?>(null) }

    val msgCaptureFailed = stringResource(R.string.capture_failed)
    val msgPhotoFailed = stringResource(R.string.study_photo_failed)

    LaunchedEffect(Unit) {
        ImageTools.purgeStaleCaptures(context)
        if (!permission.status.isGranted && !askedOnce) permission.launchPermissionRequest()
    }

    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            val file = ImageTools.copyUriToCache(context, uri)
            if (file != null) onImageCaptured(file.absolutePath) else snackbar.showSnackbar(msgPhotoFailed)
        }
    }
    val openGallery = { gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }

    val previewView = remember { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } }
    val imageCapture = remember {
        ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY).build()
    }
    LaunchedEffect(flashIndex) { imageCapture.flashMode = flashModes[flashIndex] }

    DisposableEffect(granted, lifecycleOwner) {
        if (!granted) return@DisposableEffect onDispose { }
        val future = ProcessCameraProvider.getInstance(context)
        var provider: ProcessCameraProvider? = null
        future.addListener({
            runCatching {
                val p = future.get().also { provider = it }
                val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
                p.unbindAll()
                camera = p.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture)
                cameraFailed = false
            }.onFailure {
                android.util.Log.e("CaptureScreen", "Camera binding failed", it)
                cameraFailed = true
            }
        }, ContextCompat.getMainExecutor(context))
        onDispose {
            provider?.unbindAll()
            camera = null
        }
    }

    fun takePhoto() {
        if (capturing) return
        capturing = true
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        val file = ImageTools.newCaptureFile(context)
        imageCapture.takePicture(
            ImageCapture.OutputFileOptions.Builder(file).build(),
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    capturing = false
                    onImageCaptured(file.absolutePath)
                }

                override fun onError(exception: ImageCaptureException) {
                    capturing = false
                    android.util.Log.e("CaptureScreen", "Capture failed", exception)
                    scope.launch { snackbar.showSnackbar(msgCaptureFailed) }
                }
            }
        )
    }

    CameraTheme {
        Box(
            Modifier
                .fillMaxSize()
                .background(CameraPalette.background)
        ) {
            when {
                granted && !cameraFailed -> AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
                granted -> CameraMessage(
                    title = stringResource(R.string.capture_camera_error_title),
                    body = stringResource(R.string.capture_camera_error),
                    primaryLabel = stringResource(R.string.study_add_gallery),
                    onPrimary = openGallery
                )
                else -> CameraMessage(
                    title = stringResource(R.string.capture_permission_title),
                    body = stringResource(if (blocked) R.string.capture_permission_blocked else R.string.capture_permission_body),
                    primaryLabel = stringResource(if (blocked) R.string.capture_permission_settings else R.string.capture_permission_allow),
                    onPrimary = {
                        if (blocked) {
                            context.startActivity(
                                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        } else {
                            permission.launchPermissionRequest()
                        }
                    },
                    secondaryLabel = stringResource(R.string.study_add_gallery),
                    onSecondary = openGallery
                )
            }

            // Top bar: close, hint, flash.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = Spacing.md, vertical = Spacing.xs),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ChromeIconButton(onClick = onBack) {
                    Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.capture_close))
                }
                Spacer(Modifier.weight(1f))
                if (granted && camera?.cameraInfo?.hasFlashUnit() == true) {
                    val (icon, label) = when (flashModes[flashIndex]) {
                        ImageCapture.FLASH_MODE_AUTO -> Icons.Rounded.FlashAuto to R.string.capture_flash_auto
                        ImageCapture.FLASH_MODE_ON -> Icons.Rounded.FlashOn to R.string.capture_flash_on
                        else -> Icons.Rounded.FlashOff to R.string.capture_flash_off
                    }
                    ChromeIconButton(onClick = { flashIndex = (flashIndex + 1) % flashModes.size }) {
                        Icon(icon, contentDescription = stringResource(label))
                    }
                }
            }

            // Bottom: hint, then gallery · shutter.
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(bottom = Spacing.lg),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                if (granted && !cameraFailed) {
                    Surface(color = CameraPalette.chrome, contentColor = CameraPalette.onChrome, shape = MaterialTheme.shapes.extraLarge) {
                        Text(
                            stringResource(if (state.hasApiKey) R.string.capture_hint else R.string.capture_hint_no_key),
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .widthIn(max = Spacing.formMaxWidth)
                                .padding(horizontal = Spacing.md, vertical = Spacing.xs)
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.xl),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    ChromeIconButton(onClick = openGallery, size = Spacing.cameraSideButton) {
                        Icon(Icons.Rounded.PhotoLibrary, contentDescription = stringResource(R.string.study_add_gallery))
                    }
                    ShutterButton(enabled = granted && !cameraFailed, busy = capturing, onClick = ::takePhoto)
                    Spacer(Modifier.size(Spacing.cameraSideButton))
                }
            }

            SnackbarHost(snackbar, modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = Spacing.shutter + Spacing.xxl))
        }
    }
}

/** Round translucent button that stays readable over any photo. */
@Composable
private fun ChromeIconButton(
    onClick: () -> Unit,
    size: androidx.compose.ui.unit.Dp = Spacing.minTouchTarget,
    content: @Composable () -> Unit
) {
    FilledTonalIconButton(
        onClick = onClick,
        colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = CameraPalette.chrome, contentColor = CameraPalette.onChrome),
        modifier = Modifier.size(size),
        content = content
    )
}

@Composable
private fun ShutterButton(enabled: Boolean, busy: Boolean, onClick: () -> Unit) {
    val label = stringResource(R.string.capture_take_photo)
    Surface(
        onClick = onClick,
        enabled = enabled && !busy,
        shape = CircleShape,
        color = CameraPalette.background.copy(alpha = 0f),
        modifier = Modifier
            .size(Spacing.shutter)
            .border(Spacing.xxs, CameraPalette.shutterRing, CircleShape)
            .semantics { contentDescription = label; role = Role.Button }
    ) {
        Box(contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .size(Spacing.shutter - Spacing.md)
                    .background(if (enabled) CameraPalette.shutter else CameraPalette.shutterRing, CircleShape)
            )
            if (busy) CircularProgressIndicator(color = CameraPalette.onAccent, strokeWidth = Spacing.progressStroke, modifier = Modifier.size(Spacing.lg))
        }
    }
}

/** Centered message on the dark camera background (permission, camera error). */
@Composable
private fun CameraMessage(
    title: String,
    body: String,
    primaryLabel: String,
    onPrimary: () -> Unit,
    secondaryLabel: String? = null,
    onSecondary: (() -> Unit)? = null
) {
    Box(Modifier.fillMaxSize().padding(Spacing.lg), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.widthIn(max = Spacing.formMaxWidth),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            Icon(Icons.Rounded.NoPhotography, contentDescription = null, tint = CameraPalette.accent, modifier = Modifier.size(Spacing.xxl))
            Text(title, style = MaterialTheme.typography.titleLarge, color = CameraPalette.onChrome, textAlign = TextAlign.Center)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = CameraPalette.onChromeVariant, textAlign = TextAlign.Center)
            Spacer(Modifier.size(Spacing.xs))
            Button(onClick = onPrimary) { Text(primaryLabel) }
            if (secondaryLabel != null && onSecondary != null) {
                OutlinedButton(onClick = onSecondary) { Text(secondaryLabel) }
            }
        }
    }
}

@ThemePreviews
@Composable
private fun CameraPiecesPreview() {
    FocusIslandTheme(themeMode = ThemeMode.SYSTEM) {
        CameraTheme {
            Box(Modifier.background(CameraPalette.background)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CameraMessage(
                        title = stringResource(R.string.capture_permission_title),
                        body = stringResource(R.string.capture_permission_body),
                        primaryLabel = stringResource(R.string.capture_permission_allow),
                        onPrimary = {},
                        secondaryLabel = stringResource(R.string.study_add_gallery),
                        onSecondary = {}
                    )
                    ShutterButton(enabled = true, busy = false, onClick = {})
                    TextButton(onClick = {}) { Text(stringResource(R.string.study_add_gallery)) }
                }
            }
        }
    }
}
