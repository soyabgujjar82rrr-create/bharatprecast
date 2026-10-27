package com.example.ui.editor

import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.screens.SliderRow
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
import kotlinx.coroutines.launch

@Composable
fun SmartObjectControls(
    layers: List<CutoutLayer>,
    activeLayerIndex: Int,
    isLassoMode: Boolean,
    onToggleLassoMode: () -> Unit,
    onSelectLayer: (Int) -> Unit,
    onPasteCopiedObject: () -> Unit,
    onUpdateActiveLayer: (CutoutLayer) -> Unit,
    onDuplicateLayer: (Int) -> Unit,
    onDeleteLayer: (Int) -> Unit,
    onReorderLayer: (fromIndex: Int, toIndex: Int) -> Unit,
    onOpenRecolorDialog: () -> Unit,
    onOpenLibraryDialog: () -> Unit
) {
    val context = LocalContext.current
    val activeLayer = layers.getOrNull(activeLayerIndex)
    val hasClipboard = CutoutClipboard.hasClipboard()

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // Row 1: Primary Action Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Smart Select / Lasso Button
            Button(
                onClick = onToggleLassoMode,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isLassoMode) CyanTertiary else DarkSurfaceVariant,
                    contentColor = if (isLassoMode) Color.Black else TextPrimary
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .weight(1f)
                    .testTag("smart_select_lasso_button")
            ) {
                Icon(
                    imageVector = Icons.Default.AutoFixHigh,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (isLassoMode) "Encircle Mode ON" else "Smart Cutout",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // Paste Object Button (Enabled if clipboard has a cutout)
            Button(
                onClick = {
                    if (hasClipboard) {
                        onPasteCopiedObject()
                        Toast.makeText(context, "Object pasted!", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "No object copied yet. Use 'Smart Cutout' on any photo first.", Toast.LENGTH_SHORT).show()
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (hasClipboard) IndigoPrimary else DarkSurfaceVariant.copy(alpha = 0.6f),
                    contentColor = if (hasClipboard) Color.White else TextMuted
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .weight(1f)
                    .testTag("paste_object_button")
            ) {
                Icon(
                    imageVector = Icons.Default.ContentPaste,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Paste Object",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // Library Button
            OutlinedButton(
                onClick = onOpenLibraryDialog,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(containerColor = DarkSurfaceVariant)
            ) {
                Icon(imageVector = Icons.Default.Layers, contentDescription = null, tint = CyanTertiary, modifier = Modifier.size(16.dp))
            }
        }

        // Row 2: Layer Tabs / Chips (If there are layers)
        if (layers.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "LAYERS (${layers.size})",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextMuted,
                    letterSpacing = 1.sp
                )

                if (activeLayer != null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        // Copy Layer to Clipboard
                        IconButton(
                            onClick = {
                                CutoutClipboard.copy(activeLayer)
                                Toast.makeText(context, "Object copied to clipboard!", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(imageVector = Icons.Default.ContentCopy, contentDescription = "Copy", tint = CyanTertiary, modifier = Modifier.size(16.dp))
                        }

                        // Duplicate Layer
                        IconButton(
                            onClick = { onDuplicateLayer(activeLayerIndex) },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Add, contentDescription = "Duplicate", tint = EmeraldSuccess, modifier = Modifier.size(16.dp))
                        }

                        // Toggle Visibility
                        IconButton(
                            onClick = { onUpdateActiveLayer(activeLayer.copy(isVisible = !activeLayer.isVisible)) },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = if (activeLayer.isVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                contentDescription = "Visibility",
                                tint = if (activeLayer.isVisible) TextPrimary else TextMuted,
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        // Delete Layer
                        IconButton(
                            onClick = { onDeleteLayer(activeLayerIndex) },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete", tint = RoseSecondary, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }

            // Horizontal Layer list
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                itemsIndexed(layers) { index, layer ->
                    val isSelected = index == activeLayerIndex
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isSelected) IndigoPrimary.copy(alpha = 0.35f) else DarkSurfaceVariant)
                            .border(
                                width = if (isSelected) 1.5.dp else 1.dp,
                                color = if (isSelected) IndigoPrimary else DarkBorder,
                                shape = RoundedCornerShape(10.dp)
                            )
                            .clickable { onSelectLayer(index) }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color.Black),
                            contentAlignment = Alignment.Center
                        ) {
                            androidx.compose.foundation.Image(
                                bitmap = layer.displayBitmap.asImageBitmap(),
                                contentDescription = null,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Obj ${index + 1}",
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) Color.White else TextSecondary
                        )
                    }
                }
            }

            // Row 3: Active Layer Controls (Scale, Rotate, Flip, Recolor, Feather)
            if (activeLayer != null) {
                HorizontalDivider(color = DarkBorder, modifier = Modifier.padding(vertical = 2.dp))

                // Transform Sliders: Scale & Rotation
                SliderRow(
                    label = "Scale",
                    value = activeLayer.scale * 100f,
                    range = 20f..250f,
                    onValueChange = { onUpdateActiveLayer(activeLayer.copy(scale = it / 100f)) }
                )

                SliderRow(
                    label = "Rotation",
                    value = activeLayer.rotationDegrees,
                    range = 0f..360f,
                    onValueChange = { onUpdateActiveLayer(activeLayer.copy(rotationDegrees = it)) }
                )

                SliderRow(
                    label = "Feather",
                    value = activeLayer.featherRadius,
                    range = 0f..10f,
                    onValueChange = { onUpdateActiveLayer(activeLayer.copy(featherRadius = it)) }
                )

                // Quick buttons: Recolor Object, Flip Horiz, Rotate 90°
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = onOpenRecolorDialog,
                        colors = ButtonDefaults.buttonColors(containerColor = CyanTertiary),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("recolor_object_button")
                    ) {
                        Icon(imageVector = Icons.Default.ColorLens, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Recolor Object", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }

                    OutlinedButton(
                        onClick = {
                            onUpdateActiveLayer(activeLayer.copy(isFlippedHorizontally = !activeLayer.isFlippedHorizontally))
                        },
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.outlinedButtonColors(containerColor = DarkSurfaceVariant)
                    ) {
                        Icon(imageVector = Icons.Default.Flip, contentDescription = "Flip", tint = TextPrimary, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Flip", fontSize = 11.sp, color = TextPrimary)
                    }

                    OutlinedButton(
                        onClick = {
                            val newRot = (activeLayer.rotationDegrees + 45f) % 360f
                            onUpdateActiveLayer(activeLayer.copy(rotationDegrees = newRot))
                        },
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.outlinedButtonColors(containerColor = DarkSurfaceVariant)
                    ) {
                        Icon(imageVector = Icons.Default.RotateRight, contentDescription = "+45°", tint = TextPrimary, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("+45°", fontSize = 11.sp, color = TextPrimary)
                    }
                }
            }
        } else if (!isLassoMode) {
            // Hint view when no layers and not drawing
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Tap 'Smart Cutout' and encircle any object or person on the photo.",
                    color = TextMuted,
                    fontSize = 12.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
    }
}

/**
 * Dialog shown immediately after encircling an object, giving the user options:
 * - Copy Cutout (to paste in Photo 2)
 * - Add as Layer Here
 * - Discard
 */
@Composable
fun SmartCutoutPreviewDialog(
    cutoutBitmap: Bitmap,
    onCopyAndDismiss: () -> Unit,
    onAddAsLayer: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkSurface,
        title = {
            Text("Object Detected!", color = TextPrimary, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Clean cutout extracted with transparent background.",
                    color = TextSecondary,
                    fontSize = 13.sp
                )

                // Checkerboard preview background to clearly show transparency
                Box(
                    modifier = Modifier
                        .size(180.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF222222))
                        .border(1.dp, IndigoPrimary.copy(alpha = 0.5f), RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    androidx.compose.foundation.Image(
                        bitmap = cutoutBitmap.asImageBitmap(),
                        contentDescription = "Cutout Preview",
                        modifier = Modifier.size(160.dp)
                    )
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onCopyAndDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = CyanTertiary),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(imageVector = Icons.Default.ContentCopy, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Copy Object", color = Color.Black, fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = onAddAsLayer,
                    colors = ButtonDefaults.buttonColors(containerColor = IndigoPrimary),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(imageVector = Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Add Here", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Discard", color = TextMuted)
            }
        }
    )
}

/**
 * Realistic Recolor Dialog:
 * Preserves realistic shading, textures, highlights, and wrinkles while altering object color.
 */
@Composable
fun RecolorObjectDialog(
    currentSettings: ObjectRecolorSettings,
    originalCutout: Bitmap,
    onApplyRecolor: (ObjectRecolorSettings, Bitmap) -> Unit,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var hueShift by remember { mutableFloatStateOf(currentSettings.hueShift) }
    var saturation by remember { mutableFloatStateOf(currentSettings.saturationFactor) }
    var brightness by remember { mutableFloatStateOf(currentSettings.brightnessFactor) }
    var targetColor by remember { mutableStateOf(currentSettings.targetColor) }
    var intensity by remember { mutableFloatStateOf(currentSettings.colorizeIntensity) }

    var previewBitmap by remember { mutableStateOf(originalCutout) }
    var isRendering by remember { mutableStateOf(false) }

    val presetColors = listOf(
        Color(0xFFE53935) to "Red",
        Color(0xFFFF9800) to "Orange",
        Color(0xFFFFEB3B) to "Yellow",
        Color(0xFF4CAF50) to "Emerald",
        Color(0xFF00BCD4) to "Cyan",
        Color(0xFF3F51B5) to "Indigo",
        Color(0xFF9C27B0) to "Purple",
        Color(0xFFE91E63) to "Pink",
        Color(0xFF795548) to "Brown",
        Color(0xFF607D8B) to "Silver",
        Color(0xFF212121) to "Black"
    )

    fun triggerPreview() {
        scope.launch {
            isRendering = true
            val updatedSettings = ObjectRecolorSettings(
                hueShift = hueShift,
                saturationFactor = saturation,
                brightnessFactor = brightness,
                targetColor = targetColor,
                colorizeIntensity = intensity
            )
            val colored = SmartObjectEngine.recolorCutout(originalCutout, updatedSettings)
            previewBitmap = colored
            isRendering = false
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkSurface,
        title = {
            Text("Recolor Object (Shading Preserved)", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                // Live preview thumbnail
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(110.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF1E1E1E))
                        .border(1.dp, DarkBorder, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    androidx.compose.foundation.Image(
                        bitmap = previewBitmap.asImageBitmap(),
                        contentDescription = "Recolored Preview",
                        modifier = Modifier.size(90.dp)
                    )
                    if (isRendering) {
                        CircularProgressIndicator(color = IndigoPrimary, modifier = Modifier.size(24.dp))
                    }
                }

                Text("Target Color Swatch:", color = TextSecondary, fontSize = 12.sp)

                // Color Swatches
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        // Reset to original color
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(DarkSurfaceVariant)
                                .border(
                                    width = if (targetColor == null) 2.5.dp else 1.dp,
                                    color = if (targetColor == null) IndigoPrimary else DarkBorder,
                                    shape = CircleShape
                                )
                                .clickable {
                                    targetColor = null
                                    hueShift = 0f
                                    saturation = 1f
                                    brightness = 1f
                                    triggerPreview()
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Orig", fontSize = 8.sp, color = TextPrimary)
                        }
                    }

                    items(presetColors) { (col, _) ->
                        val isSelected = targetColor == col.toArgb()
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(col)
                                .border(
                                    width = if (isSelected) 2.5.dp else 1.dp,
                                    color = if (isSelected) Color.White else DarkBorder,
                                    shape = CircleShape
                                )
                                .clickable {
                                    targetColor = col.toArgb()
                                    triggerPreview()
                                }
                        )
                    }
                }

                // Fine Adjustments: Hue, Saturation, Brightness
                SliderRow(
                    label = "Hue Shift",
                    value = hueShift,
                    range = -180f..180f,
                    onValueChange = {
                        hueShift = it
                        triggerPreview()
                    }
                )

                SliderRow(
                    label = "Saturation",
                    value = saturation * 100f,
                    range = 0f..250f,
                    onValueChange = {
                        saturation = it / 100f
                        triggerPreview()
                    }
                )

                SliderRow(
                    label = "Brightness",
                    value = brightness * 100f,
                    range = 50f..180f,
                    onValueChange = {
                        brightness = it / 100f
                        triggerPreview()
                    }
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val finalSettings = ObjectRecolorSettings(
                        hueShift = hueShift,
                        saturationFactor = saturation,
                        brightnessFactor = brightness,
                        targetColor = targetColor,
                        colorizeIntensity = intensity
                    )
                    onApplyRecolor(finalSettings, previewBitmap)
                },
                colors = ButtonDefaults.buttonColors(containerColor = IndigoPrimary),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Apply Recolor", color = Color.White, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextSecondary)
            }
        }
    )
}

