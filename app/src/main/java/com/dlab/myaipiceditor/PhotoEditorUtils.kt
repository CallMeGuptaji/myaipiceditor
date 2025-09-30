package com.dlab.myaipiceditor

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontFamily
import com.dlab.myaipiceditor.data.TextStyle

object PhotoEditorUtils {

    fun crop(input: Bitmap, x: Int, y: Int, width: Int, height: Int): Bitmap {
        val safeX = maxOf(0, minOf(x, input.width - 1))
        val safeY = maxOf(0, minOf(y, input.height - 1))
        val safeWidth = minOf(width, input.width - safeX)
        val safeHeight = minOf(height, input.height - safeY)
        return Bitmap.createBitmap(input, safeX, safeY, safeWidth, safeHeight)
    }

    fun resize(input: Bitmap, newWidth: Int, newHeight: Int): Bitmap {
        return Bitmap.createScaledBitmap(input, newWidth, newHeight, true)
    }
    
    fun rotate(input: Bitmap, degrees: Float): Bitmap {
        val matrix = Matrix().apply {
            postRotate(degrees)
        }
        return Bitmap.createBitmap(input, 0, 0, input.width, input.height, matrix, true)
    }

    fun addText(input: Bitmap, text: String, x: Float, y: Float, font: Typeface): Bitmap {
        val output = input.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(output)
        val paint = Paint().apply {
            this.typeface = font
            this.textSize = minOf(input.width, input.height) * 0.08f
            this.isAntiAlias = true
            this.color = android.graphics.Color.WHITE
            this.setShadowLayer(4f, 2f, 2f, android.graphics.Color.BLACK)
        }
        canvas.drawText(text, x, y, paint)
        return output
    }

    fun addStyledText(input: Bitmap, text: String, x: Float, y: Float, style: TextStyle, density: Float = 2f): Bitmap {
        val output = input.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(output)

        // Convert FontFamily to Typeface
        val baseTypeface = when (style.fontFamily) {
            FontFamily.Serif -> Typeface.SERIF
            FontFamily.SansSerif -> Typeface.SANS_SERIF
            FontFamily.Monospace -> Typeface.MONOSPACE
            FontFamily.Cursive -> Typeface.create("cursive", Typeface.NORMAL)
            else -> Typeface.DEFAULT
        }

        val typeface = if (style.isBold) {
            Typeface.create(baseTypeface, Typeface.BOLD)
        } else {
            baseTypeface
        }

        // Create paint for text - convert sp to pixels using density
        val textPaint = Paint().apply {
            this.textSize = style.fontSize * density
            this.isAntiAlias = true
            this.color = style.color.copy(alpha = style.opacity).toArgb()
            this.typeface = typeface
            this.textAlign = Paint.Align.LEFT
        }

        // Get text bounds for proper positioning
        val textBounds = android.graphics.Rect()
        textPaint.getTextBounds(text, 0, text.length, textBounds)

        // Account for 8dp padding (matches Compose preview)
        val padding = 8f * density

        // Draw highlight background if needed
        if (style.highlightColor.alpha > 0f) {
            val highlightPaint = Paint().apply {
                color = style.highlightColor.toArgb()
                isAntiAlias = true
            }

            canvas.drawRoundRect(
                x,
                y,
                x + textBounds.width() + padding,
                y + textBounds.height() + padding,
                12f, 12f,
                highlightPaint
            )
        }

        // Draw text with padding offset to match Compose preview
        val textX = x + (padding / 2f)
        val textY = y + (padding / 2f) - textBounds.top
        canvas.drawText(text, textX, textY, textPaint)
        return output
    }
}
