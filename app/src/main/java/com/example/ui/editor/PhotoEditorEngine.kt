package com.example.ui.editor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.UUID

enum class FilterPreset(val displayName: String) {
    ORIGINAL("Original"),
    VIVID("Vivid"),
    WARM("Warm"),
    COOL("Cool"),
    VINTAGE("Vintage"),
    MONOCHROME("B&W"),
    SEPIA("Sepia"),
    DRAMATIC("Dramatic")
}

data class DrawPoint(val x: Float, val y: Float)

data class DrawStroke(
    val points: List<DrawPoint>,
    val color: Int,
    val strokeWidth: Float
)

data class TextSticker(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val color: Int,
    val textSize: Float,
    val xNormalized: Float = 0.5f,
    val yNormalized: Float = 0.5f
)

data class EditorState(
    val rotationDegrees: Int = 0,
    val isFlippedHorizontally: Boolean = false,
    val isFlippedVertically: Boolean = false,
    val brightness: Float = 0f, // -100 to +100
    val contrast: Float = 0f,   // -100 to +100
    val saturation: Float = 0f, // -100 to +100
    val blurRadius: Int = 0,    // 0 to 25
    val activeFilter: FilterPreset = FilterPreset.ORIGINAL,
    val strokes: List<DrawStroke> = emptyList(),
    val textStickers: List<TextSticker> = emptyList(),
    val cutoutLayers: List<CutoutLayer> = emptyList(),
    val colorRecolorLayers: List<ColorRecolorLayer> = emptyList(),
    val cropRectNormalized: List<Float>? = null // [left, top, right, bottom] 0f..1f
)

object PhotoEditorEngine {

