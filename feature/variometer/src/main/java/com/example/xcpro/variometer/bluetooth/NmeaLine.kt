package com.example.xcpro.variometer.bluetooth

data class NmeaLine(
    val text: String,
    val receivedMonoMs: Long
)
