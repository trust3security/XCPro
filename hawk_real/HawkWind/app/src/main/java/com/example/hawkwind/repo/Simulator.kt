package com.example.hawkwind.repo
import kotlinx.coroutines.*
import kotlin.math.*
class Simulator(private val scope: CoroutineScope, private val onTick: (State) -> Unit) {
  data class State(
    val wsKt: Float = 18f, val wdDeg: Float = 235f, val wMs: Float = 0.6f,
    val wsKtAvg: Float = 16f, val wdDegAvg: Float = 240f, val wMsAvg: Float = 0.4f,
    val confidencePct: Int = 82,
    val vzTe: Double = 1.8, val vzEkf: Double = 2.1,
    val roll: Double = 15.0, val pitch: Double = 2.0, val yaw: Double = 90.0,
    val turnRate: Double = 2.0, val gLoad: Double = 1.05,
    val tasMs: Double = 45.0, val tasDot: Double = 0.0, val baroVzMs: Double = 0.0
  )
  @Volatile private var s = State()
  private var job: Job? = null
  fun enable(v: Boolean) { if (v) start() else stop() }
  private fun start() {
    if (job != null) return
    job = scope.launch(Dispatchers.Default) {
      val dt = 0.05; var t = 0.0; val alpha = 0.05
      while (isActive) {
        val wave = sin(2*PI*0.25*t); val gust = 0.3*sin(2*PI*0.75*t+1.0)
        val vzEkf = 1.2*wave + 0.3*gust; val vzTe = vzEkf + 0.2*sin(2*PI*0.25*t + PI/3)
        val ws = 16.0 + 4.0*sin(2*PI*0.02*t) + gust; val wd = 230.0 + 15.0*sin(2*PI*0.01*t+0.5); val wv = 0.5 + 0.3*wave
        val wsAvg = (alpha*ws + (1-alpha)*s.wsKtAvg).toFloat(); val wdAvg = (alpha*wd + (1-alpha)*s.wdDegAvg).toFloat(); val wvAvg = (alpha*wv + (1-alpha)*s.wMsAvg).toFloat()
        val baseTas = 45.0 + 5.0*sin(2*PI*0.03*t); val newTas = baseTas + 1.5*gust; val tasDot = (newTas - s.tasMs)/dt; val baroVzMs = vzEkf + 0.1*sin(2*PI*0.8*t)
        s = s.copy(wsKt=ws.toFloat(), wdDeg=wd.toFloat(), wMs=wv.toFloat(), wsKtAvg=wsAvg, wdDegAvg=wdAvg, wMsAvg=wvAvg, vzEkf=vzEkf, vzTe=vzTe, tasMs=newTas, tasDot=tasDot, baroVzMs=baroVzMs,
                   roll=20*sin(2*PI*0.01*t), pitch=3*sin(2*PI*0.015*t+0.7), yaw=(s.yaw+5*dt)%360, turnRate=3*sin(2*PI*0.02*t+0.3), gLoad=1.0+0.05*sin(2*PI*0.5*t))
        onTick(s); delay(int((dt*1000).toDouble()).toLong()); t += dt
      }
    }
  }
  private fun stop() { job?.cancel(); job = null }
}
