package com.example.hawkwind.perf
object Js1c18PolarEvo {
  const val A = -0.00016277
  const val B = -0.01291769
  const val C = -0.17504047
  fun sinkMs(tasMs: Double): Double = A*tasMs*tasMs + B*tasMs + C
  fun sinkMsAtKmh(kmh: Double): Double = sinkMs(kmh/3.6)
}
