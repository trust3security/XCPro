package com.example.xcpro

import android.util.Log
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.xcpro.common.orientation.MapOrientationMode
import com.example.xcpro.common.orientation.OrientationData
import com.example.xcpro.common.orientation.BearingSource
import com.example.xcpro.map.BuildConfig
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

@Composable
fun CompassWidget(
    orientation: OrientationData,
    onModeToggle: () -> Unit,
    modifier: Modifier = Modifier,
    widgetSize: Dp = 48.dp
) {
    val tag = "CompassWidget"

    LaunchedEffect(orientation.mode, orientation.isValid) {
        if (BuildConfig.DEBUG) {
            Log.d(
                tag,
                "CompassWidget composing: mode=${orientation.mode}, " +
                    "bearing=${orientation.bearing.toInt()}deg, valid=${orientation.isValid}, " +
                    "size=$widgetSize"
            )
        }
    }

    val animatedBearing by animateFloatAsState(
        targetValue = orientation.bearing.toFloat(),
        animationSpec = tween(
            durationMillis = if (orientation.mode == MapOrientationMode.NORTH_UP) 0 else 300,
            easing = FastOutSlowInEasing
        ),
        label = "bearing_animation"
    )

    LaunchedEffect(animatedBearing) {
        if (BuildConfig.DEBUG && System.currentTimeMillis() % 2000L < 100L) {
            Log.d(
                tag,
                "Bearing animation: target=${orientation.bearing.toInt()}deg, " +
                    "animated=${animatedBearing.toInt()}deg"
            )
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
            .background(MaterialTheme.colorScheme.surface, CircleShape)
            .clickable {
                if (BuildConfig.DEBUG) {
                    Log.d(tag, "Compass clicked - toggling mode from ${orientation.mode}")
                }
                onModeToggle()
            }
            .padding(4.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            if (BuildConfig.DEBUG && System.currentTimeMillis() % 5000L < 100L) {
                val rotationValue = when (orientation.mode) {
                    MapOrientationMode.NORTH_UP -> 0f
                    MapOrientationMode.TRACK_UP -> -animatedBearing
                    MapOrientationMode.HEADING_UP -> -animatedBearing
                    MapOrientationMode.WIND_UP -> -animatedBearing
                }
                Log.d(
                    tag,
                    "Canvas drawing: size=${size.width}x${size.height}, rotation=${rotationValue}deg"
                )
            }

            val centerX = size.width / 2f
            val centerY = size.height / 2f
            val radius = min(centerX, centerY) * 0.8f
            val center = Offset(centerX, centerY)

            drawCircle(
                color = if (orientation.isValid) Color.White else Color.Gray.copy(alpha = 0.7f),
                radius = radius,
                center = center,
                style = Fill
            )

            drawCircle(
                color = Color.Gray.copy(alpha = 0.3f),
                radius = radius,
                center = center,
                style = Stroke(width = 2f)
            )

            val rotation = when (orientation.mode) {
                MapOrientationMode.NORTH_UP -> 0f
                MapOrientationMode.TRACK_UP -> -animatedBearing
                MapOrientationMode.HEADING_UP -> -animatedBearing
                MapOrientationMode.WIND_UP -> -animatedBearing
            }

            rotate(degrees = rotation, pivot = center) {
                repeat(4) { index ->
                    val angle = index * 90.0
                    val radians = Math.toRadians(angle)
                    val cosValue = cos(radians).toFloat()
                    val sinValue = sin(radians).toFloat()
                    val markLength = radius * 0.15f
                    val startRadius = radius - markLength
                    val start = Offset(
                        x = center.x + cosValue * startRadius,
                        y = center.y + sinValue * startRadius
                    )
                    val end = Offset(
                        x = center.x + cosValue * radius,
                        y = center.y + sinValue * radius
                    )
                    drawLine(
                        color = Color.Gray.copy(alpha = 0.6f),
                        start = start,
                        end = end,
                        strokeWidth = 4f
                    )
                }

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

                drawPath(path = northPath, color = needleColor, style = Fill)

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

                drawPath(path = southPath, color = Color.White, style = Fill)
                drawPath(path = southPath, color = Color.Gray.copy(alpha = 0.8f), style = Stroke(width = 2f))

                drawCircle(
                    color = needleColor,
                    radius = needleWidth * 0.4f,
                    center = center,
                    style = Fill
                )
            }
        }

        val modeBadge = when (orientation.mode) {
            MapOrientationMode.TRACK_UP -> "T"
            MapOrientationMode.HEADING_UP -> "H"
            MapOrientationMode.WIND_UP -> "W"
            else -> ""
        }
        if (modeBadge.isNotEmpty()) {
            Text(
                text = modeBadge,
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

        val sourceLabel = when (orientation.bearingSource) {
            BearingSource.COMPASS -> "C"
            BearingSource.WIND -> "W"
            BearingSource.TRACK -> "T"
            BearingSource.LAST_KNOWN -> "LK"
            BearingSource.NONE -> ""
        }
        if (sourceLabel.isNotEmpty()) {
            Text(
                text = sourceLabel,
                style = TextStyle(
                    fontSize = 7.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (orientation.isValid) MaterialTheme.colorScheme.secondary else Color.Gray
                ),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = 2.dp)
            )
        }
    }
}
