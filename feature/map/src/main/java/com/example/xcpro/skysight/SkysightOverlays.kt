package com.example.xcpro.skysight

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Satellite
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.xcpro.common.debug.DebugUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.ResponseBody
import java.text.SimpleDateFormat
import java.util.*

class SkysightTileSource(
    private val skysightClient: SkysightClient,
    private val overlayType: SkysightOverlayType
) {
    
    suspend fun getTileUrl(z: Int, x: Int, y: Int, datetime: Date = Date()): String {
        val apiKey = skysightClient.getApiKey()
        
        val dateFormat = SimpleDateFormat("yyyy/MM/dd/HHmm", Locale.US)
        dateFormat.timeZone = TimeZone.getTimeZone("UTC")
        val datePath = dateFormat.format(datetime)
        val parts = datePath.split("/")
        
        return when (overlayType) {
            SkysightOverlayType.SATELLITE -> 
                "https://skysight.io/api/satellite/$z/$x/$y/${parts[0]}/${parts[1]}/${parts[2]}/${parts[3]}"
            SkysightOverlayType.RAIN -> 
                "https://skysight.io/api/rain/$z/$x/$y/${parts[0]}/${parts[1]}/${parts[2]}/${parts[3]}"
        }
    }
    
    suspend fun downloadTile(z: Int, x: Int, y: Int, datetime: Date = Date()): Result<ByteArray> {
        return try {
            val apiKey = skysightClient.getApiKey()
            
            val dateFormat = SimpleDateFormat("yyyy/MM/dd/HHmm", Locale.US)
            dateFormat.timeZone = TimeZone.getTimeZone("UTC")
            val datePath = dateFormat.format(datetime)
            val parts = datePath.split("/")
            
            val response = when (overlayType) {
                SkysightOverlayType.SATELLITE -> {
                    skysightClient.getTileApi().getSatelliteTile(
                        apiKey, z.toString(), x.toString(), y.toString(),
                        parts[0], parts[1], parts[2], parts[3]
                    )
                }
                SkysightOverlayType.RAIN -> {
                    skysightClient.getTileApi().getRainTile(
                        apiKey, z.toString(), x.toString(), y.toString(),
                        parts[0], parts[1], parts[2], parts[3]
                    )
                }
            }
            
            if (response.isSuccessful) {
                response.body()?.bytes()?.let { bytes ->
                    Result.success(bytes)
                } ?: Result.failure(Exception("Empty response body"))
            } else {
                Result.failure(Exception("HTTP ${response.code()}"))
            }
        } catch (e: Exception) {
            DebugUtils.logError("SkysightTileSource", "Error downloading tile", e)
            Result.failure(e)
        }
    }
}

enum class SkysightOverlayType {
    SATELLITE,
    RAIN
}

@Composable
fun SkysightOverlayControls(
    skysightClient: SkysightClient,
    onOverlayToggle: (SkysightOverlayType, Boolean) -> Unit
) {
    var showSatellite by remember { mutableStateOf(skysightClient.getShowSatellite()) }
    var showRain by remember { mutableStateOf(skysightClient.getShowRain()) }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Weather Overlays",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Satellite,
                    contentDescription = "Satellite",
                    tint = if (showSatellite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Satellite Imagery")
                Spacer(modifier = Modifier.weight(1f))
                Switch(
                    checked = showSatellite,
                    onCheckedChange = { enabled ->
                        showSatellite = enabled
                        skysightClient.setShowSatellite(enabled)
                        onOverlayToggle(SkysightOverlayType.SATELLITE, enabled)
                    }
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Cloud,
                    contentDescription = "Rain",
                    tint = if (showRain) Color.Blue else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Rain Forecast")
                Spacer(modifier = Modifier.weight(1f))
                Switch(
                    checked = showRain,
                    onCheckedChange = { enabled ->
                        showRain = enabled
                        skysightClient.setShowRain(enabled)
                        onOverlayToggle(SkysightOverlayType.RAIN, enabled)
                    }
                )
            }
        }
    }
}

@Composable
fun SkysightTimeSlider(
    currentTime: Date,
    onTimeChange: (Date) -> Unit
) {
    var sliderValue by remember { mutableStateOf(0f) }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Forecast Time",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            val timeFormat = SimpleDateFormat("HH:mm 'UTC'", Locale.US)
            timeFormat.timeZone = TimeZone.getTimeZone("UTC")
            
            Text(
                text = timeFormat.format(currentTime),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )
            
            Slider(
                value = sliderValue,
                onValueChange = { value ->
                    sliderValue = value
                    val hoursOffset = (value * 24).toInt()
                    val newTime = Date(System.currentTimeMillis() + hoursOffset * 3600000L)
                    onTimeChange(newTime)
                },
                valueRange = 0f..1f,
                modifier = Modifier.fillMaxWidth()
            )
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Now",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "+24h",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
