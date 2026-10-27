package com.example.ui.editor

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min

object ColorTransferEngine {

    /**
     * Samples pixel color at normalized (x, y) coordinates on the bitmap.
     * Uses a 3x3 local pixel average to eliminate noise and specular grain.
     */
    fun sampleColorAt(
        bitmap: Bitmap,
        xNormalized: Float,
        yNormalized: Float,
        sampleRadius: Int = 2
    ): Int {
        val bw = bitmap.width
        val bh = bitmap.height
        val centerX = (xNormalized * bw).toInt().coerceIn(0, bw - 1)
        val centerY = (yNormalized * bh).toInt().coerceIn(0, bh - 1)

        var totalR = 0L
        var totalG = 0L
        var totalB = 0L
        var count = 0

        for (dy in -sampleRadius..sampleRadius) {
            val py = (centerY + dy).coerceIn(0, bh - 1)
            for (dx in -sampleRadius..sampleRadius) {
                val px = (centerX + dx).coerceIn(0, bw - 1)
                val pixel = bitmap.getPixel(px, py)
                totalR += Color.red(pixel)
                totalG += Color.green(pixel)
                totalB += Color.blue(pixel)
                count++
            }
        }

        val avgR = (totalR / count).toInt().coerceIn(0, 255)
        val avgG = (totalG / count).toInt().coerceIn(0, 255)
        val avgB = (totalB / count).toInt().coerceIn(0, 255)
        return Color.rgb(avgR, avgG, avgB)
    }

    /**
     * Applies target color to the encircled area of the target bitmap.
     * Preserves shadows, highlights, specular reflections, and textures by retaining
     * original pixel luminosity (V in HSV) and smoothly blending Hue & Saturation.
     */
    suspend fun applyColorToBitmap(
        sourceBitmap: Bitmap,
        layer: ColorRecolorLayer
    ): Bitmap = withContext(Dispatchers.Default) {
        if (!layer.isVisible || layer.selectionPoints.size < 3) return@withContext sourceBitmap

        val bw = sourceBitmap.width
        val bh = sourceBitmap.height

        // 1. Calculate bounding box of selection
        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = Float.MIN_VALUE
        var maxY = Float.MIN_VALUE

        val mapped = layer.selectionPoints.map { pt ->
            val px = (pt.x * bw).coerceIn(0f, bw - 1f)
            val py = (pt.y * bh).coerceIn(0f, bh - 1f)
            minX = min(minX, px)
            minY = min(minY, py)
            maxX = max(maxX, px)
            maxY = max(maxY, py)
            px to py
        }

        val featherPadding = (layer.feather * 2).toInt() + 10
        val left = max(0, (minX - featherPadding).toInt())
        val top = max(0, (minY - featherPadding).toInt())
        val right = min(bw, (maxX + featherPadding).toInt())
        val bottom = min(bh, (maxY + featherPadding).toInt())
        val roiW = right - left
        val roiH = bottom - top

        if (roiW <= 5 || roiH <= 5) return@withContext sourceBitmap

        // 2. Build local polygon mask in ROI space
        val path = Path()
        val first = mapped.first()
        path.moveTo(first.first - left, first.second - top)
        for (pt in mapped.drop(1)) {
            path.lineTo(pt.first - left, pt.second - top)
        }
        path.close()

        val maskBitmap = Bitmap.createBitmap(roiW, roiH, Bitmap.Config.ALPHA_8)
        val maskCanvas = Canvas(maskBitmap)
        val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            style = Paint.Style.FILL
        }
        maskCanvas.drawPath(path, maskPaint)

        // 3. Feather the alpha mask for smooth edge transitions
        val maskPixels = ByteArray(roiW * roiH)
        val maskBuffer = java.nio.ByteBuffer.wrap(maskPixels)
        maskBitmap.copyPixelsToBuffer(maskBuffer)

        val featheredMask = applyAlphaFeather(maskPixels, roiW, roiH, layer.feather.toInt().coerceIn(1, 15))

        // 4. Recolor ROI pixels preserving lighting and texture
        val targetHsv = FloatArray(3)
        Color.colorToHSV(layer.color, targetHsv)

