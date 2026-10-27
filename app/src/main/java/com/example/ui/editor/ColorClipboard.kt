package com.example.ui.editor

import android.graphics.Color
import java.util.UUID

object ColorClipboard {
    var copiedColor: Int? = null
        private set

    var currentHue: Float = 0f
        private set
    var currentSaturation: Float = 1f
        private set
    var currentBrightness: Float = 1f
        private set

    private val recentColorsList = mutableListOf<Int>()
    val recentColors: List<Int> get() = recentColorsList.toList()

    fun copyColor(color: Int) {
        copiedColor = color
        val hsv = FloatArray(3)
        Color.colorToHSV(color, hsv)
        currentHue = hsv[0]
        currentSaturation = hsv[1]
        currentBrightness = hsv[2]

        recentColorsList.remove(color)
        recentColorsList.add(0, color)
        if (recentColorsList.size > 10) {
            recentColorsList.removeAt(recentColorsList.size - 1)
        }
    }

    fun updateHsv(hue: Float, saturation: Float, brightness: Float) {
        currentHue = hue.coerceIn(0f, 360f)
        currentSaturation = saturation.coerceIn(0f, 1f)
        currentBrightness = brightness.coerceIn(0f, 1f)
        copiedColor = Color.HSVToColor(floatArrayOf(currentHue, currentSaturation, currentBrightness))
    }

    fun hasCopiedColor(): Boolean = copiedColor != null

    fun clear() {
        copiedColor = null
        recentColorsList.clear()
    }
}

data class ColorRecolorLayer(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "Color Tint",
    val color: Int,                      // Target ARGB color
    val strength: Float = 0.85f,         // 0f .. 1.0f (Color Intensity)
    val feather: Float = 5.0f,           // 0f .. 15f (Edge Feather / Softness)
    val selectionPoints: List<DrawPoint>,// Encircled target area
    val isVisible: Boolean = true
)
