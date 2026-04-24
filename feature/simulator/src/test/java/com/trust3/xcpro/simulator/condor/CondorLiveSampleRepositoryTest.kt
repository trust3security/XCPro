package com.trust3.xcpro.simulator.condor

import com.trust3.xcpro.bluetooth.NmeaLine
import com.trust3.xcpro.core.time.FakeClock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
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

    @Test
    fun gga_publishes_gps_fix_without_motion_when_rmc_is_missing() {
        val fixture = repositoryWithWind()

        fixture.repository.onLines(listOf(ggaLine(receivedMonoMs = 1_000L)))

        val gps = requireNotNull(fixture.repository.gpsFlow.value)
        assertEquals(48.1173, gps.latitude, 0.0001)
        assertEquals(11.5167, gps.longitude, 0.0001)
        assertEquals(545.4, gps.altitude.value, 0.0)
        assertEquals(0.0, gps.speed.value, 0.0)
        assertEquals(0.0, gps.bearing, 0.0)
        assertEquals(10_000L, gps.timestamp)
        assertEquals(1_000L, gps.monotonicTimestampMillis)
    }

    @Test
    fun rmc_without_gga_does_not_publish_gps() {
        val fixture = repositoryWithWind()

        fixture.repository.onLines(listOf(rmcLine(receivedMonoMs = 1_100L)))

        assertNull(fixture.repository.gpsFlow.value)
    }

    @Test
    fun gga_after_cached_rmc_uses_cached_motion() {
        val fixture = repositoryWithWind()

        fixture.repository.onLines(listOf(rmcLine(receivedMonoMs = 900L)))
        fixture.repository.onLines(listOf(ggaLine(receivedMonoMs = 1_000L)))

        val gps = requireNotNull(fixture.repository.gpsFlow.value)
        assertEquals(11.5236, gps.speed.value, 0.0001)
        assertEquals(84.4, gps.bearing, 0.0)
        assertEquals(1_000L, gps.monotonicTimestampMillis)
    }

    @Test
    fun gga_followed_by_rmc_does_not_publish_a_second_gps_fix() = runTest {
        val fixture = repositoryWithWind()
        val monotonicEmissions = mutableListOf<Long?>()
        val collector = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            fixture.repository.gpsFlow.collect { gps ->
                monotonicEmissions += gps?.monotonicTimestampMillis
            }
        }
        runCurrent()

        fixture.repository.onLines(listOf(ggaLine(receivedMonoMs = 1_000L)))
        runCurrent()
        fixture.repository.onLines(listOf(rmcLine(receivedMonoMs = 1_100L)))
        runCurrent()

        assertEquals(listOf(null, 1_000L), monotonicEmissions)
        collector.cancel()
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

    private fun ggaLine(receivedMonoMs: Long): NmeaLine =
        line(
            withChecksum("GPGGA,123519,4807.038,N,01131.000,E,1,08,0.9,545.4,M,46.9,M,,"),
            receivedMonoMs
        )

    private fun rmcLine(receivedMonoMs: Long): NmeaLine =
        line(
            withChecksum("GPRMC,123519,A,4807.038,N,01131.000,E,022.4,084.4,230394,003.1,W"),
            receivedMonoMs
        )

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
