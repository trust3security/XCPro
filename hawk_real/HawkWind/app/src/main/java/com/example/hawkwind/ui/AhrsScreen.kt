package com.example.hawkwind.ui
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.hawkwind.viewmodel.AhrsViewModel

@Composable
fun AhrsScreen(mode: String) {
  val vm = remember(mode) { AhrsViewModel(mode) }
  val st by vm.state.collectAsState()
  Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
    Text("AHRS (" + mode + ")", style = MaterialTheme.typography.headlineMedium)
    Spacer(Modifier.height(8.dp))
    Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
      Text("Roll ${"%.1f".format(st.rollDeg)}°  Pitch ${"%.1f".format(st.pitchDeg)}°  Yaw ${"%.1f".format(st.yawDeg)}°")
    }
    Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
      Text("Turn ${"%.1f".format(st.turnRateDegs)}°/s")
      Text("Bank ${"%.1f".format(st.rollDeg)}°")
      Text("G ${"%.2f".format(st.gLoad)}")
    }
  }
}
