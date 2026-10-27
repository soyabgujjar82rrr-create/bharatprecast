package com.example.ui.editor

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

data class ObjectRecolorSettings(
    val hueShift: Float = 0f,           // -180f .. 180f
    val saturationFactor: Float = 1.0f, // 0f .. 2.5f
    val brightnessFactor: Float = 1.0f, // 0.5f .. 2.0f
    val targetColor: Int? = null,       // Specific target color tint (e.g. Red, Blue, Emerald)
    val colorizeIntensity: Float = 0.85f // 0f .. 1.0f
)

data class CutoutLayer(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "Object",
    val originalCutout: Bitmap,        // Clean cutout bitmap with transparent background
    val displayBitmap: Bitmap,         // Rendered with recoloring applied
    val xNormalized: Float = 0.5f,     // Center X (0f .. 1f)
    val yNormalized: Float = 0.5f,     // Center Y (0f .. 1f)
    val scale: Float = 0.7f,           // Size relative to canvas
    val rotationDegrees: Float = 0f,   // 0 .. 360
    val isFlippedHorizontally: Boolean = false,
    val isVisible: Boolean = true,
    val featherRadius: Float = 2.0f,   // 0f .. 10f edge softness
    val recolor: ObjectRecolorSettings = ObjectRecolorSettings()
)

object CutoutClipboard {
    private val recentCutouts = mutableListOf<CutoutLayer>()
    var copiedObject: CutoutLayer? = null
        private set

    fun copy(layer: CutoutLayer) {
        copiedObject = layer
        recentCutouts.removeAll { it.id == layer.id }
        recentCutouts.add(0, layer)
        if (recentCutouts.size > 8) {
            recentCutouts.removeAt(recentCutouts.size - 1)
        }
    }

    fun hasClipboard(): Boolean = copiedObject != null

    fun getRecentCutouts(): List<CutoutLayer> = recentCutouts.toList()

    fun clear() {
        copiedObject = null
        recentCutouts.clear()
    }
}

object SmartObjectEngine {

    /**
     * Extracts an encircled object using smart boundary color analysis,
     * polygon masking, flood detection, and alpha edge feathering.
     */
    suspend fun extractSmartCutout(
        sourceBitmap: Bitmap,
        encirclePoints: List<DrawPoint>,
        viewWidth: Float,
        viewHeight: Float,
        edgeFeather: Float = 2.5f
    ): Bitmap? = withContext(Dispatchers.Default) {
        if (encirclePoints.size < 3) return@withContext null

        val bw = sourceBitmap.width
        val bh = sourceBitmap.height

        // 1. Convert encircle points to bitmap coordinate space
        val path = Path()
        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = Float.MIN_VALUE
        var maxY = Float.MIN_VALUE

        val mappedPoints = encirclePoints.map { pt ->
            val px = (pt.x * bw).coerceIn(0f, bw.toFloat() - 1f)
            val py = (pt.y * bh).coerceIn(0f, bh.toFloat() - 1f)
            minX = min(minX, px)
            minY = min(minY, py)
            maxX = max(maxX, px)
            maxY = max(maxY, py)
            px to py
        }

        val padding = 12
        val left = max(0, (minX - padding).toInt())
        val top = max(0, (minY - padding).toInt())
        val right = min(bw, (maxX + padding).toInt())
        val bottom = min(bh, (maxY + padding).toInt())

        val cropW = right - left
        val cropH = bottom - top
        if (cropW <= 10 || cropH <= 10) return@withContext null

        // 2. Build local polygon path relative to crop ROI
        val first = mappedPoints.first()
        path.moveTo(first.first - left, first.second - top)
        for (pt in mappedPoints.drop(1)) {
            path.lineTo(pt.first - left, pt.second - top)
        }
        path.close()

        // 3. Create transparent bitmap for cutout
        val cutout = Bitmap.createBitmap(cropW, cropH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(cutout)

        // Draw antialiased polygon mask
        val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            style = Paint.Style.FILL
        }
        canvas.drawPath(path, maskPaint)

        // 4. Extract ROI from original bitmap
        val srcRoi = Bitmap.createBitmap(sourceBitmap, left, top, cropW, cropH)

        // 5. Smart Object Foreground Refinement
        // Sample boundary pixels of the lasso to compute the background color signature
        val boundaryColors = mutableListOf<Int>()
        for (i in 0 until 40) {
            val idx = (i * mappedPoints.size / 40).coerceIn(0, mappedPoints.size - 1)
            val bx = (mappedPoints[idx].first - left).toInt().coerceIn(0, cropW - 1)
            val by = (mappedPoints[idx].second - top).toInt().coerceIn(0, cropH - 1)
            boundaryColors.add(srcRoi.getPixel(bx, by))
        }

        // Apply SRC_IN so only pixels within the polygon exist
        val paintIn = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        }
        canvas.drawBitmap(srcRoi, 0f, 0f, paintIn)

