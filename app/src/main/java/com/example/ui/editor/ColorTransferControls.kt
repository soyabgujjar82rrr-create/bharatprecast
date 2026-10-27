package com.example.ui.editor

import android.graphics.Color
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.Colorize
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FormatPaint
import androidx.compose.material.icons.filled.InvertColors
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.drawscope.Stroke
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
import com.example.ui.theme.DarkSurfaceVariant
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.IndigoPrimary
import com.example.ui.theme.RoseSecondary
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun ColorTransferControls(
    isSamplingMode: Boolean,
    isTargetSelectionMode: Boolean,
    recolorLayers: List<ColorRecolorLayer>,
    activeLayerIndex: Int,
    onToggleSamplingMode: () -> Unit,
    onToggleTargetSelectionMode: () -> Unit,
    onOpenFineTuneDialog: () -> Unit,
    onUpdateLayer: (ColorRecolorLayer) -> Unit,
    onDeleteLayer: (Int) -> Unit
) {
    val context = LocalContext.current
    val copiedColor = ColorClipboard.copiedColor
    val activeLayer = recolorLayers.getOrNull(activeLayerIndex)

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // Row 1: Primary Action Buttons (Sample Color vs Apply Copied Color)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // "Copy Color" (Eyedropper Sampling Button)
            Button(
                onClick = onToggleSamplingMode,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isSamplingMode) CyanTertiary else DarkSurfaceVariant,
                    contentColor = if (isSamplingMode) ComposeColor.Black else TextPrimary
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .weight(1f)
                    .testTag("copy_color_sampling_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Colorize,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (isSamplingMode) "Tap to Sample" else "Copy Color",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // "Apply Copied Color" Button
            Button(
                onClick = {
                    if (ColorClipboard.hasCopiedColor()) {
                        onToggleTargetSelectionMode()
                    } else {
                        Toast.makeText(context, "No color copied yet. Tap 'Copy Color' and sample any object first.", Toast.LENGTH_SHORT).show()
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isTargetSelectionMode) EmeraldSuccess else (if (ColorClipboard.hasCopiedColor()) IndigoPrimary else DarkSurfaceVariant.copy(alpha = 0.6f)),
                    contentColor = if (isTargetSelectionMode) ComposeColor.Black else (if (ColorClipboard.hasCopiedColor()) ComposeColor.White else TextMuted)
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .weight(1.1f)
                    .testTag("apply_copied_color_button")
            ) {
                Icon(
                    imageVector = Icons.Default.FormatPaint,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (isTargetSelectionMode) "Select Area ON" else "Apply Color",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Row 2: Copied Color Status Card
        if (copiedColor != null) {
            val hex = String.format("#%06X", 0xFFFFFF and copiedColor)
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant),
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(ComposeColor(copiedColor))
                                .border(1.5.dp, ComposeColor.White, CircleShape)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text("Copied Color", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                            Text(hex, fontSize = 11.sp, color = TextMuted)
                        }
                    }

                    OutlinedButton(
                        onClick = onOpenFineTuneDialog,
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(containerColor = DarkSurface)
                    ) {
                        Icon(imageVector = Icons.Default.Tune, contentDescription = null, tint = CyanTertiary, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Fine-Tune", fontSize = 11.sp, color = TextPrimary)
                    }
                }
            }
        }

        // Recent Sampled Colors Palette
        if (ColorClipboard.recentColors.isNotEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Recent:", fontSize = 11.sp, color = TextMuted, modifier = Modifier.width(48.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(ColorClipboard.recentColors) { c ->
                        val isSelected = copiedColor == c
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(ComposeColor(c))
                                .border(
                                    width = if (isSelected) 2.dp else 1.dp,
                                    color = if (isSelected) ComposeColor.White else DarkBorder,
                                    shape = CircleShape
                                )
                                .clickable {
                                    ColorClipboard.copyColor(c)
                                    Toast.makeText(context, "Selected color activated", Toast.LENGTH_SHORT).show()
                                }
                        )
                    }
                }
            }
        }

        // Row 3: Active Color Recolor Layer Sliders (Strength & Feather)
        if (activeLayer != null) {
            HorizontalDivider(color = DarkBorder, modifier = Modifier.padding(vertical = 2.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "RECOLOR ADJUSTMENTS",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextMuted,
                    letterSpacing = 1.sp
                )

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    // Toggle visibility
                    IconButton(
                        onClick = { onUpdateLayer(activeLayer.copy(isVisible = !activeLayer.isVisible)) },
                        modifier = Modifier.size(26.dp)
                    ) {
                        Icon(
                            imageVector = if (activeLayer.isVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            contentDescription = "Visibility",
                            tint = if (activeLayer.isVisible) TextPrimary else TextMuted,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                    // Delete applied color layer
                    IconButton(
                        onClick = { onDeleteLayer(activeLayerIndex) },
                        modifier = Modifier.size(26.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete", tint = RoseSecondary, modifier = Modifier.size(15.dp))
                    }
                }
            }

            // Color Strength / Intensity Slider
            SliderRow(
                label = "Strength",
                value = activeLayer.strength * 100f,
                range = 10f..100f,
                onValueChange = { onUpdateLayer(activeLayer.copy(strength = it / 100f)) }
            )

            // Edge Feather / Softness Slider
            SliderRow(
                label = "Feather",
                value = activeLayer.feather,
                range = 1f..15f,
                onValueChange = { onUpdateLayer(activeLayer.copy(feather = it)) }
            )
        } else if (!isSamplingMode && !isTargetSelectionMode) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (ColorClipboard.hasCopiedColor())
                        "Tap 'Apply Color' and encircle the object you want to recolor."
                    else
                        "Tap 'Copy Color', then tap any object on the photo to sample its color.",
                    color = TextMuted,
                    fontSize = 12.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
    }
}

/**
 * Dialog shown to fine-tune the sampled color with Hue, Saturation, and Brightness sliders.
 */
@Composable
fun SampledColorFineTuneDialog(
    initialColor: Int,
    onSaveCopiedColor: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val hsv = remember(initialColor) {
        val arr = FloatArray(3)
        Color.colorToHSV(initialColor, arr)
        arr
    }

    var hue by remember { mutableFloatStateOf(hsv[0]) }
    var saturation by remember { mutableFloatStateOf(hsv[1]) }
    var brightness by remember { mutableFloatStateOf(hsv[2]) }

    val currentColor = Color.HSVToColor(floatArrayOf(hue, saturation, brightness))
    val hexString = String.format("#%06X", 0xFFFFFF and currentColor)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkSurface,
        title = {
            Text("Fine-Tune Sampled Color", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                // Swatch comparison
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Sampled", fontSize = 11.sp, color = TextMuted)
                        Spacer(modifier = Modifier.height(4.dp))
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(ComposeColor(initialColor))
                                .border(1.dp, DarkBorder, RoundedCornerShape(10.dp))
                        )
                    }

                    Icon(imageVector = Icons.Default.InvertColors, contentDescription = null, tint = CyanTertiary)

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Adjusted", fontSize = 11.sp, color = TextMuted)
                        Spacer(modifier = Modifier.height(4.dp))
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(ComposeColor(currentColor))
                                .border(2.dp, IndigoPrimary, RoundedCornerShape(10.dp))
                        )
                    }
                }

                Text("Hex: $hexString", color = TextSecondary, fontSize = 12.sp, modifier = Modifier.align(Alignment.CenterHorizontally))

                // Sliders
                SliderRow(
                    label = "Hue",
                    value = hue,
                    range = 0f..360f,
                    onValueChange = { hue = it }
                )

                SliderRow(
                    label = "Saturation",
                    value = saturation * 100f,
                    range = 0f..100f,
                    onValueChange = { saturation = it / 100f }
                )

                SliderRow(
                    label = "Brightness",
                    value = brightness * 100f,
                    range = 0f..100f,
                    onValueChange = { brightness = it / 100f }
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSaveCopiedColor(currentColor) },
                colors = ButtonDefaults.buttonColors(containerColor = IndigoPrimary),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(imageVector = Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Save Copied Color", color = ComposeColor.White, fontWeight = FontWeight.Bold)
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
 * Interactive Loupe / Magnifier view drawn over canvas during eyedropper sampling
 */
@Composable
fun LoupeOverlay(
    touchOffset: Offset,
    sampledColor: Int
) {
    val hex = String.format("#%06X", 0xFFFFFF and sampledColor)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(ComposeColor.Black.copy(alpha = 0.85f))
                .border(1.5.dp, CyanTertiary, RoundedCornerShape(20.dp))
                .padding(horizontal = 14.dp, vertical = 6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(ComposeColor(sampledColor))
                    .border(1.5.dp, ComposeColor.White, CircleShape)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Sampled: $hex",
                color = ComposeColor.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
