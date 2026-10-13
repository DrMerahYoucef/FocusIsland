package com.example.ui.screen.revisions

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CropFree
import androidx.compose.material.icons.rounded.DocumentScanner
import androidx.compose.material.icons.rounded.Gesture
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Rotate90DegreesCw
import androidx.compose.material.icons.rounded.Crop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.db.entity.RevisionDeckEntity
import com.example.data.repository.OcrEngineChoice
import com.example.ui.designsystem.EmptyState
import com.example.ui.designsystem.LoadingState
import com.example.ui.designsystem.ThemePreviews
import com.example.ui.theme.CameraPalette
import com.example.ui.theme.FocusIslandTheme
import com.example.ui.theme.Spacing
import com.example.ui.theme.ThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class CropMode { RECTANGLE, LASSO, QUADRILATERAL }

/** How the card is made from the cropped photo. */
enum class CardSource { ON_DEVICE, GEMINI, PHOTO }

private val DEFAULT_RECT = listOf(0.1f, 0.15f, 0.9f, 0.85f)
private val WHOLE_RECT = listOf(0f, 0f, 1f, 1f)
private val DEFAULT_CORNERS = listOf(Offset(0.12f, 0.15f), Offset(0.88f, 0.15f), Offset(0.88f, 0.85f), Offset(0.12f, 0.85f))

private val FloatListSaver = listSaver<List<Float>, Float>(save = { it }, restore = { it.toList() })

private val OffsetListSaver = listSaver<List<Offset>, Float>(
    save = { pts -> pts.flatMap { listOf(it.x, it.y) } },
    restore = { flat -> flat.chunked(2).map { Offset(it[0], it[1]) } }
)

private sealed interface PhotoLoad {
    data object Loading : PhotoLoad
    data object Failed : PhotoLoad
    data class Ready(val bitmap: Bitmap) : PhotoLoad
}