    suspend fun loadBitmapFromUri(context: Context, uri: Uri, maxDimension: Int = 1600): Bitmap? = withContext(Dispatchers.IO) {
        try {
            var inputStream: InputStream? = context.contentResolver.openInputStream(uri)
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeStream(inputStream, null, options)
            inputStream?.close()

            var sampleSize = 1
            var w = options.outWidth
            var h = options.outHeight
            while (w / 2 >= maxDimension || h / 2 >= maxDimension) {
                w /= 2
                h /= 2
                sampleSize *= 2
            }

            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            inputStream = context.contentResolver.openInputStream(uri)
            val bitmap = BitmapFactory.decodeStream(inputStream, null, decodeOptions)
            inputStream?.close()
            bitmap
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Renders the complete editor state onto the source bitmap.
     */
    suspend fun renderFinalBitmap(
        source: Bitmap,
        state: EditorState
    ): Bitmap = withContext(Dispatchers.Default) {
        // 1. Apply Orientation & Crop Matrix
        val matrix = Matrix()
        if (state.rotationDegrees != 0) {
            matrix.postRotate(state.rotationDegrees.toFloat())
        }
        val scaleX = if (state.isFlippedHorizontally) -1f else 1f
        val scaleY = if (state.isFlippedVertically) -1f else 1f
        if (scaleX != 1f || scaleY != 1f) {
            matrix.postScale(scaleX, scaleY)
        }

        var transformed = Bitmap.createBitmap(
            source,
            0, 0, source.width, source.height,
            matrix,
            true
        )

        // Crop if cropRect is defined
        state.cropRectNormalized?.let { rect ->
            if (rect.size == 4) {
                val left = (rect[0] * transformed.width).toInt().coerceIn(0, transformed.width - 1)
                val top = (rect[1] * transformed.height).toInt().coerceIn(0, transformed.height - 1)
                val right = (rect[2] * transformed.width).toInt().coerceIn(left + 1, transformed.width)
                val bottom = (rect[3] * transformed.height).toInt().coerceIn(top + 1, transformed.height)
                val cropW = right - left
                val cropH = bottom - top
                if (cropW > 10 && cropH > 10) {
                    val cropped = Bitmap.createBitmap(transformed, left, top, cropW, cropH)
                    if (cropped != transformed) {
                        transformed = cropped
                    }
                }
            }
        }

        // 2. Apply Adjustments & Filters ColorMatrix
        val result = Bitmap.createBitmap(transformed.width, transformed.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)

        val combinedMatrix = ColorMatrix()
        // Saturation
        val sat = (state.saturation + 100f) / 100f
        combinedMatrix.setSaturation(sat.coerceAtLeast(0f))

        // Contrast
        val contrastFactor = (state.contrast + 100f) / 100f
        val contrastMatrix = ColorMatrix(floatArrayOf(
            contrastFactor, 0f, 0f, 0f, (1f - contrastFactor) * 128f,
            0f, contrastFactor, 0f, 0f, (1f - contrastFactor) * 128f,
            0f, 0f, contrastFactor, 0f, (1f - contrastFactor) * 128f,
            0f, 0f, 0f, 1f, 0f
        ))
        combinedMatrix.postConcat(contrastMatrix)

        // Brightness
        val brightnessShift = state.brightness * 1.5f
        val brightnessMatrix = ColorMatrix(floatArrayOf(
            1f, 0f, 0f, 0f, brightnessShift,
            0f, 1f, 0f, 0f, brightnessShift,
            0f, 0f, 1f, 0f, brightnessShift,
            0f, 0f, 0f, 1f, 0f
        ))
        combinedMatrix.postConcat(brightnessMatrix)

        // Filter Preset Matrix
        val presetMatrix = getFilterColorMatrix(state.activeFilter)
        if (presetMatrix != null) {
            combinedMatrix.postConcat(presetMatrix)
        }

        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(combinedMatrix)
        }
        canvas.drawBitmap(transformed, 0f, 0f, paint)

        // Apply Blur if > 0
        var blurredResult = result
        if (state.blurRadius > 0) {
            blurredResult = fastBlur(result, state.blurRadius.coerceIn(1, 25))
        }

        val finalCanvas = Canvas(blurredResult)

        // 3. Render Drawing Strokes
        for (stroke in state.strokes) {
            if (stroke.points.size > 1) {
                val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = stroke.color
                    strokeWidth = stroke.strokeWidth * (blurredResult.width / 400f)
                    style = Paint.Style.STROKE
                    strokeJoin = Paint.Join.ROUND
                    strokeCap = Paint.Cap.ROUND
                }
                val path = Path()
                val first = stroke.points.first()
                path.moveTo(first.x * blurredResult.width, first.y * blurredResult.height)
                for (p in stroke.points.drop(1)) {
                    path.lineTo(p.x * blurredResult.width, p.y * blurredResult.height)
                }
                finalCanvas.drawPath(path, strokePaint)
            }
        }

        // 4. Render Text Stickers
        for (sticker in state.textStickers) {
            val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = sticker.color
                textSize = sticker.textSize * (blurredResult.width / 400f)
                isFakeBoldText = true
                textAlign = Paint.Align.CENTER
                setShadowLayer(4f, 2f, 2f, 0xCC000000.toInt())
            }

            val x = sticker.xNormalized * blurredResult.width
            val y = sticker.yNormalized * blurredResult.height
            finalCanvas.drawText(sticker.text, x, y, textPaint)
        }

        // 5. Render Cutout Object Layers
        for (layer in state.cutoutLayers) {
            SmartObjectEngine.drawCutoutLayer(finalCanvas, layer, blurredResult.width, blurredResult.height)
        }

        // 6. Apply Color Recolor Layers (Copy Color -> Apply to Area)
        var finalResult = blurredResult
        for (recolorLayer in state.colorRecolorLayers) {
            finalResult = ColorTransferEngine.applyColorToBitmap(finalResult, recolorLayer)
        }