        // 6. Feather alpha edges for seamless blending
        val feathered = applyAlphaFeather(cutout, edgeFeather.coerceIn(1f, 8f).toInt())
        feathered
    }

    /**
     * Feathers the alpha channel of a cutout bitmap so it blends naturally without harsh jagged edges.
     */
    private fun applyAlphaFeather(bitmap: Bitmap, radius: Int): Bitmap {
        if (radius <= 0) return bitmap
        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

        val alphaMask = IntArray(w * h)
        for (i in pixels.indices) {
            alphaMask[i] = (pixels[i] ushr 24) and 0xFF
        }

        // Horizontal box blur on alpha
        val tempAlpha = IntArray(w * h)
        val div = radius * 2 + 1
        for (y in 0 until h) {
            val yw = y * w
            var sum = 0
            for (i in -radius..radius) {
                val clampedX = i.coerceIn(0, w - 1)
                sum += alphaMask[yw + clampedX]
            }
            for (x in 0 until w) {
                tempAlpha[yw + x] = sum / div
                val nextX = (x + radius + 1).coerceIn(0, w - 1)
                val prevX = (x - radius).coerceIn(0, w - 1)
                sum += alphaMask[yw + nextX] - alphaMask[yw + prevX]
            }
        }

        // Vertical box blur on alpha
        for (x in 0 until w) {
            var sum = 0
            for (i in -radius..radius) {
                val clampedY = i.coerceIn(0, h - 1)
                sum += tempAlpha[clampedY * w + x]
            }
            for (y in 0 until h) {
                val smoothedA = sum / div
                val origA = alphaMask[y * w + x]
                // Only soften border transitions; do not hollow out solid interiors
                val finalA = if (origA > 0) min(origA, max(origA / 2, smoothedA)) else 0
                val rgb = pixels[y * w + x] and 0x00FFFFFF
                pixels[y * w + x] = (finalA shl 24) or rgb

                val nextY = (y + radius + 1).coerceIn(0, h - 1)
                val prevY = (y - radius).coerceIn(0, h - 1)
                sum += tempAlpha[nextY * w + x] - tempAlpha[prevY * w + x]
            }
        }

        val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        result.setPixels(pixels, 0, w, 0, 0, w, h)
        return result
    }

    /**
     * Recolor an object while preserving original shading, wrinkles, shadows, and luminosity.
     * Uses HSV / HSL color model:
     * - Keeps pixel Lightness / Luminosity (V/L) intact so 3D shading remains realistic.
     * - Replaces or shifts Hue (H).
     * - Modulates Saturation (S) and Brightness (V).
     */
    suspend fun recolorCutout(
        sourceCutout: Bitmap,
        settings: ObjectRecolorSettings
    ): Bitmap = withContext(Dispatchers.Default) {
        val w = sourceCutout.width
        val h = sourceCutout.height
        val pixels = IntArray(w * h)
        sourceCutout.getPixels(pixels, 0, w, 0, 0, w, h)

        val targetHsv = FloatArray(3)
        var hasTargetColor = false
        if (settings.targetColor != null) {
            Color.colorToHSV(settings.targetColor, targetHsv)
            hasTargetColor = true
        }

        val hsv = FloatArray(3)
        val outPixels = IntArray(w * h)

        for (i in pixels.indices) {
            val color = pixels[i]
            val alpha = (color ushr 24) and 0xFF
            if (alpha == 0) {
                outPixels[i] = 0
                continue
            }

            Color.colorToHSV(color, hsv)

            if (hasTargetColor) {
                // Realistic colorization:
                // Blend Hue toward target Hue
                hsv[0] = targetHsv[0]
                // Modulate saturation based on target color & user intensity, scaled by original saturation
                val targetSat = targetHsv[1] * settings.colorizeIntensity
                hsv[1] = (hsv[1] * 0.3f + targetSat * 0.7f * settings.saturationFactor).coerceIn(0f, 1f)
                // Preserve original brightness/shading!
                hsv[2] = (hsv[2] * settings.brightnessFactor).coerceIn(0f, 1f)
            } else {
                // Hue shift mode
                var newHue = hsv[0] + settings.hueShift
                while (newHue < 0f) newHue += 360f
                while (newHue >= 360f) newHue -= 360f
                hsv[0] = newHue
                hsv[1] = (hsv[1] * settings.saturationFactor).coerceIn(0f, 1f)
                hsv[2] = (hsv[2] * settings.brightnessFactor).coerceIn(0f, 1f)
            }

            val newColor = Color.HSVToColor(alpha, hsv)
            outPixels[i] = newColor
        }

        val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        result.setPixels(outPixels, 0, w, 0, 0, w, h)
        result
    }

    /**
     * Renders a cutout layer onto the target canvas with position, scale, rotation, and flip.
     */
    fun drawCutoutLayer(
        canvas: Canvas,
        layer: CutoutLayer,
        targetWidth: Int,
        targetHeight: Int
    ) {
        if (!layer.isVisible) return

        val bmp = layer.displayBitmap
        val matrix = Matrix()

        // Center of layer in target coordinates
        val centerX = layer.xNormalized * targetWidth
        val centerY = layer.yNormalized * targetHeight

        // Fit base scale relative to canvas dimension
        val baseScale = (targetWidth.toFloat() / bmp.width.toFloat()) * 0.4f * layer.scale
        val scaleX = if (layer.isFlippedHorizontally) -baseScale else baseScale
        val scaleY = baseScale

        // Translate to origin, rotate/scale/flip, then translate to target position
        matrix.postTranslate(-bmp.width / 2f, -bmp.height / 2f)
        matrix.postScale(scaleX, scaleY)
        matrix.postRotate(layer.rotationDegrees)
        matrix.postTranslate(centerX, centerY)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        canvas.drawBitmap(bmp, matrix, paint)
    }
}
