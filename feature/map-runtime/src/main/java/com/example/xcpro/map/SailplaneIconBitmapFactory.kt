package com.example.xcpro.map

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import androidx.core.graphics.toColorInt

internal object SailplaneIconBitmapFactory {

    fun create(iconSizePx: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(iconSizePx, iconSizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val centerX = iconSizePx / 2f
        val centerY = iconSizePx / 2f
        val topPoint = centerY - iconSizePx * 0.40f
        val leftPoint = centerX - iconSizePx * 0.32f
        val leftPointY = centerY + iconSizePx * 0.28f
        val rightPoint = centerX + iconSizePx * 0.32f
        val rightPointY = centerY + iconSizePx * 0.28f
        val bottomPoint = centerY + iconSizePx * 0.12f
        val cornerRadius = iconSizePx * 0.05f

        val leftFacet = Path().apply {
            moveTo(centerX - iconSizePx * 0.01f, topPoint + cornerRadius)
            quadTo(centerX, topPoint, centerX, topPoint + cornerRadius * 0.5f)
            lineTo(leftPoint + cornerRadius, leftPointY - cornerRadius * 0.3f)
            quadTo(leftPoint, leftPointY, leftPoint + cornerRadius * 0.8f, leftPointY + cornerRadius * 0.3f)
            lineTo(centerX - iconSizePx * 0.01f, bottomPoint - cornerRadius * 0.5f)
            quadTo(centerX, bottomPoint, centerX, bottomPoint)
            close()
        }

        val centerFacet = Path().apply {
            moveTo(centerX, topPoint + cornerRadius * 0.5f)
            quadTo(centerX, topPoint, centerX + iconSizePx * 0.01f, topPoint + cornerRadius)
            lineTo(centerX + iconSizePx * 0.01f, bottomPoint - cornerRadius * 0.5f)
            quadTo(centerX, bottomPoint, centerX, bottomPoint)
            lineTo(centerX - iconSizePx * 0.01f, bottomPoint - cornerRadius * 0.5f)
            close()
        }

        val rightFacet = Path().apply {
            moveTo(centerX + iconSizePx * 0.01f, topPoint + cornerRadius)
            quadTo(centerX, topPoint, centerX, topPoint + cornerRadius * 0.5f)
            lineTo(centerX + iconSizePx * 0.01f, bottomPoint - cornerRadius * 0.5f)
            quadTo(centerX, bottomPoint, rightPoint - cornerRadius * 0.8f, rightPointY + cornerRadius * 0.3f)
            quadTo(rightPoint, rightPointY, rightPoint - cornerRadius, rightPointY - cornerRadius * 0.3f)
            lineTo(centerX + iconSizePx * 0.01f, topPoint + cornerRadius)
            close()
        }

        val outlinePath = Path().apply {
            moveTo(centerX, topPoint + cornerRadius * 0.3f)
            quadTo(centerX, topPoint, centerX - iconSizePx * 0.015f, topPoint + cornerRadius * 0.8f)
            lineTo(leftPoint + cornerRadius * 0.7f, leftPointY - cornerRadius * 0.5f)
            quadTo(
                leftPoint - cornerRadius * 0.2f,
                leftPointY,
                leftPoint + cornerRadius * 0.6f,
                leftPointY + cornerRadius * 0.6f
            )
            lineTo(centerX - iconSizePx * 0.02f, bottomPoint - cornerRadius * 0.3f)
            quadTo(
                centerX,
                bottomPoint + cornerRadius * 0.2f,
                centerX + iconSizePx * 0.02f,
                bottomPoint - cornerRadius * 0.3f
            )
            lineTo(rightPoint - cornerRadius * 0.6f, rightPointY + cornerRadius * 0.6f)
            quadTo(
                rightPoint + cornerRadius * 0.2f,
                rightPointY,
                rightPoint - cornerRadius * 0.7f,
                rightPointY - cornerRadius * 0.5f
            )
            lineTo(centerX + iconSizePx * 0.015f, topPoint + cornerRadius * 0.8f)
            quadTo(centerX, topPoint, centerX, topPoint + cornerRadius * 0.3f)
            close()
        }

        val leftPaint = Paint().apply {
            color = "#5DADE2".toColorInt()
            isAntiAlias = true
            style = Paint.Style.FILL
        }
        val centerPaint = Paint().apply {
            color = "#3498DB".toColorInt()
            isAntiAlias = true
            style = Paint.Style.FILL
        }
        val rightPaint = Paint().apply {
            color = "#2874A6".toColorInt()
            isAntiAlias = true
            style = Paint.Style.FILL
        }
        val outlinePaint = Paint().apply {
            color = android.graphics.Color.BLACK
            isAntiAlias = true
            style = Paint.Style.STROKE
            strokeWidth = 5f
        }

        canvas.drawPath(outlinePath, outlinePaint)
        canvas.drawPath(leftFacet, leftPaint)
        canvas.drawPath(centerFacet, centerPaint)
        canvas.drawPath(rightFacet, rightPaint)

        return bitmap
    }
}
