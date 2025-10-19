package com.example.hawkwind.ui
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import kotlin.math.sin
import kotlin.math.cos
import com.example.hawkwind.viewmodel.WindViewModel

@Composable
fun WindScreen(mode: String) {
  val vm = remember(mode) { WindViewModel(mode) }
  val state by vm.state.collectAsState()
  Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
    Text("Wind (" + mode + ")", style = MaterialTheme.typography.headlineMedium); Spacer(Modifier.height(8.dp))
    Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
      Canvas(Modifier.size(300.dp)) {
        val r = size.minDimension / 2f * 0.85f
        drawCircle(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f))
        val d  = Math.toRadians(state.instant.windDirDeg.toDouble())
        val l  = (r * (state.instant.windSpeed / state.ui.maxWindForScale)).coerceIn(0f, r)
        val e  = Offset((size.width/2f + l * sin(d)).toFloat(), (size.height/2f - l * cos(d)).toFloat())
        drawLine(color = MaterialTheme.colorScheme.primary, start = Offset(size.width/2f, size.height/2f), end = e, strokeWidth = 6f)
        val ad = Math.toRadians(state.avg.windDirDeg.toDouble())
        val al = (r * (state.avg.windSpeed / state.ui.maxWindForScale)).coerceIn(0f, r)
        val ae = Offset((size.width/2f + al * sin(ad)).toFloat(), (size.height/2f - al * cos(ad)).toFloat())
        drawLine(color = MaterialTheme.colorScheme.onSurface, start = Offset(size.width/2f, size.height/2f), end = ae, strokeWidth = 3f)
      }
    }
    Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
      Text("WS ${"%.1f".format(state.instant.windSpeed)} kt")
      Text("WD ${"%.0f".format(state.instant.windDirDeg)}°")
      Text("WV ${"%.1f".format(state.instant.wVerticalMs)} m/s")
      Text("CONF ${state.confidence}%")
    }
  }
}
