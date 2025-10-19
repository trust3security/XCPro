package com.example.hawkwind.repo
import com.example.hawkwind.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import com.example.hawkwind.vario.TEVario

class HawkRepo(private val mode: String) {
  private val scope = CoroutineScope(Dispatchers.Default)
  private val _wind = MutableStateFlow(WindUiState())
  private val _vario = MutableStateFlow(VarioUiState())
  private val _ahrs  = MutableStateFlow(AhrsUiState())

  private var sim: Simulator? = null
  private var real: RealDataSource? = null

  init {
    when (mode) {
      "SIM" -> startSim()
      "REAL" -> startReal()
      else -> startSim()
    }
  }

  private fun startSim() {
    sim = Simulator(scope) { s ->
      val te = TEVario.compute(s.baroVzMs, s.tasMs, s.tasDot)
      _wind.value = _wind.value.copy(instant = InstantWind(s.wsKt, s.wdDeg, s.wMs),
                                     avg = InstantWind(s.wsKtAvg, s.wdDegAvg, s.wMsAvg),
                                     confidence = s.confidencePct)
      _vario.value = _vario.value.copy(tekVzMs = te, ekfVzMs = s.vzEkf)
      _ahrs.value  = _ahrs.value.copy(rollDeg = s.roll, pitchDeg = s.pitch, yawDeg = s.yaw, turnRateDegs = s.turnRate, gLoad = s.gLoad)
    }
    sim?.enable(true)
  }

  private fun startReal() {
    
    real = RealDataSource(scope) { s ->
      _wind.value = _wind.value.copy(
        instant = InstantWind(s.wsKt, s.wdDeg, s.wMs),
        avg = InstantWind(s.wsKtAvg, s.wdDegAvg, s.wMsAvg),
        confidence = s.confidencePct
      )
      _vario.value = _vario.value.copy(tekVzMs = s.vzTe, ekfVzMs = s.vzEkf)
      _ahrs.value = _ahrs.value.copy(rollDeg = s.roll, pitchDeg = s.pitch, yawDeg = s.yaw, turnRateDegs = s.turnRate, gLoad = s.gLoad)
    }
    real?.start()

  }

  fun wind(): StateFlow<WindUiState> = _wind.asStateFlow()
  fun vario(): StateFlow<VarioUiState> = _vario.asStateFlow()
  fun ahrs(): StateFlow<AhrsUiState> = _ahrs.asStateFlow()
}
