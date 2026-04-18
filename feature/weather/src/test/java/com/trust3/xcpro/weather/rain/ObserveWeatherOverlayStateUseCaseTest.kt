package com.trust3.xcpro.weather.rain

import com.trust3.xcpro.core.time.FakeClock
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class ObserveWeatherOverlayStateUseCaseTest {

    private val preferencesRepository: WeatherOverlayPreferencesRepository = mock()
    private val metadataRepository: WeatherRadarMetadataRepository = mock()
    private val clock = FakeClock(wallMs = 1_000_000L)

    @Test
    fun invoke_selectsFrameWithinTenMinuteWindow() = runTest {
        val metadataState = metadataState(lastSuccessfulFetchWallMs = 999_000L)
        val preferences = basePreferences.copy(
            animationWindow = WeatherRainAnimationWindow.TEN_MINUTES
        )
        whenever(preferencesRepository.preferencesFlow).thenReturn(flowOf(preferences))
        whenever(preferencesRepository.enabledFlow).thenReturn(flowOf(false))
        whenever(metadataRepository.currentState()).thenReturn(metadataState)

        val state = createUseCase().invoke().first()

        assertNotNull(state.selectedFrame)
        assertEquals(1_200L, state.selectedFrame?.frameTimeEpochSec)
    }

    @Test
    fun invoke_selectsFrameWithinTwentyMinuteWindow() = runTest {
        val metadataState = metadataState(lastSuccessfulFetchWallMs = 999_000L)
        val preferences = basePreferences.copy(
            animationWindow = WeatherRainAnimationWindow.TWENTY_MINUTES
        )
        whenever(preferencesRepository.preferencesFlow).thenReturn(flowOf(preferences))
        whenever(preferencesRepository.enabledFlow).thenReturn(flowOf(false))
        whenever(metadataRepository.currentState()).thenReturn(metadataState)

        val state = createUseCase().invoke().first()

        assertNotNull(state.selectedFrame)
        assertEquals(600L, state.selectedFrame?.frameTimeEpochSec)
    }

    @Test
    fun invoke_selectsFrameWithinThirtyMinuteWindow() = runTest {
        val metadataState = metadataState(lastSuccessfulFetchWallMs = 999_000L)
        val preferences = basePreferences.copy(
            animationWindow = WeatherRainAnimationWindow.THIRTY_MINUTES
        )
        whenever(preferencesRepository.preferencesFlow).thenReturn(flowOf(preferences))
        whenever(preferencesRepository.enabledFlow).thenReturn(flowOf(false))
        whenever(metadataRepository.currentState()).thenReturn(metadataState)

        val state = createUseCase().invoke().first()

        assertNotNull(state.selectedFrame)
        assertEquals(0L, state.selectedFrame?.frameTimeEpochSec)
    }

    @Test
    fun invoke_selectsFrameWithinOneHundredTwentyMinuteWindow() = runTest {
        val metadataState = metadataState(lastSuccessfulFetchWallMs = 999_000L)
        val preferences = basePreferences.copy(
            animationWindow = WeatherRainAnimationWindow.ONE_HUNDRED_TWENTY_MINUTES
        )
        whenever(preferencesRepository.preferencesFlow).thenReturn(flowOf(preferences))
        whenever(preferencesRepository.enabledFlow).thenReturn(flowOf(false))
        whenever(metadataRepository.currentState()).thenReturn(metadataState)

        val state = createUseCase().invoke().first()

        assertNotNull(state.selectedFrame)
        assertEquals(0L, state.selectedFrame?.frameTimeEpochSec)
    }

    @Test
    fun invoke_keepsBoundaryFrameWhenThirtyMinuteWindowIsSparse() = runTest {
        val sparseMetadata = WeatherRadarMetadata(
            hostUrl = "https://tilecache.rainviewer.com",
            generatedEpochSec = 1_800L,
            pastFrames = listOf(
                WeatherRadarFrame(timeEpochSec = 0L, path = "/v2/radar/0"),
                WeatherRadarFrame(timeEpochSec = 1_200L, path = "/v2/radar/1200"),
                WeatherRadarFrame(timeEpochSec = 1_800L, path = "/v2/radar/1800")
            )
        )
        val metadataState = WeatherRadarMetadataState(
            status = WeatherRadarStatusCode.OK,
            metadata = sparseMetadata,
            lastSuccessfulFetchWallMs = 999_000L
        )
        val preferences = basePreferences.copy(
            animationWindow = WeatherRainAnimationWindow.THIRTY_MINUTES
        )
        whenever(preferencesRepository.preferencesFlow).thenReturn(flowOf(preferences))
        whenever(preferencesRepository.enabledFlow).thenReturn(flowOf(false))
        whenever(metadataRepository.currentState()).thenReturn(metadataState)

        val state = createUseCase().invoke().first()

        assertNotNull(state.selectedFrame)
        assertEquals(0L, state.selectedFrame?.frameTimeEpochSec)
    }

    @Test
    fun invoke_usesManualFrameModeWhenAnimationIsDisabled() = runTest {
        val metadataState = metadataState(lastSuccessfulFetchWallMs = 999_000L)
        val preferences = basePreferences.copy(
            animatePastWindow = false,
            frameMode = WeatherRadarFrameMode.MANUAL,
            manualFrameIndex = 2
        )
        whenever(preferencesRepository.preferencesFlow).thenReturn(flowOf(preferences))
        whenever(preferencesRepository.enabledFlow).thenReturn(flowOf(false))
        whenever(metadataRepository.currentState()).thenReturn(metadataState)

        val state = createUseCase().invoke().first()

        assertNotNull(state.selectedFrame)
        assertEquals(1_200L, state.selectedFrame?.frameTimeEpochSec)
    }

    @Test
    fun invoke_propagatesRenderOptionsFromPreferences() = runTest {
        val metadataState = metadataState(lastSuccessfulFetchWallMs = 999_000L)
        val preferences = basePreferences.copy(
            animatePastWindow = false,
            frameMode = WeatherRadarFrameMode.LATEST,
            renderOptions = WeatherRadarRenderOptions(
                smooth = false,
                snow = false
            )
        )
        whenever(preferencesRepository.preferencesFlow).thenReturn(flowOf(preferences))
        whenever(preferencesRepository.enabledFlow).thenReturn(flowOf(false))
        whenever(metadataRepository.currentState()).thenReturn(metadataState)

        val state = createUseCase().invoke().first()

        assertNotNull(state.selectedFrame)
        assertFalse(state.selectedFrame!!.renderOptions.smooth)
        assertFalse(state.selectedFrame!!.renderOptions.snow)
    }

    @Test
    fun invoke_marksMetadataAsStaleWhenLastSuccessTooOld() = runTest {
        val staleAgeMs = WEATHER_RAIN_METADATA_STALE_AFTER_MS + 1L
        val metadataState = metadataState(
            lastSuccessfulFetchWallMs = clock.nowWallMs() - staleAgeMs
        )
        whenever(preferencesRepository.preferencesFlow).thenReturn(flowOf(basePreferences))
        whenever(preferencesRepository.enabledFlow).thenReturn(flowOf(false))
        whenever(metadataRepository.currentState()).thenReturn(metadataState)

        val state = createUseCase().invoke().first()

        assertTrue(state.metadataStale)
        assertEquals(staleAgeMs, state.metadataFreshnessAgeMs)
    }

    @Test
    fun invoke_marksMetadataAsLiveWhenRecentAndOk() = runTest {
        val freshnessAgeMs = 45_000L
        val metadataState = metadataState(
            lastSuccessfulFetchWallMs = clock.nowWallMs() - freshnessAgeMs
        )
        whenever(preferencesRepository.preferencesFlow).thenReturn(flowOf(basePreferences))
        whenever(preferencesRepository.enabledFlow).thenReturn(flowOf(false))
        whenever(metadataRepository.currentState()).thenReturn(metadataState)

        val state = createUseCase().invoke().first()

        assertFalse(state.metadataStale)
        assertEquals(freshnessAgeMs, state.metadataFreshnessAgeMs)
    }

    @Test
    fun invoke_updatesMetadataAgeEvenWhenMetadataPayloadIsUnchanged() = runTest {
        val initialAgeMs = 30_000L
        val lastSuccessWallMs = clock.nowWallMs() - initialAgeMs
        val metadataState = metadataState(lastSuccessfulFetchWallMs = lastSuccessWallMs)
        val preferences = basePreferences.copy(
            enabled = true,
            animatePastWindow = false
        )
        whenever(preferencesRepository.preferencesFlow).thenReturn(flowOf(preferences))
        whenever(preferencesRepository.enabledFlow).thenReturn(flowOf(true))
        whenever(metadataRepository.currentState()).thenReturn(metadataState)
        whenever(metadataRepository.refreshMetadata()).thenReturn(metadataState)

        val states = mutableListOf<WeatherOverlayRuntimeState>()
        val collectJob = backgroundScope.launch {
            createUseCase().invoke().take(2).toList(states)
        }

        runCurrent()
        assertEquals(1, states.size)
        assertEquals(initialAgeMs, states[0].metadataFreshnessAgeMs)

        clock.advanceWallMs(WEATHER_RAIN_METADATA_REFRESH_INTERVAL_OK_MS)
        advanceTimeBy(WEATHER_RAIN_METADATA_REFRESH_INTERVAL_OK_MS)
        runCurrent()

        collectJob.join()
        assertEquals(2, states.size)
        assertEquals(
            initialAgeMs + WEATHER_RAIN_METADATA_REFRESH_INTERVAL_OK_MS,
            states[1].metadataFreshnessAgeMs
        )
    }

    @Test
    fun invoke_updatesContentAndFrameAgesWhenMetadataPayloadIsUnchanged() = runTest {
        val localClock = FakeClock(wallMs = 3_000_000L)
        val metadataState = metadataState(
            lastSuccessfulFetchWallMs = localClock.nowWallMs() - 30_000L
        ).copy(
            lastContentChangeWallMs = localClock.nowWallMs() - 60_000L
        )
        val preferences = basePreferences.copy(
            enabled = true,
            animatePastWindow = false
        )
        whenever(preferencesRepository.preferencesFlow).thenReturn(flowOf(preferences))
        whenever(preferencesRepository.enabledFlow).thenReturn(flowOf(true))
        whenever(metadataRepository.currentState()).thenReturn(metadataState)
        whenever(metadataRepository.refreshMetadata()).thenReturn(metadataState)

        val useCase = ObserveWeatherOverlayStateUseCase(
            preferencesRepository = preferencesRepository,
            metadataRepository = metadataRepository,
            clock = localClock
        )
        val states = mutableListOf<WeatherOverlayRuntimeState>()
        val collectJob = backgroundScope.launch {
            useCase.invoke().take(2).toList(states)
        }

        runCurrent()
        assertEquals(1, states.size)
        assertEquals(30_000L, states[0].metadataFreshnessAgeMs)
        assertEquals(60_000L, states[0].metadataContentAgeMs)
        assertEquals(1_200_000L, states[0].selectedFrameAgeMs)

        localClock.advanceWallMs(WEATHER_RAIN_METADATA_REFRESH_INTERVAL_OK_MS)
        advanceTimeBy(WEATHER_RAIN_METADATA_REFRESH_INTERVAL_OK_MS)
        runCurrent()

        collectJob.join()
        assertEquals(2, states.size)
        assertEquals(90_000L, states[1].metadataFreshnessAgeMs)
        assertEquals(120_000L, states[1].metadataContentAgeMs)
        assertEquals(1_260_000L, states[1].selectedFrameAgeMs)
    }

    @Test
    fun invoke_keepsMetadataLiveForRecentTransientStatus() = runTest {
        val metadataState = metadataState(
            status = WeatherRadarStatusCode.NETWORK_ERROR,
            lastSuccessfulFetchWallMs = clock.nowWallMs() - 10_000L
        )
        whenever(preferencesRepository.preferencesFlow).thenReturn(flowOf(basePreferences))
        whenever(preferencesRepository.enabledFlow).thenReturn(flowOf(false))
        whenever(metadataRepository.currentState()).thenReturn(metadataState)

        val state = createUseCase().invoke().first()

        assertFalse(state.metadataStale)
    }

    @Test
    fun invoke_keepsMetadataLiveForRecentRateLimitStatus() = runTest {
        val metadataState = metadataState(
            status = WeatherRadarStatusCode.RATE_LIMIT,
            lastSuccessfulFetchWallMs = clock.nowWallMs() - 10_000L
        )
        whenever(preferencesRepository.preferencesFlow).thenReturn(flowOf(basePreferences))
        whenever(preferencesRepository.enabledFlow).thenReturn(flowOf(false))
        whenever(metadataRepository.currentState()).thenReturn(metadataState)

        val state = createUseCase().invoke().first()

        assertFalse(state.metadataStale)
    }

    @Test
    fun invoke_marksMetadataAsStaleForParseError() = runTest {
        val metadataState = metadataState(
            status = WeatherRadarStatusCode.PARSE_ERROR,
            lastSuccessfulFetchWallMs = clock.nowWallMs() - 10_000L
        )
        whenever(preferencesRepository.preferencesFlow).thenReturn(flowOf(basePreferences))
        whenever(preferencesRepository.enabledFlow).thenReturn(flowOf(false))
        whenever(metadataRepository.currentState()).thenReturn(metadataState)

        val state = createUseCase().invoke().first()

        assertTrue(state.metadataStale)
    }

    @Test
    fun invoke_usesTransientErrorBackoffForNextMetadataRefresh() = runTest {
        val callCount = AtomicInteger(0)
        val preferences = basePreferences.copy(
            enabled = true,
            animatePastWindow = false
        )
        whenever(preferencesRepository.preferencesFlow).thenReturn(flowOf(preferences))
        whenever(preferencesRepository.enabledFlow).thenReturn(flowOf(true))
        whenever(metadataRepository.currentState()).thenReturn(metadataState())
        whenever(metadataRepository.refreshMetadata()).thenAnswer {
            if (callCount.getAndIncrement() == 0) {
                metadataState(status = WeatherRadarStatusCode.NETWORK_ERROR)
            } else {
                metadataState(status = WeatherRadarStatusCode.OK)
            }
        }

        val states = mutableListOf<WeatherOverlayRuntimeState>()
        val collectJob = backgroundScope.launch {
            createUseCase().invoke().take(2).toList(states)
        }

        runCurrent()
        assertEquals(1, callCount.get())
        advanceTimeBy(WEATHER_RAIN_METADATA_REFRESH_INTERVAL_TRANSIENT_ERROR_MS - 1L)
        runCurrent()
        assertEquals(1, callCount.get())
        advanceTimeBy(1L)
        runCurrent()
        assertEquals(2, callCount.get())
        collectJob.join()
        assertEquals(2, states.size)
    }

    @Test
    fun invoke_usesRateLimitBackoffForNextMetadataRefresh() = runTest {
        val callCount = AtomicInteger(0)
        val preferences = basePreferences.copy(
            enabled = true,
            animatePastWindow = false
        )
        whenever(preferencesRepository.preferencesFlow).thenReturn(flowOf(preferences))
        whenever(preferencesRepository.enabledFlow).thenReturn(flowOf(true))
        whenever(metadataRepository.currentState()).thenReturn(metadataState())
        whenever(metadataRepository.refreshMetadata()).thenAnswer {
            if (callCount.getAndIncrement() == 0) {
                metadataState(status = WeatherRadarStatusCode.RATE_LIMIT)
            } else {
                metadataState(status = WeatherRadarStatusCode.OK)
            }
        }

        val states = mutableListOf<WeatherOverlayRuntimeState>()
        val collectJob = backgroundScope.launch {
            createUseCase().invoke().take(2).toList(states)
        }

        runCurrent()
        assertEquals(1, callCount.get())
        advanceTimeBy(WEATHER_RAIN_METADATA_REFRESH_INTERVAL_RATE_LIMIT_MS - 1L)
        runCurrent()
        assertEquals(1, callCount.get())
        advanceTimeBy(1L)
        runCurrent()
        assertEquals(2, callCount.get())
        collectJob.join()
        assertEquals(2, states.size)
    }

    @Test
    fun invoke_appliesTransitionTuningByAnimationWindow() = runTest {
        val metadataState = metadataState(lastSuccessfulFetchWallMs = 999_000L)
        whenever(preferencesRepository.enabledFlow).thenReturn(flowOf(false))
        whenever(metadataRepository.currentState()).thenReturn(metadataState)

        whenever(preferencesRepository.preferencesFlow).thenReturn(
            flowOf(basePreferences.copy(animationWindow = WeatherRainAnimationWindow.TEN_MINUTES))
        )
        val tenMinuteState = createUseCase().invoke().first()

        whenever(preferencesRepository.preferencesFlow).thenReturn(
            flowOf(basePreferences.copy(animationWindow = WeatherRainAnimationWindow.TWENTY_MINUTES))
        )
        val twentyMinuteState = createUseCase().invoke().first()

        whenever(preferencesRepository.preferencesFlow).thenReturn(
            flowOf(basePreferences.copy(animationWindow = WeatherRainAnimationWindow.THIRTY_MINUTES))
        )
        val thirtyMinuteState = createUseCase().invoke().first()

        whenever(preferencesRepository.preferencesFlow).thenReturn(
            flowOf(
                basePreferences.copy(
                    animationWindow = WeatherRainAnimationWindow.ONE_HUNDRED_TWENTY_MINUTES
                )
            )
        )
        val oneHundredTwentyMinuteState = createUseCase().invoke().first()

        assertEquals(280L, tenMinuteState.transitionDurationMs)
        assertEquals(238L, twentyMinuteState.transitionDurationMs)
        assertEquals(196L, thirtyMinuteState.transitionDurationMs)
        assertEquals(196L, oneHundredTwentyMinuteState.transitionDurationMs)
    }

    @Test
    fun invoke_reportsSelectedFrameAgeForManualOldFrame() = runTest {
        val preferences = basePreferences.copy(
            animatePastWindow = false,
            frameMode = WeatherRadarFrameMode.MANUAL,
            manualFrameIndex = 0
        )
        val metadataState = metadataState(lastSuccessfulFetchWallMs = clock.nowWallMs() - 10_000L)
        whenever(preferencesRepository.preferencesFlow).thenReturn(flowOf(preferences))
        whenever(preferencesRepository.enabledFlow).thenReturn(flowOf(false))
        whenever(metadataRepository.currentState()).thenReturn(metadataState)

        val state = createUseCase().invoke().first()

        assertEquals(0L, state.selectedFrame?.frameTimeEpochSec)
        assertEquals(1_000_000L, state.selectedFrameAgeMs)
    }

    private fun createUseCase(): ObserveWeatherOverlayStateUseCase =
        ObserveWeatherOverlayStateUseCase(
            preferencesRepository = preferencesRepository,
            metadataRepository = metadataRepository,
            clock = clock
        )

    private fun metadataState(
        status: WeatherRadarStatusCode = WeatherRadarStatusCode.OK,
        lastSuccessfulFetchWallMs: Long? = null
    ): WeatherRadarMetadataState =
        WeatherRadarMetadataState(
            status = status,
            metadata = WeatherRadarMetadata(
                hostUrl = "https://tilecache.rainviewer.com",
                generatedEpochSec = 1_800L,
                pastFrames = listOf(
                    WeatherRadarFrame(timeEpochSec = 0L, path = "/v2/radar/0"),
                    WeatherRadarFrame(timeEpochSec = 600L, path = "/v2/radar/600"),
                    WeatherRadarFrame(timeEpochSec = 1_200L, path = "/v2/radar/1200"),
                    WeatherRadarFrame(timeEpochSec = 1_800L, path = "/v2/radar/1800")
                )
            ),
            lastSuccessfulFetchWallMs = lastSuccessfulFetchWallMs
        )

    private val basePreferences = WeatherOverlayPreferences(
        enabled = false,
        animatePastWindow = true,
        animationWindow = WeatherRainAnimationWindow.TEN_MINUTES
    )
}
