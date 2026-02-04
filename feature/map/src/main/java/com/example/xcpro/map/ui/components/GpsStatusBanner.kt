package com.example.xcpro.map.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import com.example.xcpro.map.model.GpsStatusUiModel

@Composable
fun GpsStatusBanner(status: GpsStatusUiModel, modifier: Modifier = Modifier) {
    val (text, color) = when (status) {
        GpsStatusUiModel.NoPermission -> "Location permission needed" to Color(0xFFB00020)
        GpsStatusUiModel.Disabled -> "GPS is off" to Color(0xFFB00020)
        is GpsStatusUiModel.LostFix -> "Waiting for GPS" to Color(0xFFCA8A04)
        GpsStatusUiModel.Searching -> "Searching for GPS" to Color(0xFFCA8A04)
        is GpsStatusUiModel.Ok -> return // No banner when OK
    }
    Surface(
        color = color.copy(alpha = 0.85f),
        tonalElevation = 4.dp,
        shape = RoundedCornerShape(10.dp),
        modifier = modifier
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            color = Color.White,
            style = MaterialTheme.typography.labelMedium,
            textAlign = TextAlign.Center
        )
    }
}
