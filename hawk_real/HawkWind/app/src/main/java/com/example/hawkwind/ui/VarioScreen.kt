package com.example.hawkwind.ui
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import com.example.hawkwind.viewmodel.VarioViewModel

@Composable fun VarioScreen(mode: String) {
  val vm = remember(mode) { VarioViewModel(mode) }
  val st by vm.state.collectAsState()
  Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
    Text("Dual-Needle Vario (TE vs EKF) (" + mode + ")", style = MaterialTheme.typography.headlineMedium); Spacer(Modifier.height(8.dp))
    Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
      Canvas(Modifier.size(300.dp)) {
        val r = size.minDimension / 2f * 0.85f; drawCircle(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f))
        fun ang(v: Double): Double { val s = st.ui.scaleMs; val c = v.coerceIn(-s, s); return (c/s)*120.0 }
        rotate(ang(st.tekVzMs).toFloat()) { drawLine(Offset(size.width/2f, size.height/2f), Offset(size.width/2f, size.height/2f - r), 6f) }
        rotate(ang(st.ekfVzMs).toFloat()) { drawLine(Offset(size.width/2f, size.height/2f), Offset(size.width/2f, size.height/2f - r*0.9f), 4f) }
      }
    }
    Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
      Text("TE ${"%.2f".format(st.tekVzMs)} m/s"); Text("EKF ${"%.2f".format(st.ekfVzMs)} m/s"); Text("Δ ${"%.2f".format(st.ekfVzMs - st.tekVzMs)}")
    }
  }
}