/**
 * Cutout Library Dialog to paste previously copied objects onto the current photo
 */
@Composable
fun CutoutLibraryDialog(
    onSelectCutoutToPaste: (CutoutLayer) -> Unit,
    onDismiss: () -> Unit
) {
    val recentList = CutoutClipboard.getRecentCutouts()

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkSurface,
        title = {
            Text("Cutout Library", color = TextPrimary, fontWeight = FontWeight.Bold)
        },
        text = {
            if (recentList.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No cutouts copied yet.\nUse 'Smart Cutout' on Photo 1 to copy any object!", color = TextMuted, fontSize = 13.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Select a copied object to paste onto this photo:", color = TextSecondary, fontSize = 12.sp)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        itemsIndexed(recentList) { idx, item ->
                            Card(
                                colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .clickable { onSelectCutoutToPaste(item) }
                                    .border(1.dp, DarkBorder, RoundedCornerShape(12.dp))
                                    .padding(4.dp)
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.padding(8.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(72.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color.Black),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        androidx.compose.foundation.Image(
                                            bitmap = item.displayBitmap.asImageBitmap(),
                                            contentDescription = null,
                                            modifier = Modifier.size(68.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text("Cutout ${idx + 1}", fontSize = 11.sp, color = TextPrimary, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = TextSecondary)
            }
        }
    )
}
