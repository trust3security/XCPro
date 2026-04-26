package com.trust3.xcpro.simulator.condor

internal object CondorTcpPortSpec {
    const val DEFAULT_PORT: Int = 4_353
    const val MIN_PORT: Int = 1
    const val MAX_PORT: Int = 65_535

    fun isValid(port: Int): Boolean = port in MIN_PORT..MAX_PORT
}
