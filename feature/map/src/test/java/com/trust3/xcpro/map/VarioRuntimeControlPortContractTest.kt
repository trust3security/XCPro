package com.trust3.xcpro.map

import org.junit.Assert.assertEquals
import org.junit.Test

class VarioRuntimeControlPortContractTest {

    @Test
    fun publicApi_remains_caller_agnostic() {
        val methods = VarioRuntimeControlPort::class.java.declaredMethods
            .sortedBy { it.name }

        assertEquals(
            listOf("ensureRunningIfPermitted", "requestStop"),
            methods.map { it.name }
        )

        val ensureRunning = methods.first { it.name == "ensureRunningIfPermitted" }
        assertEquals(0, ensureRunning.parameterCount)
        assertEquals(Boolean::class.javaPrimitiveType, ensureRunning.returnType)

        val requestStop = methods.first { it.name == "requestStop" }
        assertEquals(0, requestStop.parameterCount)
        assertEquals(Void.TYPE, requestStop.returnType)
    }
}
