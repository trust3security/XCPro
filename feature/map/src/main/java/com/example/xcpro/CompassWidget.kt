package com.example.xcpro

import android.util.Log
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawStyle
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.xcpro.common.orientation.MapOrientationMode
import com.example.xcpro.common.orientation.OrientationData
import kotlin.math.*

@Composable
fun CompassWidget(
    orientation: OrientationData,
    onModeToggle: () -> Unit,
    modifier: Modifier = Modifier,
    widgetSize: androidx.compose.ui.unit.Dp = 48.dp
) {
    val TAG = "CompassWidget"

    // Log compass widget composition (only occasionally to avoid spam)
    LaunchedEffect(orientation.mode, orientation.isValid) {
        Log.d(TAG, "🧭 CompassWidget composing: mode=${orientation.mode}, " +
                  "bearing=${orientation.bearing.toInt()}°, valid=${orientation.isValid}, " +
                  "size=${widgetSize}")
    }

    // Animate bearing changes smoothly
    val animatedBearing by animateFloatAsState(
        targetValue = orientation.bearing.toFloat(),
        animationSpec = tween(
            durationMillis = if (orientation.mode == MapOrientationMode.NORTH_UP) 0 else 300,
            easing = androidx.compose.animation.core.FastOutSlowInEasing
        ),
        label = "bearing_animation"
    )

    // Log bearing animation changes
    LaunchedEffect(animatedBearing) {
        if (System.currentTimeMillis() % 2000 < 100) { // Log every 2 seconds to avoid spam
            Log.d(TAG, "🎯 Bearing animation: target=${orientation.bearing.toInt()}°, " +
                      "animated=${animatedBearing.toInt()}°")
        }
    }

    Box(
        modifier = modifier
            .size(widgetSize)
            .shadow(
                elevation = 4.dp,
                shape = CircleShape,
                ambientColor = Color.Black.copy(alpha = 0.1f),
                spotColor = Color.Black.copy(alpha = 0.2f)
            )
            .clip(CircleShape)
            .background(
                color = MaterialTheme.colorScheme.surface,
                shape = CircleShape
            )
            .clickable {
                Log.d(TAG, "🖱️ Compass clicked - toggling mode from ${orientation.mode}")
                onModeToggle()
            }
            .padding(4.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier.fillMaxSize()
        ) {
            // Log canvas drawing (only occasionally to avoid spam)
            if (System.currentTimeMillis() % 5000 < 100) { // Every 5 seconds
                Log.d(TAG, "🎨 Canvas drawing: size=${size.width}x${size.height}, " +
                          "rotation=${when (orientation.mode) {
                              MapOrientationMode.NORTH_UP -> 0f
                              MapOrientationMode.TRACK_UP -> -animatedBearing
                              MapOrientationMode.HEADING_UP -> -animatedBearing
                          }}°")
            }
            val centerX = size.width / 2f
            val centerY = size.height / 2f
            val radius = minOf(centerX, centerY) * 0.8f
            val center = Offset(centerX, centerY)

            // Draw outer circle background
            drawCircle(
                color = if (orientation.isValid) Color.White else Color.Gray.copy(alpha = 0.7f),
                radius = radius,
                center = center,
                style = Fill
            )

            // Draw border
            drawCircle(
                color = Color.Gray.copy(alpha = 0.3f),
                radius = radius,
                center = center,
                style = Stroke(width = 2f)
            )

            // Rotation based on mode
            val rotation = when (orientation.mode) {
                MapOrientationMode.NORTH_UP -> 0f
                MapOrientationMode.TRACK_UP -> -animatedBearing
                MapOrientationMode.HEADING_UP -> -animatedBearing
            }

            rotate(degrees = rotation, pivot = center) {
                // Draw cardinal direction marks
                for (i in 0 until 4) {
                    val angle = i * 90.0 // N, E, S, W
                    val radians = Math.toRadians(angle)
                    val cos = cos(radians).toFloat()
                    val sin = sin(radians).toFloat()

                    val markLength = radius * 0.15f
                    val startRadius = radius - markLength
                    val endRadius = radius

                    val startX = center.x + cos * startRadius
                    val startY = center.y + sin * startRadius
                    val endX = center.x + cos * endRadius
                    val endY = center.y + sin * endRadius

                    drawLine(
                        color = Color.Gray.copy(alpha = 0.6f),
                        start = Offset(startX, startY),
                        end = Offset(endX, endY),
                        strokeWidth = 4f
                    )
                }

                // Draw north needle (red triangle)
                val needleLength = radius * 0.7f
                val needleWidth = radius * 0.08f
                val needleColor = if (orientation.isValid) Color.Red else Color.Gray

                val northTip = Offset(center.x, center.y - needleLength)
                val northLeft = Offset(center.x - needleWidth, center.y - needleLength * 0.3f)
                val northRight = Offset(center.x + needleWidth, center.y - needleLength * 0.3f)

                val northPath = Path().apply {
                    moveTo(northTip.x, northTip.y)
                    lineTo(northLeft.x, northLeft.y)
                    lineTo(center.x, center.y)
                    lineTo(northRight.x, northRight.y)
                    close()
                }

                drawPath(
                    path = northPath,
                    color = needleColor,
                    style = Fill
                )

                // Draw south needle (white triangle)
                val southTip = Offset(center.x, center.y + needleLength)
                val southLeft = Offset(center.x - needleWidth, center.y + needleLength * 0.3f)
                val southRight = Offset(center.x + needleWidth, center.y + needleLength * 0.3f)

                val southPath = Path().apply {
                    moveTo(southTip.x, southTip.y)
                    lineTo(southLeft.x, southLeft.y)
                    lineTo(center.x, center.y)
                    lineTo(southRight.x, southRight.y)
                    close()
                }

                drawPath(
                    path = southPath,
                    color = Color.White,
                    style = Fill
                )

                drawPath(
                    path = southPath,
                    color = Color.Gray.copy(alpha = 0.8f),
                    style = Stroke(width = 2f)
                )

                // Center dot
                drawCircle(
                    color = needleColor,
                    radius = needleWidth * 0.4f,
                    center = center,
                    style = Fill
                )
            }
        }

        // Mode indicator text overlay
        if (orientation.mode != MapOrientationMode.NORTH_UP) {
            Text(
                text = when (orientation.mode) {
                    MapOrientationMode.TRACK_UP -> "T"
                    MapOrientationMode.HEADING_UP -> "H"
                    else -> ""
                },
                style = TextStyle(
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                ),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .offset(y = (-2).dp)
            )
        }
    }
}
