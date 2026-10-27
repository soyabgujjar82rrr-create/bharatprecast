package com.example.ui.screens

import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.material.icons.filled.Colorize
import androidx.compose.material.icons.filled.FormatPaint
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.GalleryPhoto
import com.example.ui.editor.ColorClipboard
import com.example.ui.editor.ColorRecolorLayer
import com.example.ui.editor.ColorTransferControls
import com.example.ui.editor.ColorTransferEngine
import com.example.ui.editor.CutoutClipboard
import com.example.ui.editor.CutoutLayer
import com.example.ui.editor.CutoutLibraryDialog
import com.example.ui.editor.DrawPoint
import com.example.ui.editor.DrawStroke
import com.example.ui.editor.EditorState
import com.example.ui.editor.FilterPreset
import com.example.ui.editor.LoupeOverlay
import com.example.ui.editor.ObjectRecolorSettings
import com.example.ui.editor.PhotoEditorEngine
import com.example.ui.editor.RecolorObjectDialog
import com.example.ui.editor.SampledColorFineTuneDialog
import com.example.ui.editor.SmartCutoutPreviewDialog
import com.example.ui.editor.SmartObjectControls
import com.example.ui.editor.SmartObjectEngine
import com.example.ui.editor.TextSticker
import com.example.ui.theme.CyanTertiary
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkBorder
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceContainerHigh
import com.example.ui.theme.DarkSurfaceVariant
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.IndigoPrimary
import com.example.ui.theme.RoseSecondary
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

enum class EditorTab(val label: String) {
    CROP_ROTATE("Transform"),
    ADJUST("Adjust"),
    FILTERS("Filters"),
    SMART_OBJECT("Smart Object"),
    COPY_COLOR("Copy Color"),
    DRAW("Draw"),
    TEXT("Text")
}