/**
 * Crop the photo (rectangle, free shape, or page corners with straightening), rotate it, then
 * "Next" opens the Create card sheet: deck, how to read it (on device, Gemini, keep as photo),
 * optional title or Gemini instructions. The card is made in the background; the Study tab
 * shows its progress. Selection, mode and rotation survive rotation and process death.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CropEditorScreen(
    imagePath: String,
    wholePage: Boolean = false,
    viewModel: RevisionsViewModel,
    onCropConfirmed: () -> Unit,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val appColors = MaterialTheme.colorScheme

    var quarterTurns by rememberSaveable { mutableIntStateOf(0) }
    var mode by rememberSaveable { mutableStateOf(CropMode.RECTANGLE) }
    val initialRect = if (wholePage) WHOLE_RECT else DEFAULT_RECT
    var rect by rememberSaveable(stateSaver = FloatListSaver) { mutableStateOf(initialRect) }
    var corners by rememberSaveable(stateSaver = OffsetListSaver) { mutableStateOf(DEFAULT_CORNERS) }
    var lasso by rememberSaveable(stateSaver = OffsetListSaver) { mutableStateOf(emptyList<Offset>()) }

    var base by remember(imagePath) { mutableStateOf<PhotoLoad>(PhotoLoad.Loading) }
    var shown by remember { mutableStateOf<Bitmap?>(null) }
    var cropping by remember { mutableStateOf(false) }
    var cropped by remember { mutableStateOf<Bitmap?>(null) }
    val msgCropFailed = stringResource(R.string.crop_failed)

    LaunchedEffect(imagePath) {
        base = ImageTools.loadUpright(imagePath)?.let { PhotoLoad.Ready(it) } ?: PhotoLoad.Failed
    }
    LaunchedEffect(base, quarterTurns) {
        val b = (base as? PhotoLoad.Ready)?.bitmap ?: return@LaunchedEffect
        shown = withContext(Dispatchers.Default) { ImageTools.rotate(b, quarterTurns * 90) }
    }

    fun resetSelection() {
        rect = initialRect
        corners = DEFAULT_CORNERS
        lasso = emptyList()
    }

    val discard = {
        ImageTools.deleteQuietly(imagePath)
        onBack()
    }

    val canContinue = shown != null && !cropping && (mode != CropMode.LASSO || lasso.size >= 3)
    fun next() {
        val bmp = shown ?: return
        cropping = true
        scope.launch {
            val result = withContext(Dispatchers.Default) {
                runCatching {
                    when (mode) {
                        CropMode.RECTANGLE -> ImageTools.cropRect(bmp, rect[0], rect[1], rect[2], rect[3])
                        CropMode.LASSO -> ImageTools.cropFreeShape(bmp, lasso)
                        CropMode.QUADRILATERAL -> ImageTools.cropCorners(bmp, corners[0], corners[1], corners[2], corners[3])
                    }
                }.getOrNull()
            }
            cropping = false
            if (result != null) cropped = result else snackbar.showSnackbar(msgCropFailed)
        }
    }

    CameraTheme {
        Box(Modifier.fillMaxSize().background(CameraPalette.background)) {
            Column(Modifier.fillMaxSize()) {
                // Top bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = Spacing.xs, vertical = Spacing.xxs),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = discard) {
                        Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.crop_close), tint = CameraPalette.onChrome)
                    }
                    Text(
                        stringResource(R.string.crop_title),
                        style = MaterialTheme.typography.titleLarge,
                        color = CameraPalette.onChrome,
                        modifier = Modifier.weight(1f).padding(start = Spacing.xs)
                    )
                    IconButton(onClick = { resetSelection() }, enabled = shown != null) {
                        Icon(Icons.Rounded.Restore, contentDescription = stringResource(R.string.crop_reset), tint = CameraPalette.onChrome)
                    }
                    IconButton(onClick = { quarterTurns = (quarterTurns + 1) % 4; resetSelection() }, enabled = shown != null) {
                        Icon(Icons.Rounded.Rotate90DegreesCw, contentDescription = stringResource(R.string.crop_rotate), tint = CameraPalette.onChrome)
                    }
                }

                // Photo + crop overlay
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(Spacing.lg),
                    contentAlignment = Alignment.Center
                ) {
                    val bmp = shown
                    when {
                        base is PhotoLoad.Failed -> EmptyState(
                            icon = Icons.Rounded.Image,
                            title = stringResource(R.string.crop_load_failed_title),
                            body = stringResource(R.string.crop_load_failed_body),
                            actionLabel = stringResource(R.string.back),
                            onAction = onBack
                        )
                        bmp == null -> LoadingState()
                        else -> CropCanvas(
                            bitmap = bmp,
                            mode = mode,
                            rect = rect,
                            onRect = { rect = it },
                            corners = corners,
                            onCorners = { corners = it },
                            lasso = lasso,
                            onLasso = { lasso = it }
                        )
                    }
                }

                // Bottom panel
                Surface(color = CameraPalette.chrome, contentColor = CameraPalette.onChrome) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(Spacing.md),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                    ) {
                        val modes = listOf(
                            Triple(CropMode.RECTANGLE, Icons.Rounded.Crop, R.string.crop_mode_rect),
                            Triple(CropMode.LASSO, Icons.Rounded.Gesture, R.string.crop_mode_free),
                            Triple(CropMode.QUADRILATERAL, Icons.Rounded.CropFree, R.string.crop_mode_corners)
                        )
                        SingleChoiceSegmentedButtonRow(Modifier.widthIn(max = Spacing.formMaxWidth).fillMaxWidth()) {
                            modes.forEachIndexed { i, (m, icon, label) ->
                                SegmentedButton(
                                    selected = mode == m,
                                    onClick = { mode = m },
                                    shape = SegmentedButtonDefaults.itemShape(i, modes.size),
                                    icon = { SegmentedButtonDefaults.Icon(active = mode == m) { Icon(icon, contentDescription = null, modifier = Modifier.size(SegmentedButtonDefaults.IconSize)) } }
                                ) { Text(stringResource(label), maxLines = 1) }
                            }
                        }
                        Text(
                            stringResource(
                                when (mode) {
                                    CropMode.RECTANGLE -> R.string.crop_hint_rect
                                    CropMode.LASSO -> R.string.crop_hint_free
                                    CropMode.QUADRILATERAL -> R.string.crop_hint_corners
                                }
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = CameraPalette.onChromeVariant,
                            textAlign = TextAlign.Center
                        )
                        Button(
                            onClick = ::next,
                            enabled = canContinue,
                            modifier = Modifier.widthIn(max = Spacing.formMaxWidth).fillMaxWidth().height(Spacing.buttonHeight)
                        ) {
                            if (cropping) {
                                CircularProgressIndicator(strokeWidth = Spacing.progressStroke, modifier = Modifier.size(ButtonDefaults.IconSize))
                            } else {
                                Text(stringResource(R.string.crop_next))
                                Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                                Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                            }
                        }
                    }
                }
            }
            SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = Spacing.xxl * 3))
        }
    }

    // The sheet uses the app's own colours, like every other form.
    cropped?.let { bmp ->
        MaterialTheme(colorScheme = appColors) {
            CreateCardSheet(
                preview = bmp,
                viewModel = viewModel,
                onDismiss = { cropped = null },
                onCreate = { source, deckId, text, explain ->
                    when (source) {
                        CardSource.ON_DEVICE, CardSource.GEMINI -> viewModel.launchBackgroundOcrCardCreation(
                            croppedBitmap = bmp,
                            temporaryPromptAddendum = text,
                            explainMode = source == CardSource.GEMINI && explain,
                            targetDeckId = deckId,
                            engineChoice = if (source == CardSource.GEMINI) OcrEngineChoice.GEMINI else OcrEngineChoice.ML_KIT
                        )
                        CardSource.PHOTO -> viewModel.launchBackgroundLocalImageCardCreation(bitmap = bmp, userTitle = text, deckId = deckId)
                    }
                    ImageTools.deleteQuietly(imagePath)
                    cropped = null
                    onCropConfirmed()
                }
            )
        }
    }
}

/** Photo letterboxed in the available space, with the crop overlay for the current mode. */
@Composable
private fun CropCanvas(
    bitmap: Bitmap,
    mode: CropMode,
    rect: List<Float>,
    onRect: (List<Float>) -> Unit,
    corners: List<Offset>,
    onCorners: (List<Offset>) -> Unit,
    lasso: List<Offset>,
    onLasso: (List<Offset>) -> Unit
) {
    val density = LocalDensity.current
    val handleR = with(density) { Spacing.cropHandle.toPx() }
    val hitR = with(density) { Spacing.cropHandleHit.toPx() }
    val stroke = with(density) { Spacing.cropStroke.toPx() }
    val image = remember(bitmap) { bitmap.asImageBitmap() }
    val a11y = stringResource(R.string.crop_area_a11y)

    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val cw = constraints.maxWidth.toFloat()
        val ch = constraints.maxHeight.toFloat()
        val scale = minOf(cw / bitmap.width, ch / bitmap.height)
        val dw = bitmap.width * scale
        val dh = bitmap.height * scale
        val ox = (cw - dw) / 2f
        val oy = (ch - dh) / 2f
        fun toScreen(p: Offset) = Offset(ox + p.x * dw, oy + p.y * dh)
        fun toNorm(s: Offset) = Offset(((s.x - ox) / dw).coerceIn(0f, 1f), ((s.y - oy) / dh).coerceIn(0f, 1f))

        Image(image, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())

        val currentRect by rememberUpdatedState(rect)
        val currentCorners by rememberUpdatedState(corners)
        val currentLasso by rememberUpdatedState(lasso)
        val minSize = 0.05f

        Canvas(
            Modifier
                .fillMaxSize()
                .semantics { contentDescription = a11y }
                .pointerInput(mode, dw, dh) {
                    when (mode) {
                        CropMode.RECTANGLE -> {
                            var handle = -1 // 0 TL, 1 TR, 2 BR, 3 BL, 4 move
                            detectDragGestures(
                                onDragStart = { at ->
                                    val r = currentRect
                                    val pts = listOf(Offset(r[0], r[1]), Offset(r[2], r[1]), Offset(r[2], r[3]), Offset(r[0], r[3])).map(::toScreen)
                                    handle = pts.indexOfFirst { (it - at).getDistance() < hitR }
                                    if (handle == -1 && Rect(pts[0], pts[2]).contains(at)) handle = 4
                                },
                                onDrag = { change, drag ->
                                    change.consume()
                                    val dx = drag.x / dw
                                    val dy = drag.y / dh
                                    var (l, t, r, b) = currentRect
                                    when (handle) {
                                        0 -> { l = (l + dx).coerceIn(0f, r - minSize); t = (t + dy).coerceIn(0f, b - minSize) }
                                        1 -> { r = (r + dx).coerceIn(l + minSize, 1f); t = (t + dy).coerceIn(0f, b - minSize) }
                                        2 -> { r = (r + dx).coerceIn(l + minSize, 1f); b = (b + dy).coerceIn(t + minSize, 1f) }
                                        3 -> { l = (l + dx).coerceIn(0f, r - minSize); b = (b + dy).coerceIn(t + minSize, 1f) }
                                        4 -> {
                                            val w = r - l
                                            val h = b - t
                                            l = (l + dx).coerceIn(0f, 1f - w); t = (t + dy).coerceIn(0f, 1f - h)
                                            r = l + w; b = t + h
                                        }
                                        else -> return@detectDragGestures
                                    }
                                    onRect(listOf(l, t, r, b))
                                },
                                onDragEnd = { handle = -1 },
                                onDragCancel = { handle = -1 }
                            )
                        }
                        CropMode.QUADRILATERAL -> {
                            var handle = -1
                            detectDragGestures(
                                onDragStart = { at -> handle = currentCorners.map(::toScreen).indexOfFirst { (it - at).getDistance() < hitR } },
                                onDrag = { change, _ ->
                                    change.consume()
                                    if (handle >= 0) onCorners(currentCorners.toMutableList().also { it[handle] = toNorm(change.position) })
                                },
                                onDragEnd = { handle = -1 },
                                onDragCancel = { handle = -1 }
                            )
                        }
                        CropMode.LASSO -> detectDragGestures(
                            onDragStart = { at -> onLasso(listOf(toNorm(at))) },
                            onDrag = { change, _ ->
                                change.consume()
                                val p = toNorm(change.position)
                                val last = currentLasso.lastOrNull()
                                // Skip points closer than ~0.5 % to keep the outline (and saved state) small.
                                if (last == null || (p - last).getDistance() > 0.005f) onLasso(currentLasso + p)
                            }
                        )
                    }
                }
        ) {
            when (mode) {
                CropMode.RECTANGLE -> {
                    val tl = toScreen(Offset(rect[0], rect[1]))
                    val br = toScreen(Offset(rect[2], rect[3]))
                    clipRect(tl.x, tl.y, br.x, br.y, clipOp = ClipOp.Difference) { drawRect(CameraPalette.scrim) }
                    drawRect(CameraPalette.handle, topLeft = tl, size = androidx.compose.ui.geometry.Size(br.x - tl.x, br.y - tl.y), style = Stroke(stroke))
                    // Thirds grid helps line up a page.
                    for (i in 1..2) {
                        val x = tl.x + (br.x - tl.x) * i / 3f
                        val y = tl.y + (br.y - tl.y) * i / 3f
                        drawLine(CameraPalette.shutterRing, Offset(x, tl.y), Offset(x, br.y), strokeWidth = stroke / 2)
                        drawLine(CameraPalette.shutterRing, Offset(tl.x, y), Offset(br.x, y), strokeWidth = stroke / 2)
                    }
                    listOf(tl, Offset(br.x, tl.y), br, Offset(tl.x, br.y)).forEach { drawHandle(it, handleR) }
                }
                CropMode.QUADRILATERAL -> {
                    val pts = corners.map(::toScreen)
                    val path = Path().apply {
                        moveTo(pts[0].x, pts[0].y); pts.drop(1).forEach { lineTo(it.x, it.y) }; close()
                    }
                    clipPath(path, clipOp = ClipOp.Difference) { drawRect(CameraPalette.scrim) }
                    drawPath(path, CameraPalette.handle, style = Stroke(stroke))
                    pts.forEach { drawHandle(it, handleR) }
                }
                CropMode.LASSO -> {
                    if (lasso.size >= 2) {
                        val pts = lasso.map(::toScreen)
                        val path = Path().apply {
                            moveTo(pts[0].x, pts[0].y); pts.drop(1).forEach { lineTo(it.x, it.y) }; close()
                        }
                        clipPath(path, clipOp = ClipOp.Difference) { drawRect(CameraPalette.scrim) }
                        drawPath(path, CameraPalette.accent, style = Stroke(stroke))
                    } else {
                        drawRect(CameraPalette.scrim, topLeft = Offset(ox, oy), size = androidx.compose.ui.geometry.Size(dw, dh))
                    }
                }
            }
        }
    }
}

