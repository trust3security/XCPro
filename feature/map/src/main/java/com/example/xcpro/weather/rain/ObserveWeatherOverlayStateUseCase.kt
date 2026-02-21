package com.example.xcpro.weather.rain

import com.example.xcpro.core.time.Clock
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

@OptIn(ExperimentalCoroutinesApi::class)
class ObserveWeatherOverlayStateUseCase @Inject constructor(
    private val preferencesRepository: WeatherOverlayPreferencesRepository,
    private val metadataRepository: WeatherRadarMetadataRepository,
    private val clock: Clock
) {
    operator fun invoke(): Flow<WeatherOverlayRuntimeState> =
        combine(
            preferencesRepository.preferencesFlow,
            metadataTickerFlow(preferencesRepository.enabledFlow),
            animationTickerFlow(preferencesRepository.preferencesFlow)
        ) { preferences, metadataState, animationTick ->
            toRuntimeState(
                preferences = preferences,
                metadataState = metadataState,
                animationTick = animationTick
            )
        }
            .distinctUntilChanged()

    private fun metadataTickerFlow(
        enabledFlow: Flow<Boolean>
    ): Flow<WeatherRadarMetadataState> =
        enabledFlow.flatMapLatest { enabled ->
            if (!enabled) {
                flow { emit(metadataRepository.currentState()) }
            } else {
                flow {
                    while (true) {
                        val refreshedState = metadataRepository.refreshMetadata()
                        emit(refreshedState)
                        delay(resolveWeatherRainMetadataRefreshIntervalMs(refreshedState.status))
                    }
                }
            }
        }

    private fun animationTickerFlow(
        preferencesFlow: Flow<WeatherOverlayPreferences>
    ): Flow<Long> =
        preferencesFlow
            .map { preferences ->
                AnimationTickerConfig(
                    enabled = preferences.enabled,
                    animatePastWindow = preferences.animatePastWindow,
                    frameIntervalMs = preferences.animationSpeed.frameIntervalMs,
                    animationWindow = preferences.animationWindow
                )
            }
            .distinctUntilChanged()
            .flatMapLatest { config ->
                if (!config.enabled || !config.animatePastWindow) {
                    flow { emit(0L) }
                } else {
                    flow {
                        var tick = 0L
                        while (true) {
                            emit(tick)
                            tick++
                            delay(config.frameIntervalMs)
                        }
                    }
                }
            }

    private fun toRuntimeState(
        preferences: WeatherOverlayPreferences,
        metadataState: WeatherRadarMetadataState,
        animationTick: Long
    ): WeatherOverlayRuntimeState {
        val metadata = metadataState.metadata
        val frameSelection = selectFrameSelection(
            metadata = metadata,
            animatePastWindow = preferences.animatePastWindow,
            animationWindowSeconds = preferences.animationWindow.windowSeconds,
            frameMode = preferences.frameMode,
            manualFrameIndex = preferences.manualFrameIndex,
            renderOptions = preferences.renderOptions,
            animationTick = animationTick
        )
        val transitionDurationMs = resolveWeatherRainEffectiveTransitionDurationMs(
            preferredDurationMs = preferences.transitionQuality.preferredDurationMs,
            frameIntervalMs = preferences.animationSpeed.frameIntervalMs,
            animatePastWindow = preferences.animatePastWindow,
            animationWindow = preferences.animationWindow
        )
        val nowWallMs = clock.nowWallMs()
        val metadataFreshnessAgeMs = metadataState.lastSuccessfulFetchWallMs?.let { lastSuccessWallMs ->
            (nowWallMs - lastSuccessWallMs).coerceAtLeast(0L)
        }
        val metadataContentAgeMs = metadataState.lastContentChangeWallMs?.let { lastContentChangeWallMs ->
            (nowWallMs - lastContentChangeWallMs).coerceAtLeast(0L)
        }
        val selectedFrameAgeMs = frameSelection?.let { selected ->
            ((nowWallMs / 1_000L) - selected.frameTimeEpochSec).coerceAtLeast(0L) * 1_000L
        }
        val metadataStale = isWeatherRainMetadataStale(
            status = metadataState.status,
            freshnessAgeMs = metadataFreshnessAgeMs
        )
        val frameCount = metadata?.pastFrames?.size ?: 0
        return WeatherOverlayRuntimeState(
            enabled = preferences.enabled,
            opacity = preferences.opacity,
            animatePastWindow = preferences.animatePastWindow,
            animationWindow = preferences.animationWindow,
            animationSpeed = preferences.animationSpeed,
            transitionQuality = preferences.transitionQuality,
            transitionDurationMs = transitionDurationMs,
            frameMode = preferences.frameMode,
            manualFrameIndex = preferences.manualFrameIndex,
            selectedFrame = frameSelection,
            availableFrameCount = frameCount,
            metadataGeneratedEpochSec = metadata?.generatedEpochSec,
            metadataStatus = metadataState.status,
            metadataDetail = metadataState.detail,
            lastSuccessfulMetadataFetchWallMs = metadataState.lastSuccessfulFetchWallMs,
            lastMetadataContentChangeWallMs = metadataState.lastContentChangeWallMs,
            metadataFreshnessAgeMs = metadataFreshnessAgeMs,
            metadataContentAgeMs = metadataContentAgeMs,
            selectedFrameAgeMs = selectedFrameAgeMs,
            metadataStale = metadataStale
        )
    }

    private fun selectFrameSelection(
        metadata: WeatherRadarMetadata?,
        animatePastWindow: Boolean,
        animationWindowSeconds: Long,
        frameMode: WeatherRadarFrameMode,
        manualFrameIndex: Int,
        renderOptions: WeatherRadarRenderOptions,
        animationTick: Long
    ): WeatherRainFrameSelection? {
        val frames = metadata?.pastFrames ?: return null
        if (frames.isEmpty()) return null

        val selectedFrame = if (animatePastWindow) {
            selectAnimatedFrame(
                frames = frames,
                animationWindowSeconds = animationWindowSeconds,
                animationTick = animationTick
            )
        } else {
            val selectedIndex = when (frameMode) {
                WeatherRadarFrameMode.LATEST -> frames.lastIndex
                WeatherRadarFrameMode.MANUAL -> manualFrameIndex.coerceIn(0, frames.lastIndex)
            }
            frames[selectedIndex]
        }
        return WeatherRainFrameSelection(
            hostUrl = metadata.hostUrl,
            framePath = selectedFrame.path,
            frameTimeEpochSec = selectedFrame.timeEpochSec,
            renderOptions = renderOptions
        )
    }

    private fun selectAnimatedFrame(
        frames: List<WeatherRadarFrame>,
        animationWindowSeconds: Long,
        animationTick: Long
    ): WeatherRadarFrame {
        val latestTimeEpochSec = frames.last().timeEpochSec
        val animationWindowStartSec = latestTimeEpochSec - animationWindowSeconds
        val eligibleFrames = frames.filter { frame ->
            frame.timeEpochSec >= animationWindowStartSec
        }
        val candidateFrames = if (eligibleFrames.isNotEmpty()) eligibleFrames else frames
        val animationFrames = applyAnimationFrameQualityPolicy(
            frames = candidateFrames,
            animationWindowSeconds = animationWindowSeconds
        )
        val boundedIndex = resolveWeatherRainPlaybackFrameIndex(
            animationTick = animationTick,
            frameCount = animationFrames.size
        )
        return animationFrames[boundedIndex]
    }

    private fun applyAnimationFrameQualityPolicy(
        frames: List<WeatherRadarFrame>,
        animationWindowSeconds: Long
    ): List<WeatherRadarFrame> {
        if (animationWindowSeconds < WEATHER_RAIN_ANIMATION_WINDOW_30_MIN_SECONDS) return frames
        if (frames.size < MIN_FRAMES_FOR_QUALITY_FILTER) return frames
        val windowSpanSeconds = frames.last().timeEpochSec - frames.first().timeEpochSec
        if (windowSpanSeconds < animationWindowSeconds) return frames
        return frames.drop(1)
    }

    private data class AnimationTickerConfig(
        val enabled: Boolean,
        val animatePastWindow: Boolean,
        val frameIntervalMs: Long,
        val animationWindow: WeatherRainAnimationWindow
    )

    private companion object {
        private const val MIN_FRAMES_FOR_QUALITY_FILTER = 4
    }
}