@Composable
fun PhotoEditorScreen(
    photo: GalleryPhoto,
    onDismiss: () -> Unit,
    onSaveSuccess: (File, Boolean) -> Unit // (savedFile, replaceOriginal)
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var sourceBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var previewBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var isSaving by remember { mutableStateOf(false) }

    // History stack for Undo / Redo
    val history = remember { mutableStateListOf(EditorState()) }
    var historyIndex by remember { mutableIntStateOf(0) }
    val currentState = history.getOrNull(historyIndex) ?: EditorState()

    var selectedTab by remember { mutableStateOf(EditorTab.CROP_ROTATE) }
    var showSaveDialog by remember { mutableStateOf(false) }
    var showExitConfirm by remember { mutableStateOf(false) }

    // Drawing Tool State
    var currentDrawColor by remember { mutableStateOf(Color.White) }
    var currentStrokeWidth by remember { mutableStateOf(4f) }
    val currentPoints = remember { mutableStateListOf<DrawPoint>() }

    // Text Tool State
    var showAddTextDialog by remember { mutableStateOf(false) }
    var newTextString by remember { mutableStateOf("") }
    var newTextColor by remember { mutableStateOf(Color.Yellow) }

    // Smart Object Cutout & Layers State
    var isLassoMode by remember { mutableStateOf(false) }
    val lassoPoints = remember { mutableStateListOf<DrawPoint>() }
    var isExtractingCutout by remember { mutableStateOf(false) }
    var extractedCutoutBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var showCutoutPreviewDialog by remember { mutableStateOf(false) }
    var showRecolorDialog by remember { mutableStateOf(false) }
    var showLibraryDialog by remember { mutableStateOf(false) }
    var activeLayerIndex by remember { mutableIntStateOf(0) }

    // Copy Color Transfer State
    var isColorSamplingMode by remember { mutableStateOf(false) }
    var isColorTargetSelectionMode by remember { mutableStateOf(false) }
    val colorTargetPoints = remember { mutableStateListOf<DrawPoint>() }
    var showColorFineTuneDialog by remember { mutableStateOf(false) }
    var colorFineTuneInitialColor by remember { mutableIntStateOf(0) }
    var activeColorLayerIndex by remember { mutableIntStateOf(0) }
    var liveSampledColor by remember { mutableStateOf<Int?>(null) }
    var liveSampledOffset by remember { mutableStateOf<Offset?>(null) }

    var saveAsNewPhotoOption by remember { mutableStateOf(true) }
    var renderJob by remember { mutableStateOf<Job?>(null) }

    // Load initial bitmap
    LaunchedEffect(photo.localUri) {
        isLoading = true
        val uri = Uri.parse(photo.localUri)
        val bmp = PhotoEditorEngine.loadBitmapFromUri(context, uri)
        sourceBitmap = bmp
        previewBitmap = bmp
        isLoading = false
    }

    // Helper to push new state to history
    fun pushState(newState: EditorState) {
        // Truncate redo tree if we are mid-history
        while (history.size > historyIndex + 1) {
            history.removeAt(history.size - 1)
        }
        history.add(newState)
        historyIndex = history.size - 1
    }

    // Trigger rendering whenever currentState changes
    LaunchedEffect(currentState, sourceBitmap) {
        val src = sourceBitmap ?: return@LaunchedEffect
        renderJob?.cancel()
        renderJob = scope.launch {
            delay(50) // Debounce rapid slider gestures
            val rendered = PhotoEditorEngine.renderFinalBitmap(src, currentState)
            previewBitmap = rendered
        }
    }

    BackHandler {
        if (historyIndex > 0) {
            showExitConfirm = true
        } else {
            onDismiss()
        }
    }

    if (showExitConfirm) {
        AlertDialog(
            onDismissRequest = { showExitConfirm = false },
            containerColor = DarkSurface,
            title = { Text("Discard Edits?", color = TextPrimary, fontWeight = FontWeight.Bold) },
            text = { Text("You have unsaved edits. Are you sure you want to exit without saving?", color = TextSecondary) },
            confirmButton = {
                Button(
                    onClick = {
                        showExitConfirm = false
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = RoseSecondary),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Discard", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showExitConfirm = false }) {
                    Text("Keep Editing", color = TextSecondary)
                }
            }
        )
    }

    // Add Text Dialog
    if (showAddTextDialog) {
        AlertDialog(
            onDismissRequest = { showAddTextDialog = false },
            containerColor = DarkSurface,
            title = { Text("Add Text to Photo", color = TextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = newTextString,
                        onValueChange = { newTextString = it },
                        placeholder = { Text("Enter caption or text...") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text("Text Color:", color = TextSecondary, fontSize = 13.sp)
                    val colors = listOf(Color.White, Color.Yellow, Color.Red, Color.Cyan, Color.Green, Color.Magenta)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (col in colors) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(col)
                                    .border(
                                        width = if (newTextColor == col) 2.5.dp else 1.dp,
                                        color = if (newTextColor == col) IndigoPrimary else DarkBorder,
                                        shape = CircleShape
                                    )
                                    .clickable { newTextColor = col }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newTextString.isNotBlank()) {
                            val newSticker = TextSticker(
                                text = newTextString,
                                color = newTextColor.toArgb(),
                                textSize = 24f,
                                xNormalized = 0.5f,
                                yNormalized = 0.5f
                            )
                            pushState(currentState.copy(textStickers = currentState.textStickers + newSticker))
                            newTextString = ""
                            showAddTextDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = IndigoPrimary),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Add Text", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddTextDialog = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        )
    }

    // Save Choice Dialog
    if (showSaveDialog) {
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            containerColor = DarkSurface,
            title = { Text("Save Edited Photo", color = TextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "How would you like to save this photo?",
                        color = TextSecondary,
                        fontSize = 14.sp
                    )

                    // Option 1: Save as New Photo (Default, Recommended)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { saveAsNewPhotoOption = true }
                            .padding(vertical = 4.dp)
                    ) {
                        RadioButton(
                            selected = saveAsNewPhotoOption,
                            onClick = { saveAsNewPhotoOption = true },
                            colors = RadioButtonDefaults.colors(selectedColor = IndigoPrimary)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text("Save as New Photo", color = TextPrimary, fontWeight = FontWeight.SemiBold)
                            Text("Keeps the original intact and adds a copy to MyGallery", color = TextMuted, fontSize = 12.sp)
                        }
                    }

                    // Option 2: Replace in MyGallery
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { saveAsNewPhotoOption = false }
                            .padding(vertical = 4.dp)
                    ) {
                        RadioButton(
                            selected = !saveAsNewPhotoOption,
                            onClick = { saveAsNewPhotoOption = false },
                            colors = RadioButtonDefaults.colors(selectedColor = IndigoPrimary)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text("Replace in MyGallery", color = TextPrimary, fontWeight = FontWeight.SemiBold)
                            Text("Updates this photo in MyGallery (original phone gallery photo is still safe)", color = TextMuted, fontSize = 12.sp)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showSaveDialog = false
                        isSaving = true
                        val src = sourceBitmap
                        if (src != null) {
                            scope.launch {
                                val finalBmp = PhotoEditorEngine.renderFinalBitmap(src, currentState)
                                val dir = File(context.filesDir, "mygallery_photos").apply { if (!exists()) mkdirs() }
                                val targetFile = if (saveAsNewPhotoOption) {
                                    File(dir, "edited_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}.jpg")
                                } else {
                                    val uri = Uri.parse(photo.localUri)
                                    if (uri.scheme == "file" && uri.path != null) {
                                        File(uri.path!!)
                                    } else {
                                        File(dir, "edited_${photo.id}.jpg")
                                    }
                                }

                                val success = PhotoEditorEngine.saveBitmapToFile(context, finalBmp, targetFile)
                                isSaving = false
                                if (success) {
                                    onSaveSuccess(targetFile, !saveAsNewPhotoOption)
                                } else {
                                    Toast.makeText(context, "Failed to save photo", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = IndigoPrimary),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Save", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showSaveDialog = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        )
    }

    // Smart Object Cutout Preview Dialog
    if (showCutoutPreviewDialog && extractedCutoutBitmap != null) {
        SmartCutoutPreviewDialog(
            cutoutBitmap = extractedCutoutBitmap!!,
            onCopyAndDismiss = {
                val bmp = extractedCutoutBitmap!!
                val newLayer = CutoutLayer(
                    name = "Cutout ${CutoutClipboard.getRecentCutouts().size + 1}",
                    originalCutout = bmp,
                    displayBitmap = bmp
                )
                CutoutClipboard.copy(newLayer)
                showCutoutPreviewDialog = false
                isLassoMode = false
                Toast.makeText(context, "Cutout copied to clipboard! Open any photo and tap 'Paste Object'.", Toast.LENGTH_LONG).show()
            },
            onAddAsLayer = {
                val bmp = extractedCutoutBitmap!!
                val newLayer = CutoutLayer(
                    name = "Object ${currentState.cutoutLayers.size + 1}",
                    originalCutout = bmp,
                    displayBitmap = bmp,
                    xNormalized = 0.5f,
                    yNormalized = 0.5f
                )
                pushState(currentState.copy(cutoutLayers = currentState.cutoutLayers + newLayer))
                activeLayerIndex = currentState.cutoutLayers.size
                showCutoutPreviewDialog = false
                isLassoMode = false
                Toast.makeText(context, "Cutout added as a new layer!", Toast.LENGTH_SHORT).show()
            },
            onDismiss = {
                showCutoutPreviewDialog = false
            }
        )
    }

    // Smart Object Recolor Dialog
    if (showRecolorDialog) {
        val activeLayer = currentState.cutoutLayers.getOrNull(activeLayerIndex)
        if (activeLayer != null) {
            RecolorObjectDialog(
                currentSettings = activeLayer.recolor,
                originalCutout = activeLayer.originalCutout,
                onApplyRecolor = { newSettings, recoloredBmp ->
                    val updatedLayer = activeLayer.copy(
                        recolor = newSettings,
                        displayBitmap = recoloredBmp
                    )
                    val updatedList = currentState.cutoutLayers.toMutableList()
                    updatedList[activeLayerIndex] = updatedLayer
                    pushState(currentState.copy(cutoutLayers = updatedList))
                    showRecolorDialog = false
                    Toast.makeText(context, "Object recolored! Shading and details preserved.", Toast.LENGTH_SHORT).show()
                },
                onDismiss = { showRecolorDialog = false }
            )
        } else {
            showRecolorDialog = false
        }
    }

    // Cutout Library Dialog
    if (showLibraryDialog) {
        CutoutLibraryDialog(
            onSelectCutoutToPaste = { item ->
                val newLayer = item.copy(
                    id = UUID.randomUUID().toString(),
                    name = "Object ${currentState.cutoutLayers.size + 1}",
                    xNormalized = 0.5f,
                    yNormalized = 0.5f
                )
                pushState(currentState.copy(cutoutLayers = currentState.cutoutLayers + newLayer))
                activeLayerIndex = currentState.cutoutLayers.size
                showLibraryDialog = false
                Toast.makeText(context, "Cutout pasted onto photo!", Toast.LENGTH_SHORT).show()
            },
            onDismiss = { showLibraryDialog = false }
        )
    }

    // Sampled Color Fine-Tune Dialog (Hue, Saturation, Brightness adjustment)
    if (showColorFineTuneDialog) {
        val initialColor = if (colorFineTuneInitialColor != 0) {
            colorFineTuneInitialColor
        } else {
            ColorClipboard.copiedColor ?: android.graphics.Color.BLUE
        }
        SampledColorFineTuneDialog(
            initialColor = initialColor,
            onSaveCopiedColor = { savedColor ->
                ColorClipboard.copyColor(savedColor)
                showColorFineTuneDialog = false
                Toast.makeText(context, "Copied Color saved! Open any photo and tap 'Apply Color'.", Toast.LENGTH_SHORT).show()
            },
            onDismiss = { showColorFineTuneDialog = false }
        )
    }

    Scaffold(
        containerColor = DarkBackground,
        topBar = {
            // Editor Top Bar with Undo / Redo and Save Action
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = {
                    if (historyIndex > 0) showExitConfirm = true else onDismiss()
                }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = TextPrimary
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Undo Button
                    IconButton(
                        onClick = {
                            if (historyIndex > 0) historyIndex--
                        },
                        enabled = historyIndex > 0
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Undo,
                            contentDescription = "Undo",
                            tint = if (historyIndex > 0) TextPrimary else TextMuted
                        )
                    }

                    // Redo Button
                    IconButton(
                        onClick = {
                            if (historyIndex < history.size - 1) historyIndex++
                        },
                        enabled = historyIndex < history.size - 1
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Redo,
                            contentDescription = "Redo",
                            tint = if (historyIndex < history.size - 1) TextPrimary else TextMuted
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // Save Button
                    Button(
                        onClick = { showSaveDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = IndigoPrimary),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.testTag("editor_save_button")
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                        } else {
                            Icon(imageVector = Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Save", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Main Canvas / Preview Area
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                if (isLoading) {
                    CircularProgressIndicator(color = IndigoPrimary)
                } else if (previewBitmap != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(selectedTab, isLassoMode, activeLayerIndex, currentState.cutoutLayers.size, isColorSamplingMode, isColorTargetSelectionMode, currentState.colorRecolorLayers.size) {
                                if (selectedTab == EditorTab.DRAW) {
                                    detectDragGestures(
                                        onDragStart = { offset ->
                                            currentPoints.clear()
                                            currentPoints.add(DrawPoint(offset.x / size.width, offset.y / size.height))
                                        },
                                        onDrag = { change, _ ->
                                            change.consume()
                                            currentPoints.add(DrawPoint(change.position.x / size.width, change.position.y / size.height))
                                        },
                                        onDragEnd = {
                                            if (currentPoints.size > 1) {
                                                val newStroke = DrawStroke(
                                                    points = currentPoints.toList(),
                                                    color = currentDrawColor.toArgb(),
                                                    strokeWidth = currentStrokeWidth
                                                )
                                                pushState(currentState.copy(strokes = currentState.strokes + newStroke))
                                            }
                                            currentPoints.clear()
                                        }
                                    )
                                } else if (selectedTab == EditorTab.SMART_OBJECT) {
                                    if (isLassoMode) {
                                        detectDragGestures(
                                            onDragStart = { offset ->
                                                lassoPoints.clear()
                                                lassoPoints.add(DrawPoint(offset.x / size.width, offset.y / size.height))
                                            },
                                            onDrag = { change, _ ->
                                                change.consume()
                                                lassoPoints.add(DrawPoint(change.position.x / size.width, change.position.y / size.height))
                                            },
                                            onDragEnd = {
                                                if (lassoPoints.size >= 5) {
                                                    val pts = lassoPoints.toList()
                                                    val src = sourceBitmap
                                                    if (src != null) {
                                                        scope.launch {
                                                            isExtractingCutout = true
                                                            val cutout = SmartObjectEngine.extractSmartCutout(
                                                                sourceBitmap = src,
                                                                encirclePoints = pts,
                                                                viewWidth = size.width.toFloat(),
                                                                viewHeight = size.height.toFloat(),
                                                                edgeFeather = 2.5f
                                                            )
                                                            isExtractingCutout = false
                                                            if (cutout != null) {
                                                                extractedCutoutBitmap = cutout
                                                                showCutoutPreviewDialog = true
                                                            } else {
                                                                Toast.makeText(context, "Could not detect object inside selection. Please draw closely around the subject.", Toast.LENGTH_SHORT).show()
                                                            }
                                                            lassoPoints.clear()
                                                        }
                                                    }
                                                } else {
                                                    lassoPoints.clear()
                                                }
                                            }
                                        )
                                    } else if (currentState.cutoutLayers.isNotEmpty()) {
                                        detectDragGestures(
                                            onDrag = { change, dragAmount ->
                                                val idx = activeLayerIndex
                                                val layer = currentState.cutoutLayers.getOrNull(idx)
                                                if (layer != null) {
                                                    change.consume()
                                                    val deltaX = dragAmount.x / size.width
                                                    val deltaY = dragAmount.y / size.height
                                                    val updated = layer.copy(
                                                        xNormalized = (layer.xNormalized + deltaX).coerceIn(0.05f, 0.95f),
                                                        yNormalized = (layer.yNormalized + deltaY).coerceIn(0.05f, 0.95f)
                                                    )
                                                    val list = currentState.cutoutLayers.toMutableList()
                                                    list[idx] = updated
                                                    pushState(currentState.copy(cutoutLayers = list))
                                                }
                                            }
                                        )
                                    }
                                } else if (selectedTab == EditorTab.COPY_COLOR) {
                                    if (isColorSamplingMode) {
                                        detectDragGestures(
                                            onDragStart = { offset ->
                                                val src = sourceBitmap
                                                if (src != null) {
                                                    val xNorm = (offset.x / size.width).coerceIn(0f, 1f)
                                                    val yNorm = (offset.y / size.height).coerceIn(0f, 1f)
                                                    val sampled = ColorTransferEngine.sampleColorAt(src, xNorm, yNorm)
                                                    liveSampledColor = sampled
                                                    liveSampledOffset = offset
                                                }
                                            },
                                            onDrag = { change, _ ->
                                                change.consume()
                                                val src = sourceBitmap
                                                if (src != null) {
                                                    val xNorm = (change.position.x / size.width).coerceIn(0f, 1f)
                                                    val yNorm = (change.position.y / size.height).coerceIn(0f, 1f)
                                                    val sampled = ColorTransferEngine.sampleColorAt(src, xNorm, yNorm)
                                                    liveSampledColor = sampled
                                                    liveSampledOffset = change.position
                                                }
                                            },
                                            onDragEnd = {
                                                val sampled = liveSampledColor
                                                if (sampled != null) {
                                                    ColorClipboard.copyColor(sampled)
                                                    colorFineTuneInitialColor = sampled
                                                    isColorSamplingMode = false
                                                    showColorFineTuneDialog = true
                                                    Toast.makeText(context, "Color copied! Fine-tune or apply to another photo.", Toast.LENGTH_SHORT).show()
                                                }
                                                liveSampledColor = null
                                                liveSampledOffset = null
                                            },
                                            onDragCancel = {
                                                liveSampledColor = null
                                                liveSampledOffset = null
                                            }
                                        )
                                    } else if (isColorTargetSelectionMode) {
                                        detectDragGestures(
                                            onDragStart = { offset ->
                                                colorTargetPoints.clear()
                                                colorTargetPoints.add(DrawPoint(offset.x / size.width, offset.y / size.height))
                                            },
                                            onDrag = { change, _ ->
                                                change.consume()
                                                colorTargetPoints.add(DrawPoint(change.position.x / size.width, change.position.y / size.height))
                                            },
                                            onDragEnd = {
                                                if (colorTargetPoints.size >= 5) {
                                                    val copied = ColorClipboard.copiedColor
                                                    if (copied != null) {
                                                        val newLayer = ColorRecolorLayer(
                                                            id = UUID.randomUUID().toString(),
                                                            name = "Recolor ${currentState.colorRecolorLayers.size + 1}",
                                                            color = copied,
                                                            strength = 0.85f,
                                                            feather = 5.0f,
                                                            selectionPoints = colorTargetPoints.toList()
                                                        )
                                                        val updated = currentState.colorRecolorLayers + newLayer
                                                        pushState(currentState.copy(colorRecolorLayers = updated))
                                                        activeColorLayerIndex = updated.size - 1
                                                        isColorTargetSelectionMode = false
                                                        Toast.makeText(context, "Color applied to object! Preserving shadows & texture.", Toast.LENGTH_SHORT).show()
                                                    } else {
                                                        Toast.makeText(context, "No color copied yet. Sample a color first.", Toast.LENGTH_SHORT).show()
                                                    }
                                                } else {
                                                    Toast.makeText(context, "Encircle the object you want to recolor.", Toast.LENGTH_SHORT).show()
                                                }
                                                colorTargetPoints.clear()
                                            }
                                        )
                                    }
                                }
                            }
                    ) {
                        androidx.compose.foundation.Image(
                            bitmap = previewBitmap!!.asImageBitmap(),
                            contentDescription = "Editor Preview",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )

                        // Live Draw Stroke overlay while finger is down
                        if (currentPoints.size > 1) {
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                val p = androidx.compose.ui.graphics.Path()
                                val first = currentPoints.first()
                                p.moveTo(first.x * size.width, first.y * size.height)
                                for (pt in currentPoints.drop(1)) {
                                    p.lineTo(pt.x * size.width, pt.y * size.height)
                                }
                                drawPath(
                                    path = p,
                                    color = currentDrawColor,
                                    style = androidx.compose.ui.graphics.drawscope.Stroke(
                                        width = currentStrokeWidth * (size.width / 400f)
                                    )
                                )
                            }
                        }

                        // Live Smart Lasso outline while encircling
                        if (lassoPoints.size > 1) {
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                val p = androidx.compose.ui.graphics.Path()
                                val first = lassoPoints.first()
                                p.moveTo(first.x * size.width, first.y * size.height)
                                for (pt in lassoPoints.drop(1)) {
                                    p.lineTo(pt.x * size.width, pt.y * size.height)
                                }
                                drawPath(
                                    path = p,
                                    color = CyanTertiary,
                                    style = androidx.compose.ui.graphics.drawscope.Stroke(
                                        width = 3.dp.toPx(),
                                        pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(16f, 16f), 0f)
                                    )
                                )
                            }
                        }

                        // Active Layer Selection Bounding Box
                        if (selectedTab == EditorTab.SMART_OBJECT && !isLassoMode && currentState.cutoutLayers.isNotEmpty()) {
                            val activeLayer = currentState.cutoutLayers.getOrNull(activeLayerIndex)
                            if (activeLayer != null && activeLayer.isVisible) {
                                Canvas(modifier = Modifier.fillMaxSize()) {
                                    val cx = activeLayer.xNormalized * size.width
                                    val cy = activeLayer.yNormalized * size.height
                                    val boxW = (size.width * 0.4f * activeLayer.scale).coerceAtLeast(40f)
                                    val boxH = (size.height * 0.4f * activeLayer.scale).coerceAtLeast(40f)
                                    drawRect(
                                        color = CyanTertiary.copy(alpha = 0.8f),
                                        topLeft = androidx.compose.ui.geometry.Offset(cx - boxW / 2, cy - boxH / 2),
                                        size = androidx.compose.ui.geometry.Size(boxW, boxH),
                                        style = androidx.compose.ui.graphics.drawscope.Stroke(
                                            width = 1.8.dp.toPx(),
                                            pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(12f, 12f), 0f)
                                        )
                                    )
                                }
                            }
                        }

                        // Top Banner when in Lasso Encircle Mode
                        if (isLassoMode) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .padding(top = 12.dp)
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(Color.Black.copy(alpha = 0.85f))
                                    .border(1.dp, CyanTertiary, RoundedCornerShape(20.dp))
                                    .padding(horizontal = 14.dp, vertical = 6.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(imageVector = Icons.Default.AutoFixHigh, contentDescription = null, tint = CyanTertiary, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Encircle an object to cutout", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = "Cancel",
                                        color = RoseSecondary,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.clickable {
                                            isLassoMode = false
                                            lassoPoints.clear()
                                        }
                                    )
                                }
                            }
                        }

                        // Extraction Progress Indicator
                        if (isExtractingCutout) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(DarkSurface.copy(alpha = 0.95f))
                                    .border(1.dp, CyanTertiary, RoundedCornerShape(16.dp))
                                    .padding(20.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    CircularProgressIndicator(color = CyanTertiary, modifier = Modifier.size(32.dp))
                                    Text("Extracting smart cutout...", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        // Live Target Encircle outline while selecting object for color application
                        if (colorTargetPoints.size > 1) {
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                val p = androidx.compose.ui.graphics.Path()
                                val first = colorTargetPoints.first()
                                p.moveTo(first.x * size.width, first.y * size.height)
                                for (pt in colorTargetPoints.drop(1)) {
                                    p.lineTo(pt.x * size.width, pt.y * size.height)
                                }
                                drawPath(
                                    path = p,
                                    color = EmeraldSuccess,
                                    style = androidx.compose.ui.graphics.drawscope.Stroke(
                                        width = 3.dp.toPx(),
                                        pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(16f, 16f), 0f)
                                    )
                                )
                            }
                        }

                        // Top Banner when in Color Eyedropper Sampling Mode
                        if (isColorSamplingMode) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .padding(top = 12.dp)
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(Color.Black.copy(alpha = 0.85f))
                                    .border(1.dp, CyanTertiary, RoundedCornerShape(20.dp))
                                    .padding(horizontal = 14.dp, vertical = 6.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(imageVector = Icons.Default.Colorize, contentDescription = null, tint = CyanTertiary, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Tap or drag to sample color", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = "Cancel",
                                        color = RoseSecondary,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.clickable {
                                            isColorSamplingMode = false
                                            liveSampledColor = null
                                            liveSampledOffset = null
                                        }
                                    )
                                }
                            }
                        }

                        // Top Banner when in Color Target Selection Mode
                        if (isColorTargetSelectionMode) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .padding(top = 12.dp)
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(Color.Black.copy(alpha = 0.85f))
                                    .border(1.dp, EmeraldSuccess, RoundedCornerShape(20.dp))
                                    .padding(horizontal = 14.dp, vertical = 6.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(imageVector = Icons.Default.FormatPaint, contentDescription = null, tint = EmeraldSuccess, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Encircle object to apply copied color", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = "Cancel",
                                        color = RoseSecondary,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.clickable {
                                            isColorTargetSelectionMode = false
                                            colorTargetPoints.clear()
                                        }
                                    )
                                }
                            }
                        }

                        // Magnifier Loupe Overlay during Color Sampling
                        if (isColorSamplingMode && liveSampledOffset != null && liveSampledColor != null) {
                            LoupeOverlay(
                                touchOffset = liveSampledOffset!!,
                                sampledColor = liveSampledColor!!
                            )
                        }
                    }
                }
            }

            // Controls & Tools Panel
            Surface(
                color = DarkSurface,
                border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(vertical = 12.dp)) {
                    // Tool Tabs (Scrollable for compact screens)
                    ScrollableTabRow(
                        selectedTabIndex = selectedTab.ordinal,
                        edgePadding = 8.dp,
                        containerColor = DarkSurface,
                        contentColor = IndigoPrimary,
                        indicator = { tabPositions ->
                            TabRowDefaults.SecondaryIndicator(
                                Modifier.tabIndicatorOffset(tabPositions[selectedTab.ordinal]),
                                color = IndigoPrimary
                            )
                        }
                    ) {
                        EditorTab.values().forEach { tab ->
                            Tab(
                                selected = selectedTab == tab,
                                onClick = { selectedTab = tab },
                                text = {
                                    Text(
                                        text = tab.label,
                                        fontWeight = if (selectedTab == tab) FontWeight.Bold else FontWeight.Normal,
                                        fontSize = 12.sp,
                                        color = if (selectedTab == tab) IndigoPrimary else TextMuted
                                    )
                                }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Tab Specific Controls
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                    ) {
                        when (selectedTab) {
                            EditorTab.CROP_ROTATE -> CropRotateControls(
                                state = currentState,
                                onRotate = {
                                    val nextRotation = (currentState.rotationDegrees + 90) % 360
                                    pushState(currentState.copy(rotationDegrees = nextRotation))
                                },
                                onFlipHorizontal = {
                                    pushState(currentState.copy(isFlippedHorizontally = !currentState.isFlippedHorizontally))
                                },
                                onFlipVertical = {
                                    pushState(currentState.copy(isFlippedVertically = !currentState.isFlippedVertically))
                                },
                                onCropPreset = { preset ->
                                    val crop = when (preset) {
                                        "1:1" -> listOf(0.1f, 0.1f, 0.9f, 0.9f)
                                        "4:3" -> listOf(0.05f, 0.1f, 0.95f, 0.775f)
                                        "16:9" -> listOf(0.05f, 0.2f, 0.95f, 0.706f)
                                        else -> null
                                    }
                                    pushState(currentState.copy(cropRectNormalized = crop))
                                }
                            )

                            EditorTab.ADJUST -> AdjustControls(
                                state = currentState,
                                onBrightnessChange = { pushState(currentState.copy(brightness = it)) },
                                onContrastChange = { pushState(currentState.copy(contrast = it)) },
                                onSaturationChange = { pushState(currentState.copy(saturation = it)) },
                                onBlurChange = { pushState(currentState.copy(blurRadius = it)) }
                            )

                            EditorTab.FILTERS -> FilterControls(
                                currentFilter = currentState.activeFilter,
                                onSelectFilter = { filter ->
                                    pushState(currentState.copy(activeFilter = filter))
                                }
                            )

                            EditorTab.SMART_OBJECT -> SmartObjectControls(
                                layers = currentState.cutoutLayers,
                                activeLayerIndex = activeLayerIndex.coerceIn(0, maxOf(0, currentState.cutoutLayers.size - 1)),
                                isLassoMode = isLassoMode,
                                onToggleLassoMode = {
                                    isLassoMode = !isLassoMode
                                    if (isLassoMode) {
                                        lassoPoints.clear()
                                        Toast.makeText(context, "Draw around the object you want to cutout", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                onSelectLayer = { activeLayerIndex = it },
                                onPasteCopiedObject = {
                                    val copied = CutoutClipboard.copiedObject
                                    if (copied != null) {
                                        val newLayer = copied.copy(
                                            id = UUID.randomUUID().toString(),
                                            name = "Object ${currentState.cutoutLayers.size + 1}",
                                            xNormalized = 0.5f,
                                            yNormalized = 0.5f
                                        )
                                        pushState(currentState.copy(cutoutLayers = currentState.cutoutLayers + newLayer))
                                        activeLayerIndex = currentState.cutoutLayers.size
                                    }
                                },
                                onUpdateActiveLayer = { updated ->
                                    val idx = activeLayerIndex
                                    if (idx in currentState.cutoutLayers.indices) {
                                        val updatedList = currentState.cutoutLayers.toMutableList()
                                        updatedList[idx] = updated
                                        pushState(currentState.copy(cutoutLayers = updatedList))
                                    }
                                },
                                onDuplicateLayer = { idx ->
                                    if (idx in currentState.cutoutLayers.indices) {
                                        val orig = currentState.cutoutLayers[idx]
                                        val dup = orig.copy(
                                            id = UUID.randomUUID().toString(),
                                            name = "${orig.name} (Copy)",
                                            xNormalized = (orig.xNormalized + 0.05f).coerceIn(0.1f, 0.9f),
                                            yNormalized = (orig.yNormalized + 0.05f).coerceIn(0.1f, 0.9f)
                                        )
                                        pushState(currentState.copy(cutoutLayers = currentState.cutoutLayers + dup))
                                        activeLayerIndex = currentState.cutoutLayers.size
                                        Toast.makeText(context, "Layer duplicated", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                onDeleteLayer = { idx ->
                                    if (idx in currentState.cutoutLayers.indices) {
                                        val updated = currentState.cutoutLayers.toMutableList()
                                        updated.removeAt(idx)
                                        pushState(currentState.copy(cutoutLayers = updated))
                                        activeLayerIndex = maxOf(0, updated.size - 1)
                                        Toast.makeText(context, "Layer deleted", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                onReorderLayer = { from, to ->
                                    if (from in currentState.cutoutLayers.indices && to in currentState.cutoutLayers.indices) {
                                        val updated = currentState.cutoutLayers.toMutableList()
                                        val item = updated.removeAt(from)
                                        updated.add(to, item)
                                        pushState(currentState.copy(cutoutLayers = updated))
                                        activeLayerIndex = to
                                    }
                                },
                                onOpenRecolorDialog = { showRecolorDialog = true },
                                onOpenLibraryDialog = { showLibraryDialog = true }
                            )

                            EditorTab.COPY_COLOR -> ColorTransferControls(
                                isSamplingMode = isColorSamplingMode,
                                isTargetSelectionMode = isColorTargetSelectionMode,
                                recolorLayers = currentState.colorRecolorLayers,
                                activeLayerIndex = activeColorLayerIndex.coerceIn(0, maxOf(0, currentState.colorRecolorLayers.size - 1)),
                                onToggleSamplingMode = {
                                    isColorSamplingMode = !isColorSamplingMode
                                    if (isColorSamplingMode) {
                                        isColorTargetSelectionMode = false
                                        Toast.makeText(context, "Tap any point on the photo to copy its color", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                onToggleTargetSelectionMode = {
                                    isColorTargetSelectionMode = !isColorTargetSelectionMode
                                    if (isColorTargetSelectionMode) {
                                        isColorSamplingMode = false
                                        colorTargetPoints.clear()
                                        Toast.makeText(context, "Encircle the object you want to recolor", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                onOpenFineTuneDialog = {
                                    colorFineTuneInitialColor = ColorClipboard.copiedColor ?: android.graphics.Color.BLUE
                                    showColorFineTuneDialog = true
                                },
                                onUpdateLayer = { updated ->
                                    val idx = activeColorLayerIndex
                                    if (idx in currentState.colorRecolorLayers.indices) {
                                        val list = currentState.colorRecolorLayers.toMutableList()
                                        list[idx] = updated
                                        pushState(currentState.copy(colorRecolorLayers = list))
                                    }
                                },
                                onDeleteLayer = { idx ->
                                    if (idx in currentState.colorRecolorLayers.indices) {
                                        val list = currentState.colorRecolorLayers.toMutableList()
                                        list.removeAt(idx)
                                        pushState(currentState.copy(colorRecolorLayers = list))
                                        activeColorLayerIndex = maxOf(0, list.size - 1)
                                        Toast.makeText(context, "Recolor layer removed", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            )

                            EditorTab.DRAW -> DrawControls(
                                currentColor = currentDrawColor,
                                onColorChange = { currentDrawColor = it },
                                currentWidth = currentStrokeWidth,
                                onWidthChange = { currentStrokeWidth = it },
                                onClearStrokes = { pushState(currentState.copy(strokes = emptyList())) }
                            )

                            EditorTab.TEXT -> TextControls(
                                stickerCount = currentState.textStickers.size,
                                onAddTextClick = { showAddTextDialog = true },
                                onClearText = { pushState(currentState.copy(textStickers = emptyList())) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CropRotateControls(
    state: EditorState,
    onRotate: () -> Unit,
    onFlipHorizontal: () -> Unit,
    onFlipVertical: () -> Unit,
    onCropPreset: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Rotate & Flip Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            ToolButton(icon = Icons.Default.RotateRight, label = "Rotate 90°", onClick = onRotate)
            ToolButton(icon = Icons.Default.Flip, label = "Flip Horiz", onClick = onFlipHorizontal)
            ToolButton(icon = Icons.Default.Flip, label = "Flip Vert", onClick = onFlipVertical)
        }

        // Crop Presets
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Crop Aspect:", color = TextSecondary, fontSize = 12.sp)
            listOf("Free", "1:1", "4:3", "16:9").forEach { preset ->
                OutlinedButton(
                    onClick = { onCropPreset(preset) },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(containerColor = DarkSurfaceVariant)
                ) {
                    Text(preset, fontSize = 11.sp, color = TextPrimary)
                }
            }
        }
    }
}

@Composable
fun AdjustControls(
    state: EditorState,
    onBrightnessChange: (Float) -> Unit,
    onContrastChange: (Float) -> Unit,
    onSaturationChange: (Float) -> Unit,
    onBlurChange: (Int) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SliderRow(label = "Brightness", value = state.brightness, range = -100f..100f, onValueChange = onBrightnessChange)
        SliderRow(label = "Contrast", value = state.contrast, range = -100f..100f, onValueChange = onContrastChange)
        SliderRow(label = "Saturation", value = state.saturation, range = -100f..100f, onValueChange = onSaturationChange)
        SliderRow(label = "Blur", value = state.blurRadius.toFloat(), range = 0f..25f, onValueChange = { onBlurChange(it.toInt()) })
    }
}

@Composable
fun SliderRow(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, color = TextSecondary, fontSize = 12.sp, modifier = Modifier.width(80.dp))
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            modifier = Modifier.weight(1f),
            colors = SliderDefaults.colors(
                thumbColor = IndigoPrimary,
                activeTrackColor = IndigoPrimary,
                inactiveTrackColor = DarkSurfaceVariant
            )
        )
        Text(
            text = value.toInt().toString(),
            color = TextMuted,
            fontSize = 11.sp,
            modifier = Modifier.width(36.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.End
        )
    }
}

@Composable
fun FilterControls(
    currentFilter: FilterPreset,
    onSelectFilter: (FilterPreset) -> Unit
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        items(FilterPreset.values()) { preset ->
            val isSelected = currentFilter == preset
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onSelectFilter(preset) }
                    .padding(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            when (preset) {
                                FilterPreset.ORIGINAL -> DarkSurfaceVariant
                                FilterPreset.VIVID -> Color(0xFFFF5722)
                                FilterPreset.WARM -> Color(0xFFFF9800)
                                FilterPreset.COOL -> Color(0xFF00BCD4)
                                FilterPreset.VINTAGE -> Color(0xFF795548)
                                FilterPreset.MONOCHROME -> Color(0xFF607D8B)
                                FilterPreset.SEPIA -> Color(0xFF8D6E63)
                                FilterPreset.DRAMATIC -> Color(0xFF263238)
                            }
                        )
                        .border(
                            width = if (isSelected) 2.5.dp else 1.dp,
                            color = if (isSelected) IndigoPrimary else DarkBorder,
                            shape = RoundedCornerShape(12.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (isSelected) {
                        Icon(imageVector = Icons.Default.Check, contentDescription = null, tint = Color.White)
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = preset.displayName,
                    fontSize = 11.sp,
                    color = if (isSelected) IndigoPrimary else TextSecondary,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                )
            }
        }
    }
}

@Composable
fun DrawControls(
    currentColor: Color,
    onColorChange: (Color) -> Unit,
    currentWidth: Float,
    onWidthChange: (Float) -> Unit,
    onClearStrokes: () -> Unit
) {
    val colors = listOf(Color.White, Color.Yellow, Color(0xFFFF5722), Color.Red, Color(0xFFE91E63), Color.Cyan, Color.Green, Color.Black)

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(colors) { col ->
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(col)
                            .border(
                                width = if (currentColor == col) 2.5.dp else 1.dp,
                                color = if (currentColor == col) IndigoPrimary else DarkBorder,
                                shape = CircleShape
                            )
                            .clickable { onColorChange(col) }
                    )
                }
            }

            TextButton(onClick = onClearStrokes) {
                Text("Clear", color = RoseSecondary, fontSize = 12.sp)
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Brush Size:", color = TextSecondary, fontSize = 12.sp)
            Spacer(modifier = Modifier.width(8.dp))
            listOf(2f to "Fine", 5f to "Med", 10f to "Bold").forEach { (width, label) ->
                OutlinedButton(
                    onClick = { onWidthChange(width) },
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = if (currentWidth == width) IndigoPrimary else DarkSurfaceVariant
                    ),
                    modifier = Modifier.padding(horizontal = 4.dp)
                ) {
                    Text(label, fontSize = 11.sp, color = if (currentWidth == width) Color.White else TextPrimary)
                }
            }
        }
    }
}

@Composable
fun TextControls(
    stickerCount: Int,
    onAddTextClick: () -> Unit,
    onClearText: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Button(
            onClick = onAddTextClick,
            colors = ButtonDefaults.buttonColors(containerColor = IndigoPrimary),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(imageVector = Icons.Default.TextFields, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Add Caption / Text", fontWeight = FontWeight.Bold)
        }

        if (stickerCount > 0) {
            TextButton(onClick = onClearText) {
                Text("Clear Text ($stickerCount)", color = RoseSecondary, fontSize = 12.sp)
            }
        }
    }
}

@Composable
fun ToolButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(DarkSurfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(imageVector = icon, contentDescription = label, tint = TextPrimary, modifier = Modifier.size(20.dp))
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(label, color = TextSecondary, fontSize = 11.sp)
    }
}
