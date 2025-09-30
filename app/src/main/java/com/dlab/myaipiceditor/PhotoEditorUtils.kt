package com.dlab.myaipiceditor

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.ui.graphics.toArgb
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

    fun addStyledText(input: Bitmap, text: String, x: Float, y: Float, style: TextStyle): Bitmap {
        val output = input.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(output)

        // Create paint for text
        val textPaint = Paint().apply {
            textSize = style.fontSize * (minOf(input.width, input.height) / 500f) // Scale based on image size
            isAntiAlias = true
            color = style.color.copy(alpha = style.opacity).toArgb()
            typeface = if (style.isBold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            textAlign = Paint.Align.LEFT
            setShadowLayer(4f, 2f, 2f, android.graphics.Color.BLACK)
        }

        // Get text bounds for proper positioning
        val textBounds = android.graphics.Rect()
        textPaint.getTextBounds(text, 0, text.length, textBounds)

        // Adjust y to account for text baseline
        val adjustedY = y + textBounds.height()

        // Draw highlight background if needed
        if (style.highlightColor.alpha > 0f) {
            val highlightPaint = Paint().apply {
                color = style.highlightColor.toArgb()
                isAntiAlias = true
            }

            val padding = 16f
            canvas.drawRoundRect(
                x - padding,
                y - padding,
                x + textBounds.width() + padding,
                y + textBounds.height() + padding,
                12f, 12f,
                highlightPaint
            )
        }

        // Draw text
        canvas.drawText(text, x, adjustedY, textPaint)
        return output
    }
}
