package com.trust3.xcpro.simulator.condor

import com.trust3.xcpro.bluetooth.NmeaLine
import com.trust3.xcpro.core.time.FakeClock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class CondorLiveSampleRepositoryTest {

    @Test
    fun lxwp0_updates_external_snapshot_and_external_wind() {
        val clock = FakeClock(wallMs = 10_000L)
        val externalWindWritePort = TestExternalWindWritePort()
        val repository = CondorLiveSampleRepository(
            parser = CondorSentenceParser(),
            clock = clock,
            externalWindWritePort = externalWindWritePort
        )

        repository.onLines(
            listOf(line(withChecksum("LXWP0,Y,88.4,654.1,1.12,,,,,,239,174,10.1"), 1_200L))
        )

        assertNotNull(repository.airspeedFlow.value)
        assertEquals(654.1, repository.externalFlightSnapshot.value.pressureAltitudeM?.value ?: Double.NaN, 0.0)
        assertEquals(1.12, repository.externalFlightSnapshot.value.totalEnergyVarioMps?.value ?: Double.NaN, 0.0)
        assertEquals(1_200L, repository.externalFlightSnapshot.value.pressureAltitudeM?.receivedMonoMs)
        assertEquals(
            354.0,
            normalizeDegrees(externalWindWritePort.lastVector?.directionFromDeg ?: Double.NaN),
            0.0
        )
        assertEquals(10_000L, externalWindWritePort.lastTimestampMillis)
    }

    @Test
    fun clear_resets_condor_owned_outputs_and_external_wind() {
        val fixture = repositoryWithWind()

        fixture.repository.onLines(
            listOf(line(withChecksum("LXWP0,Y,88.4,654.1,1.12,,,,,,239,174,10.1"), 1_200L))
        )
        val windPort = fixture.windPort

        fixture.repository.clear()

        assertNull(fixture.repository.gpsFlow.value)
        assertNull(fixture.repository.airspeedFlow.value)
        assertNull(fixture.repository.externalFlightSnapshot.value.pressureAltitudeM)
        assertNull(fixture.repository.externalFlightSnapshot.value.totalEnergyVarioMps)
        assertEquals(1, windPort.clearCount)
        assertNull(windPort.lastVector)
    }

    @Test
    fun stream_stale_clears_external_wind_only() {
        val fixture = repositoryWithWind()
        fixture.repository.onLines(
            listOf(line(withChecksum("LXWP0,Y,88.4,654.1,1.12,,,,,,239,174,10.1"), 1_200L))
        )

        fixture.repository.onStreamStale()

        assertNotNull(fixture.repository.airspeedFlow.value)
        assertNotNull(fixture.repository.externalFlightSnapshot.value.pressureAltitudeM)
        assertNull(fixture.windPort.lastVector)
        assertEquals(1, fixture.windPort.clearCount)
    }

    private fun repositoryWithWind(): RepositoryFixture {
        val windPort = TestExternalWindWritePort()
        return RepositoryFixture(
            repository = CondorLiveSampleRepository(
                parser = CondorSentenceParser(),
                clock = FakeClock(wallMs = 10_000L),
                externalWindWritePort = windPort
            ),
            windPort = windPort
        )
    }

    private fun line(text: String, receivedMonoMs: Long): NmeaLine =
        NmeaLine(text = text, receivedMonoMs = receivedMonoMs)

    private fun withChecksum(payloadWithoutDollar: String): String {
        val checksum = payloadWithoutDollar.fold(0) { acc, character -> acc xor character.code }
        return "\$$payloadWithoutDollar*${checksum.toString(16).uppercase().padStart(2, '0')}"
    }

    private fun normalizeDegrees(value: Double): Double =
        ((value % 360.0) + 360.0) % 360.0

    private data class RepositoryFixture(
        val repository: CondorLiveSampleRepository,
        val windPort: TestExternalWindWritePort
    )
}
