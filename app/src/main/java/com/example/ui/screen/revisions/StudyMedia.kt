package com.example.ui.screen.revisions

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.example.R
import com.example.data.repository.LocalMediaStore
import com.example.ui.theme.Spacing
import kotlinx.coroutines.delay
import java.io.File

/** Saving to the public Pictures/Download folders needs Android 10+ (no storage permission). */
val canSaveToSharedStorage: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

/** A card photo, decoded off the main thread by Coil. Tap opens [ZoomableImageDialog]. */
@Composable
fun CardImage(file: File, onZoom: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = Spacing.sceneHeight)
                .clickable(onClickLabel = stringResource(R.string.study_zoom_photo), onClick = onZoom)
        ) {
            AsyncImage(
                model = file,
                contentDescription = stringResource(R.string.study_card_photo),
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxWidth()
            )
        }
        Text(
            stringResource(R.string.study_tap_to_zoom),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Spacing.xxs)
        )
    }
}

/** Full-screen photo viewer: pinch to zoom, drag to move. Black backdrop in both themes (it is a photo viewer). */
@Composable
fun ZoomableImageDialog(file: File, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        var scale by remember { mutableFloatStateOf(1f) }
        var offsetX by remember { mutableFloatStateOf(0f) }
        var offsetY by remember { mutableFloatStateOf(0f) }
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            AsyncImage(
                model = file,
                contentDescription = stringResource(R.string.study_card_photo),
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(1f, 5f)
                            if (scale > 1f) {
                                offsetX += pan.x
                                offsetY += pan.y
                            } else {
                                offsetX = 0f
                                offsetY = 0f
                            }
                        }
                    }
                    .graphicsLayer(scaleX = scale, scaleY = scale, translationX = offsetX, translationY = offsetY)
            )
            FilledTonalIconButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(Spacing.md)
            ) {
                Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.study_close))
            }
        }
    }
}

/** Save / Send row for a card's photo or recording. Save is hidden before Android 10. */
@Composable
fun MediaActions(onSave: () -> Unit, onShare: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        if (canSaveToSharedStorage) {
            OutlinedButton(onClick = onSave, modifier = Modifier.weight(1f)) {
                Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                Text(stringResource(R.string.study_save_copy))
            }
        }
        Button(onClick = onShare, modifier = Modifier.weight(1f)) {
            Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
            Spacer(Modifier.size(ButtonDefaults.IconSpacing))
            Text(stringResource(R.string.study_send))
        }
    }
}

/**
 * Plays a voice card. The player is released when the card leaves the screen.
 * [context] is kept for source compatibility with existing callers.
 */
@Suppress("UNUSED_PARAMETER")
@Composable
fun AudioCardPlayerView(audioFile: File, context: Context, autoPlay: Boolean = false) {
    var playing by remember(audioFile) { mutableStateOf(false) }
    var position by remember(audioFile) { mutableIntStateOf(0) }
    var duration by remember(audioFile) { mutableIntStateOf(1) }
    var failed by remember(audioFile) { mutableStateOf(false) }

    val player = remember(audioFile) {
        MediaPlayer().apply {
            try {
                setDataSource(audioFile.absolutePath)
                prepare()
                duration = this.duration.coerceAtLeast(1)
                setOnCompletionListener {
                    playing = false
                    position = 0
                }
                if (autoPlay) {
                    start()
                    playing = true
                }
            } catch (e: Exception) {
                failed = true
            }
        }
    }
    DisposableEffect(player) {
        onDispose { runCatching { player.stop(); player.release() } }
    }
    LaunchedEffect(playing) {
        while (playing) {
            position = runCatching { player.currentPosition }.getOrDefault(position)
            delay(200)
        }
    }

    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
        if (failed) {
            Text(
                stringResource(R.string.study_audio_failed),
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(Spacing.md)
            )
            return@Surface
        }
        Row(Modifier.padding(Spacing.md), verticalAlignment = Alignment.CenterVertically) {
            FilledIconButton(
                onClick = {
                    if (playing) player.pause() else player.start()
                    playing = !playing
                },
                modifier = Modifier.size(Spacing.xxl)
            ) {
                Icon(
                    if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = stringResource(if (playing) R.string.study_pause_audio else R.string.study_play_audio)
                )
            }
            Spacer(Modifier.size(Spacing.sm))
            Column(Modifier.weight(1f)) {
                val positionText = clock(position)
                Slider(
                    value = position.toFloat(),
                    onValueChange = {
                        position = it.toInt()
                        player.seekTo(position)
                    },
                    valueRange = 0f..duration.toFloat(),
                    modifier = Modifier.semantics { stateDescription = positionText }
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(positionText, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(clock(duration), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun clock(millis: Int): String {
    val seconds = millis / 1000
    return stringResource(R.string.study_recording_time, seconds / 60, seconds % 60)
}

// ---------------------------------------------------------------- save & share (return success)

/** Copies a card photo to Pictures/RevisionCards. Android 10+ only (see [canSaveToSharedStorage]). */
fun saveImageToGallery(context: Context, imageFile: File): Boolean = runCatching {
    if (!canSaveToSharedStorage) return false
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, "card_photo_${System.currentTimeMillis()}.${imageFile.extension.ifBlank { "jpg" }}")
        put(MediaStore.Images.Media.MIME_TYPE, LocalMediaStore.mimeTypeFor(imageFile.absolutePath))
        put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/RevisionCards")
    }
    val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return false
    context.contentResolver.openOutputStream(uri)?.use { out -> imageFile.inputStream().use { it.copyTo(out) } } != null
}.getOrDefault(false)

/**
 * Copies a voice card to Download/RevisionCards. Uses the Downloads collection: the Audio
 * collection does not accept the Download folder (that is why saving used to fail).
 */
fun saveAudioToDownloads(context: Context, audioFile: File): Boolean = runCatching {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
    val values = ContentValues().apply {
        put(MediaStore.Downloads.DISPLAY_NAME, "card_audio_${System.currentTimeMillis()}.${audioFile.extension.ifBlank { "m4a" }}")
        put(MediaStore.Downloads.MIME_TYPE, LocalMediaStore.mimeTypeFor(audioFile.absolutePath))
        put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/RevisionCards")
    }
    val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return false
    context.contentResolver.openOutputStream(uri)?.use { out -> audioFile.inputStream().use { it.copyTo(out) } } != null
}.getOrDefault(false)

/** Opens the share sheet for a card's file (through the app's FileProvider). */
fun shareMediaFile(context: Context, mediaFile: File, chooserTitle: String): Boolean = runCatching {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", mediaFile)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = LocalMediaStore.mimeTypeFor(mediaFile.absolutePath)
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, chooserTitle))
    true
}.getOrDefault(false)
