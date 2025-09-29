package com.dlab.myaipiceditor

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Typeface

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

    fun applyFilter(input: Bitmap): Bitmap {
        // Simple brightness filter as example
        val output = input.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(output)
        val paint = Paint().apply {
            colorFilter = android.graphics.ColorMatrixColorFilter(
                floatArrayOf(
                    1.2f, 0f, 0f, 0f, 20f,
                    0f, 1.2f, 0f, 0f, 20f,
                    0f, 0f, 1.2f, 0f, 20f,
                    0f, 0f, 0f, 1f, 0f
                )
            )
        }
        canvas.drawBitmap(input, 0f, 0f, paint)
        return output
    }
}
