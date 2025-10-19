package com.example.hawkwind.vario
object TEVario {
  private const val G = 9.80665
  fun compute(baroVzMs: Double, tasMs: Double, tasDotMs2: Double): Double = baroVzMs + (tasMs / G) * tasDotMs2
}