private fun DrawScope.drawHandle(center: Offset, radius: Float) {
    drawCircle(CameraPalette.handle, radius = radius, center = center)
    drawCircle(CameraPalette.accent, radius = radius * 0.7f, center = center)
}

/** Final step: deck, how to make the card, optional title / Gemini instructions, then Create. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateCardSheet(
    preview: Bitmap,
    viewModel: RevisionsViewModel,
    onDismiss: () -> Unit,
    onCreate: (source: CardSource, deckId: String, text: String?, explain: Boolean) -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var deckId by rememberSaveable { mutableStateOf(state.selectedDeckId) }
    var source by rememberSaveable { mutableStateOf(CardSource.ON_DEVICE) }
    var text by rememberSaveable { mutableStateOf("") }
    var explain by rememberSaveable { mutableStateOf(false) }
    var newDeck by rememberSaveable { mutableStateOf(false) }
    val image = remember(preview) { preview.asImageBitmap() }
    val deckOk = state.decks.any { it.id == deckId }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.lg)
                .padding(bottom = Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            CreateCardForm(
                image = { mod -> Image(image, contentDescription = stringResource(R.string.create_preview_a11y), contentScale = ContentScale.Fit, modifier = mod) },
                decks = state.decks,
                deckId = deckId,
                onDeck = { deckId = it },
                onNewDeck = { newDeck = true },
                hasApiKey = state.hasApiKey,
                source = source,
                onSource = { source = it },
                text = text,
                onText = { text = it },
                explain = explain,
                onExplain = { explain = it }
            )
            Button(
                onClick = { onCreate(source, deckId, text.trim().ifBlank { null }, explain) },
                enabled = deckOk && (source != CardSource.GEMINI || state.hasApiKey),
                modifier = Modifier.fillMaxWidth().height(Spacing.buttonHeight)
            ) { Text(stringResource(R.string.create_button)) }
        }
    }

    if (newDeck) {
        DeckDialog(
            title = stringResource(R.string.study_new_deck),
            confirmLabel = stringResource(R.string.study_create),
            onConfirm = { name, color ->
                viewModel.addDeckAndSelect(name, color) { id -> deckId = id }
                newDeck = false
            },
            onDismiss = { newDeck = false }
        )
    }
}

/** Stateless body of the Create card sheet (previewable). */
@Composable
private fun CreateCardForm(
    image: @Composable (Modifier) -> Unit,
    decks: List<RevisionDeckEntity>,
    deckId: String,
    onDeck: (String) -> Unit,
    onNewDeck: () -> Unit,
    hasApiKey: Boolean,
    source: CardSource,
    onSource: (CardSource) -> Unit,
    text: String,
    onText: (String) -> Unit,
    explain: Boolean,
    onExplain: (Boolean) -> Unit
) {
    Text(stringResource(R.string.create_title), style = MaterialTheme.typography.titleLarge)
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
        image(Modifier.fillMaxWidth().height(Spacing.cropPreviewHeight).padding(Spacing.xs))
    }
    DeckPicker(decks = decks, selectedId = deckId, onSelect = onDeck, onNewDeck = onNewDeck, modifier = Modifier.fillMaxWidth())

    Text(stringResource(R.string.create_how), style = MaterialTheme.typography.titleSmall)
    Column(Modifier.selectableGroup()) {
        SourceOption(CardSource.ON_DEVICE, Icons.Rounded.DocumentScanner, R.string.create_on_device, R.string.create_on_device_body, source, true, onSource)
        SourceOption(
            CardSource.GEMINI, Icons.Rounded.AutoAwesome, R.string.create_gemini,
            if (hasApiKey) R.string.create_gemini_body else R.string.create_gemini_no_key,
            source, hasApiKey, onSource
        )
        SourceOption(CardSource.PHOTO, Icons.Rounded.Image, R.string.create_photo, R.string.create_photo_body, source, true, onSource)
    }

    if (source == CardSource.GEMINI) {
        OutlinedTextField(
            value = text,
            onValueChange = onText,
            label = { Text(stringResource(R.string.create_instructions)) },
            placeholder = { Text(stringResource(R.string.create_instructions_example)) },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        ListItem(
            headlineContent = { Text(stringResource(R.string.create_explain)) },
            supportingContent = { Text(stringResource(R.string.create_explain_body)) },
            trailingContent = { Switch(checked = explain, onCheckedChange = null) },
            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            modifier = Modifier
                .fillMaxWidth()
                .selectable(selected = explain, role = Role.Switch, onClick = { onExplain(!explain) })
        )
    } else {
        OutlinedTextField(
            value = text,
            onValueChange = onText,
            label = { Text(stringResource(R.string.study_field_title_optional)) },
            supportingText = {
                Text(stringResource(if (source == CardSource.ON_DEVICE) R.string.create_title_detect else R.string.study_field_title_auto))
            },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun SourceOption(
    value: CardSource,
    icon: ImageVector,
    title: Int,
    body: Int,
    selected: CardSource,
    enabled: Boolean,
    onSelect: (CardSource) -> Unit
) {
    ListItem(
        leadingContent = { Icon(icon, contentDescription = null) },
        headlineContent = { Text(stringResource(title)) },
        supportingContent = { Text(stringResource(body)) },
        trailingContent = { RadioButton(selected = selected == value, onClick = null, enabled = enabled) },
        colors = ListItemDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = if (selected == value) 1f else 0f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected == value, enabled = enabled, role = Role.RadioButton, onClick = { onSelect(value) })
    )
}

@ThemePreviews
@Composable
private fun CreateCardFormPreview() {
    FocusIslandTheme(themeMode = ThemeMode.SYSTEM) {
        Surface {
            Column(Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                CreateCardForm(
                    image = { mod -> Box(mod.background(MaterialTheme.colorScheme.surfaceContainerHighest)) },
                    decks = listOf(RevisionDeckEntity(id = "d", name = "Biology", colorHex = "#1F6A64")),
                    deckId = "d",
                    onDeck = {}, onNewDeck = {},
                    hasApiKey = false,
                    source = CardSource.ON_DEVICE, onSource = {},
                    text = "", onText = {},
                    explain = false, onExplain = {}
                )
            }
        }
    }
}
