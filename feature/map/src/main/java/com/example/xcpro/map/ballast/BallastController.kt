package com.example.xcpro.map.ballast

import androidx.compose.animation.core.CubicBezierEasing
import com.example.xcpro.common.glider.GliderConfig
import com.example.xcpro.glider.GliderRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.roundToLong

private const val FRAME_DELAY_MS = 200L
private const val FILL_FULL_DURATION_MS = 20_000L
private const val DEFAULT_DRAIN_FULL_DURATION_MS = 300_000L
private val FILL_EASING = CubicBezierEasing(0.3f, 0.0f, 0.2f, 1.0f)
private val DRAIN_EASING = CubicBezierEasing(0.4f, 0.0f, 0.2f, 1.0f)

class BallastController(
    private val repository: GliderRepository,
    private val scope: CoroutineScope,
    private val dispatcher: CoroutineDispatcher,
    private val adapter: BallastRepositoryAdapter = BallastRepositoryAdapter(repository),
    private val timeSource: () -> Long = { android.os.SystemClock.uptimeMillis() }
) {
    private val _state = MutableStateFlow(
        BallastUiState(snapshot = initialSnapshot(repository))
    )
    val state: StateFlow<BallastUiState> = _state.asStateFlow()

    private var snapshotJob: Job
    private var animationJob: Job? = null
    private var lastWriteKg: Double? = null

    init {
        snapshotJob = scope.launch(dispatcher) {
            adapter.snapshots.collect { snapshot ->
                var cancelAnimation = false
                _state.update { current ->
                    val next = current.withSnapshot(snapshot)
                    if (current.isAnimating) {
                        if (animationJob?.isActive == true) {
                            val target = current.targetKg
                            val selfUpdate = lastWriteKg?.let { snapshot.currentKg.isCloseTo(it, tolerance = 0.5) } ?: false
                            if (!selfUpdate && target != null) {
                                val prev = current.snapshot.currentKg
                                val towardTargetBefore = (target - prev)
                                val step = (snapshot.currentKg - prev)
                                val movingAway = towardTargetBefore * step < 0 && !snapshot.currentKg.isCloseTo(prev, tolerance = 0.5)
                                val largeJump = kotlin.math.abs(step) > 5.0
                                if (movingAway || largeJump) {
                                    cancelAnimation = true
                                    return@update next.resetAnimation()
                                }
                            }
                        } else {
                            return@update next.resetAnimation()
                        }
                    }
                    next
                }
                if (cancelAnimation) {
                    stopAnimation()
                }
            }
        }
    }

    fun submit(command: BallastCommand) {
        scope.launch(dispatcher) {
            when (command) {
                BallastCommand.StartFill -> {
                    if (state.value.mode == BallastMode.Filling) {
                        stopAnimation()
                    } else {
                        handleStartFill()
                    }
                }
                BallastCommand.StartDrain -> {
                    if (state.value.mode == BallastMode.Draining) {
                        stopAnimation()
                    } else {
                        handleStartDrain()
                    }
                }
                BallastCommand.Cancel -> stopAnimation()
                is BallastCommand.ImmediateSet -> {
                    stopAnimation()
                    adapter.updateWaterBallast(command.kilograms)
                }
            }
        }
    }

    private suspend fun handleStartFill() {
        val snapshot = state.value.snapshot
        if (!snapshot.canFill) return
        val duration = scaledDuration(
            startKg = snapshot.currentKg,
            targetKg = snapshot.maxKg,
            maxKg = snapshot.maxKg,
            fullDuration = FILL_FULL_DURATION_MS
        )
        startAnimation(
            mode = BallastMode.Filling,
            targetKg = snapshot.maxKg,
            durationMillis = duration,
            easing = FILL_EASING
        )
    }

    private suspend fun handleStartDrain() {
        val snapshot = state.value.snapshot
        if (!snapshot.canDrain) return
        val duration = scaledDuration(
            startKg = snapshot.currentKg,
            targetKg = 0.0,
            maxKg = snapshot.maxKg,
            fullDuration = drainFullDurationMs(repository.config.value)
        )
        startAnimation(
            mode = BallastMode.Draining,
            targetKg = 0.0,
            durationMillis = duration,
            easing = DRAIN_EASING
        )
    }

    private suspend fun startAnimation(
        mode: BallastMode,
        targetKg: Double,
        durationMillis: Long,
        easing: CubicBezierEasing
    ) {
        stopAnimation()

        val startSnapshot = state.value.snapshot
        val startKg = startSnapshot.currentKg
        if (startKg.isCloseTo(targetKg) || durationMillis <= 0) {
            adapter.updateWaterBallast(targetKg)
            _state.update { it.resetAnimation() }
            return
        }

        _state.update { it.withMode(mode, durationMillis, targetKg) }
        animationJob = scope.launch(dispatcher) {
            val startTime = timeSource()
            while (isActive) {
                val elapsed = timeSource() - startTime
                val fraction = (elapsed.toFloat() / durationMillis).coerceIn(0f, 1f)
                val eased = easing.transform(fraction)
                val currentKg = lerp(startKg, targetKg, eased)
                lastWriteKg = currentKg
                adapter.updateWaterBallast(currentKg)

                val remaining = (durationMillis - elapsed).coerceAtLeast(0L)
                _state.update { it.withRemaining(remaining) }

                if (fraction >= 1f || currentKg.isCloseTo(targetKg)) break
                delay(FRAME_DELAY_MS)
            }

            lastWriteKg = targetKg
            adapter.updateWaterBallast(targetKg)
            _state.update { it.resetAnimation() }
        }
    }

    private suspend fun stopAnimation() {
        animationJob?.cancel()
        animationJob?.join()
        animationJob = null
        lastWriteKg = null
        _state.update { state ->
            if (state.isAnimating) state.resetAnimation() else state
        }
    }

    fun dispose() {
        snapshotJob.cancel()
    }
}

private fun lerp(start: Double, end: Double, fraction: Float): Double =
    start + (end - start) * fraction

private fun initialSnapshot(repository: GliderRepository): BallastSnapshot {
    val config = repository.config.value
    val model = repository.selectedModel.value
    val maxKg = model?.water?.totalLiters?.toDouble()?.coerceAtLeast(config.waterBallastKg)
        ?: maxOf(200.0, config.waterBallastKg)
    return BallastSnapshot.create(config.waterBallastKg, maxKg)
}

private fun drainFullDurationMs(config: GliderConfig): Long {
    val configMinutes = config.ballastDrainMinutes
    val minutes = if (!configMinutes.isNaN() && !configMinutes.isInfinite() && configMinutes > 0.0) {
        configMinutes
    } else {
        DEFAULT_DRAIN_FULL_DURATION_MS / 60_000.0
    }
    return (minutes * 60_000.0).roundToLong().coerceAtLeast(10_000L)
}

private fun scaledDuration(
    startKg: Double,
    targetKg: Double,
    maxKg: Double,
    fullDuration: Long
): Long {
    if (fullDuration <= 0L) return 0L
    val delta = abs(targetKg - startKg)
    if (delta.isCloseTo(0.0)) return 0L
    val reference = if (maxKg > 0.0) maxKg else delta
    if (reference == 0.0) return 0L
    val fraction = (delta / reference).coerceIn(0.0, 1.0)
    return (fullDuration * fraction).roundToLong()
}