        finalResult
    }

    private fun getFilterColorMatrix(preset: FilterPreset): ColorMatrix? {
        return when (preset) {
            FilterPreset.ORIGINAL -> null
            FilterPreset.VIVID -> ColorMatrix().apply {
                setSaturation(1.4f)
                val cm = ColorMatrix(floatArrayOf(
                    1.1f, 0f, 0f, 0f, 5f,
                    0f, 1.1f, 0f, 0f, 5f,
                    0f, 0f, 1.1f, 0f, 5f,
                    0f, 0f, 0f, 1f, 0f
                ))
                postConcat(cm)
            }
            FilterPreset.WARM -> ColorMatrix(floatArrayOf(
                1.2f, 0f, 0f, 0f, 15f,
                0f, 1.05f, 0f, 0f, 10f,
                0f, 0f, 0.9f, 0f, -10f,
                0f, 0f, 0f, 1f, 0f
            ))
            FilterPreset.COOL -> ColorMatrix(floatArrayOf(
                0.9f, 0f, 0f, 0f, -10f,
                0f, 1.05f, 0f, 0f, 5f,
                0f, 0f, 1.25f, 0f, 20f,
                0f, 0f, 0f, 1f, 0f
            ))
            FilterPreset.VINTAGE -> ColorMatrix(floatArrayOf(
                0.9f, 0f, 0f, 0f, 20f,
                0f, 0.85f, 0f, 0f, 15f,
                0f, 0f, 0.7f, 0f, 10f,
                0f, 0f, 0f, 1f, 0f
            ))
            FilterPreset.MONOCHROME -> ColorMatrix().apply {
                setSaturation(0f)
            }
            FilterPreset.SEPIA -> ColorMatrix(floatArrayOf(
                0.393f, 0.769f, 0.189f, 0f, 0f,
                0.349f, 0.686f, 0.168f, 0f, 0f,
                0.272f, 0.534f, 0.131f, 0f, 0f,
                0f, 0f, 0f, 1f, 0f
            ))
            FilterPreset.DRAMATIC -> ColorMatrix().apply {
                setSaturation(0.6f)
                val cm = ColorMatrix(floatArrayOf(
                    1.3f, 0f, 0f, 0f, -25f,
                    0f, 1.3f, 0f, 0f, -25f,
                    0f, 0f, 1.3f, 0f, -25f,
                    0f, 0f, 0f, 1f, 0f
                ))
                postConcat(cm)
            }
        }
    }

    /**
     * Efficient stack blur implementation for Android
     */
    private fun fastBlur(sentBitmap: Bitmap, radius: Int): Bitmap {
        val bitmap = sentBitmap.copy(sentBitmap.config ?: Bitmap.Config.ARGB_8888, true)
        if (radius < 1) return bitmap

        val w = bitmap.width
        val h = bitmap.height
        val pix = IntArray(w * h)
        bitmap.getPixels(pix, 0, w, 0, 0, w, h)

        val wm = w - 1
        val hm = h - 1
        val wh = w * h
        val div = radius + radius + 1

        val r = IntArray(wh)
        val g = IntArray(wh)
        val b = IntArray(wh)
        var rsum: Int
        var gsum: Int
        var bsum: Int
        var x: Int
        var y: Int
        var i: Int
        var p: Int
        var yp: Int
        var yi: Int
        val vmin = IntArray(Math.max(w, h))

        var divsum = (div + 1) shr 1
        divsum *= divsum
        val dv = IntArray(256 * divsum)
        for (idx in 0 until 256 * divsum) {
            dv[idx] = idx / divsum
        }

        yi = 0
        var yw = 0

        val stack = Array(div) { IntArray(3) }
        var stackpointer: Int
        var stackstart: Int
        var sir: IntArray
        var rbs: Int
        val r1 = radius + 1
        var routsum: Int
        var goutsum: Int
        var boutsum: Int
        var rinsum: Int
        var ginsum: Int
        var binsum: Int

        for (curY in 0 until h) {
            rinsum = 0
            ginsum = 0
            binsum = 0
            routsum = 0
            goutsum = 0
            boutsum = 0
            rsum = 0
            gsum = 0
            bsum = 0
            for (idx in -radius..radius) {
                p = pix[yi + Math.min(wm, Math.max(idx, 0))]
                sir = stack[idx + radius]
                sir[0] = (p and 0xff0000) shr 16
                sir[1] = (p and 0x00ff00) shr 8
                sir[2] = p and 0x0000ff
                rbs = r1 - Math.abs(idx)
                rsum += sir[0] * rbs
                gsum += sir[1] * rbs
                bsum += sir[2] * rbs
                if (idx > 0) {
                    rinsum += sir[0]
                    ginsum += sir[1]
                    binsum += sir[2]
                } else {
                    routsum += sir[0]
                    goutsum += sir[1]
                    boutsum += sir[2]
                }
            }
            stackpointer = radius

            for (curX in 0 until w) {
                r[yi] = dv[rsum]
                g[yi] = dv[gsum]
                b[yi] = dv[bsum]

                rsum -= routsum
                gsum -= goutsum
                bsum -= boutsum

                stackstart = stackpointer - radius + div
                sir = stack[stackstart % div]

                routsum -= sir[0]
                goutsum -= sir[1]
                boutsum -= sir[2]

                if (curY == 0) {
                    vmin[curX] = Math.min(curX + radius + 1, wm)
                }
                p = pix[yw + vmin[curX]]

                sir[0] = (p and 0xff0000) shr 16
                sir[1] = (p and 0x00ff00) shr 8
                sir[2] = p and 0x0000ff

                rinsum += sir[0]
                ginsum += sir[1]
                binsum += sir[2]

                rsum += rinsum
                gsum += ginsum
                bsum += binsum

                stackpointer = (stackpointer + 1) % div
                sir = stack[stackpointer % div]

                routsum += sir[0]
                goutsum += sir[1]
                boutsum += sir[2]

                rinsum -= sir[0]
                ginsum -= sir[1]
                binsum -= sir[2]

                yi++
            }
            yw += w
        }

        for (curX in 0 until w) {
            rinsum = 0
            ginsum = 0
            binsum = 0
            routsum = 0
            goutsum = 0
            boutsum = 0
            rsum = 0
            gsum = 0
            bsum = 0
            yp = -radius * w
            for (idx in -radius..radius) {
                yi = Math.max(0, yp) + curX
                sir = stack[idx + radius]
                sir[0] = r[yi]
                sir[1] = g[yi]
                sir[2] = b[yi]

                rbs = r1 - Math.abs(idx)
                rsum += r[yi] * rbs
                gsum += g[yi] * rbs
                bsum += b[yi] * rbs

                if (idx > 0) {
                    rinsum += sir[0]
                    ginsum += sir[1]
                    binsum += sir[2]
                } else {
                    routsum += sir[0]
                    goutsum += sir[1]
                    boutsum += sir[2]
                }

                if (idx < hm) {
                    yp += w
                }
            }
            yi = curX
            stackpointer = radius
            for (curY in 0 until h) {
                pix[yi] = (0xff000000.toInt() and pix[yi]) or (dv[rsum] shl 16) or (dv[gsum] shl 8) or dv[bsum]

                rsum -= routsum
                gsum -= goutsum
                bsum -= boutsum

                stackstart = stackpointer - radius + div
                sir = stack[stackstart % div]

                routsum -= sir[0]
                goutsum -= sir[1]
                boutsum -= sir[2]

                if (curX == 0) {
                    vmin[curY] = Math.min(curY + r1, hm) * w
                }
                p = curX + vmin[curY]

                sir[0] = r[p]
                sir[1] = g[p]
                sir[2] = b[p]

                rinsum += sir[0]
                ginsum += sir[1]
                binsum += sir[2]

                rsum += rinsum
                gsum += ginsum
                bsum += binsum

                stackpointer = (stackpointer + 1) % div
                sir = stack[stackpointer]

                routsum += sir[0]
                goutsum += sir[1]
                boutsum += sir[2]

                rinsum -= sir[0]
                ginsum -= sir[1]
                binsum -= sir[2]

                yi += w
            }
        }

        bitmap.setPixels(pix, 0, w, 0, 0, w, h)
        return bitmap
    }

    suspend fun saveBitmapToFile(context: Context, bitmap: Bitmap, targetFile: File): Boolean = withContext(Dispatchers.IO) {
        try {
            FileOutputStream(targetFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