        val resultBitmap = sourceBitmap.copy(sourceBitmap.config ?: Bitmap.Config.ARGB_8888, true)
        val roiPixels = IntArray(roiW * roiH)
        resultBitmap.getPixels(roiPixels, 0, roiW, left, top, roiW, roiH)

        val pixelHsv = FloatArray(3)
        val strength = layer.strength.coerceIn(0f, 1f)

        for (y in 0 until roiH) {
            val yw = y * roiW
            for (x in 0 until roiW) {
                val idx = yw + x
                val maskAlpha = featheredMask[idx].toInt() and 0xFF
                if (maskAlpha == 0) continue

                val origColor = roiPixels[idx]
                val origA = Color.alpha(origColor)
                if (origA == 0) continue

                Color.colorToHSV(origColor, pixelHsv)

                // Preserve original brightness (V/Luminosity) so shadows, highlights, and wrinkles stay 100% natural!
                val origV = pixelHsv[2]
                val origS = pixelHsv[1]

                // Replace Hue with Target Hue
                pixelHsv[0] = targetHsv[0]

                // Blend Saturation based on target color and strength
                val blendedSat = origS * (1f - strength) + targetHsv[1] * strength
                pixelHsv[1] = blendedSat.coerceIn(0f, 1f)

                // Subtly modulate brightness with very light weight (5%) to reflect target color tone while retaining highlights
                val subtleV = origV * (0.92f + targetHsv[2] * 0.08f)
                pixelHsv[2] = subtleV.coerceIn(0f, 1f)

                val recoloredColor = Color.HSVToColor(origA, pixelHsv)

                // Effective blend factor = (featheredMaskAlpha / 255f) * strength
                val blendFactor = (maskAlpha / 255f) * strength

                if (blendFactor >= 0.99f) {
                    roiPixels[idx] = recoloredColor
                } else {
                    // Alpha-blend between original color and recolored color
                    val r = (Color.red(origColor) * (1f - blendFactor) + Color.red(recoloredColor) * blendFactor).toInt().coerceIn(0, 255)
                    val g = (Color.green(origColor) * (1f - blendFactor) + Color.green(recoloredColor) * blendFactor).toInt().coerceIn(0, 255)
                    val b = (Color.blue(origColor) * (1f - blendFactor) + Color.blue(recoloredColor) * blendFactor).toInt().coerceIn(0, 255)
                    roiPixels[idx] = Color.argb(origA, r, g, b)
                }
            }
        }

        resultBitmap.setPixels(roiPixels, 0, roiW, left, top, roiW, roiH)
        resultBitmap
    }

    private fun applyAlphaFeather(pixels: ByteArray, width: Int, height: Int, radius: Int): ByteArray {
        if (radius <= 0) return pixels
        val output = ByteArray(width * height)
        val div = radius * 2 + 1
        val temp = IntArray(width * height)

        // Horizontal box blur
        for (y in 0 until height) {
            val yw = y * width
            var sum = 0
            for (i in -radius..radius) {
                val cx = i.coerceIn(0, width - 1)
                sum += pixels[yw + cx].toInt() and 0xFF
            }
            for (x in 0 until width) {
                temp[yw + x] = sum / div
                val nextX = (x + radius + 1).coerceIn(0, width - 1)
                val prevX = (x - radius).coerceIn(0, width - 1)
                sum += (pixels[yw + nextX].toInt() and 0xFF) - (pixels[yw + prevX].toInt() and 0xFF)
            }
        }

        // Vertical box blur
        for (x in 0 until width) {
            var sum = 0
            for (i in -radius..radius) {
                val cy = i.coerceIn(0, height - 1)
                sum += temp[cy * width + x]
            }
            for (y in 0 until height) {
                output[y * width + x] = (sum / div).toByte()
                val nextY = (y + radius + 1).coerceIn(0, height - 1)
                val prevY = (y - radius).coerceIn(0, height - 1)
                sum += temp[nextY * width + x] - temp[prevY * width + x]
            }
        }

        return output
    }
}
