package com.example.xcpro.map

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path

internal fun createForecastWindArrowBitmap(
    colorArgb: Int = FORECAST_WIND_GLYPH_COLOR_BLACK
): Bitmap {
    val bitmap = Bitmap.createBitmap(
        FORECAST_WIND_ARROW_ICON_BITMAP_SIZE_PX,
        FORECAST_WIND_ARROW_ICON_BITMAP_SIZE_PX,
        Bitmap.Config.ARGB_8888
    )
    val canvas = Canvas(bitmap)
    val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colorArgb
        style = Paint.Style.FILL
    }
    val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = FORECAST_WIND_GLYPH_OUTLINE_COLOR_BLACK
        style = Paint.Style.STROKE
        strokeWidth = FORECAST_WIND_ARROW_STROKE_WIDTH_PX
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }
    val center = FORECAST_WIND_ARROW_ICON_BITMAP_SIZE_PX / 2f
    val tipY = FORECAST_WIND_ARROW_PADDING_PX
    val tailY = FORECAST_WIND_ARROW_ICON_BITMAP_SIZE_PX - FORECAST_WIND_ARROW_PADDING_PX
    val wingHalf = FORECAST_WIND_ARROW_ICON_BITMAP_SIZE_PX * 0.24f
    val shaftHalf = FORECAST_WIND_ARROW_ICON_BITMAP_SIZE_PX * 0.09f

    val path = Path().apply {
        moveTo(center, tipY)
        lineTo(center + wingHalf, center + FORECAST_WIND_ARROW_ICON_BITMAP_SIZE_PX * 0.08f)
        lineTo(center + shaftHalf, center + FORECAST_WIND_ARROW_ICON_BITMAP_SIZE_PX * 0.08f)
        lineTo(center + shaftHalf, tailY)
        lineTo(center - shaftHalf, tailY)
        lineTo(center - shaftHalf, center + FORECAST_WIND_ARROW_ICON_BITMAP_SIZE_PX * 0.08f)
        lineTo(center - wingHalf, center + FORECAST_WIND_ARROW_ICON_BITMAP_SIZE_PX * 0.08f)
        close()
    }
    canvas.drawPath(path, fillPaint)
    canvas.drawPath(path, strokePaint)
    return bitmap
}

internal fun createForecastWindBarbBitmap(speedKtBucket: Int): Bitmap {
    val bitmap = Bitmap.createBitmap(
        FORECAST_WIND_BARB_ICON_BITMAP_SIZE_PX,
        FORECAST_WIND_BARB_ICON_BITMAP_SIZE_PX,
        Bitmap.Config.ARGB_8888
    )
    val canvas = Canvas(bitmap)
    val outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = FORECAST_BARB_OUTLINE_COLOR
        style = Paint.Style.STROKE
        strokeWidth = FORECAST_WIND_BARB_STROKE_WIDTH_PX + 2f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = FORECAST_BARB_FILL_COLOR
        style = Paint.Style.STROKE
        strokeWidth = FORECAST_WIND_BARB_STROKE_WIDTH_PX
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = FORECAST_BARB_FILL_COLOR
        style = Paint.Style.FILL
    }
    val fillOutlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = FORECAST_BARB_OUTLINE_COLOR
        style = Paint.Style.STROKE
        strokeWidth = 2f
        strokeJoin = Paint.Join.ROUND
    }

    val centerX = FORECAST_WIND_BARB_ICON_BITMAP_SIZE_PX / 2f
    val topY = FORECAST_WIND_BARB_PADDING_PX
    val bottomY = FORECAST_WIND_BARB_ICON_BITMAP_SIZE_PX - FORECAST_WIND_BARB_PADDING_PX
    canvas.drawLine(centerX, bottomY, centerX, topY, outlinePaint)
    canvas.drawLine(centerX, bottomY, centerX, topY, strokePaint)

    var remainingKt = speedKtBucket.coerceAtLeast(0)
    var markY = topY + FORECAST_WIND_BARB_MARK_SPACING_PX
    val maxFlagY = bottomY - FORECAST_WIND_BARB_MARK_SPACING_PX

    while (remainingKt >= 50 && markY <= maxFlagY) {
        val triangle = Path().apply {
            moveTo(centerX, markY)
            lineTo(centerX + FORECAST_WIND_BARB_MARK_LENGTH_PX, markY + FORECAST_WIND_BARB_MARK_SPACING_PX)
            lineTo(centerX, markY + FORECAST_WIND_BARB_MARK_SPACING_PX * 2f)
            close()
        }
        canvas.drawPath(triangle, fillPaint)
        canvas.drawPath(triangle, fillOutlinePaint)
        remainingKt -= 50
        markY += FORECAST_WIND_BARB_MARK_SPACING_PX * 2f
    }

    while (remainingKt >= 10 && markY <= maxFlagY) {
        canvas.drawLine(
            centerX,
            markY,
            centerX + FORECAST_WIND_BARB_MARK_LENGTH_PX,
            markY + FORECAST_WIND_BARB_MARK_SPACING_PX,
            outlinePaint
        )
        canvas.drawLine(
            centerX,
            markY,
            centerX + FORECAST_WIND_BARB_MARK_LENGTH_PX,
            markY + FORECAST_WIND_BARB_MARK_SPACING_PX,
            strokePaint
        )
        remainingKt -= 10
        markY += FORECAST_WIND_BARB_MARK_SPACING_PX
    }

    if (remainingKt >= 5 && markY <= maxFlagY) {
        canvas.drawLine(
            centerX,
            markY,
            centerX + FORECAST_WIND_BARB_MARK_LENGTH_PX * 0.55f,
            markY + FORECAST_WIND_BARB_MARK_SPACING_PX * 0.55f,
            outlinePaint
        )
        canvas.drawLine(
            centerX,
            markY,
            centerX + FORECAST_WIND_BARB_MARK_LENGTH_PX * 0.55f,
            markY + FORECAST_WIND_BARB_MARK_SPACING_PX * 0.55f,
            strokePaint
        )
    }

    return bitmap
}

private const val FORECAST_WIND_ARROW_ICON_BITMAP_SIZE_PX = 96
private const val FORECAST_WIND_ARROW_PADDING_PX = 8f
private const val FORECAST_WIND_ARROW_STROKE_WIDTH_PX = 3f
private const val FORECAST_WIND_BARB_ICON_BITMAP_SIZE_PX = 96
private const val FORECAST_WIND_BARB_PADDING_PX = 10f
private const val FORECAST_WIND_BARB_STROKE_WIDTH_PX = 4f
private const val FORECAST_WIND_BARB_MARK_SPACING_PX = 10f
private const val FORECAST_WIND_BARB_MARK_LENGTH_PX = 28f
private val FORECAST_WIND_GLYPH_COLOR_BLACK = 0xFF000000.toInt()
private val FORECAST_WIND_GLYPH_OUTLINE_COLOR_BLACK = FORECAST_WIND_GLYPH_COLOR_BLACK
private val FORECAST_BARB_FILL_COLOR = FORECAST_WIND_GLYPH_COLOR_BLACK
private val FORECAST_BARB_OUTLINE_COLOR = FORECAST_WIND_GLYPH_COLOR_BLACK
