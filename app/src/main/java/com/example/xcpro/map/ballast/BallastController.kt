package com.example.xcpro.map.ballast

import androidx.compose.animation.core.CubicBezierEasing
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
import kotlin.math.roundToLong

private const val FRAME_DELAY_MS = 200L
private const val FILL_DURATION_MS = 20_000L
private const val DRAIN_DURATION_MS = 180_000L
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

    init {
        snapshotJob = scope.launch(dispatcher) {
            adapter.snapshots.collect { snapshot ->
                var cancelAnimation = false
                _state.update { current ->
                    val next = current.withSnapshot(snapshot)
                    if (current.isAnimating && animationJob?.isActive == true) {
                        val target = current.targetKg
                        if (target != null &&
                            !snapshot.currentKg.isCloseTo(target) &&
                            !snapshot.currentKg.isCloseTo(current.snapshot.currentKg)
                        ) {
                            cancelAnimation = true
                            next.resetAnimation()
                        } else {
                            next
                        }
                    } else if (current.isAnimating && animationJob?.isActive != true) {
                        next.resetAnimation()
                    } else {
                        next
                    }
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
                BallastCommand.StartFill -> handleStartFill()
                BallastCommand.StartDrain -> handleStartDrain()
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
        startAnimation(
            mode = BallastMode.Filling,
            targetKg = snapshot.maxKg,
            durationMillis = FILL_DURATION_MS,
            easing = FILL_EASING
        )
    }

    private suspend fun handleStartDrain() {
        val snapshot = state.value.snapshot
        if (!snapshot.canDrain) return
        startAnimation(
            mode = BallastMode.Draining,
            targetKg = 0.0,
            durationMillis = DRAIN_DURATION_MS,
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
                adapter.updateWaterBallast(currentKg)

                val remaining = (durationMillis - elapsed).coerceAtLeast(0L)
                _state.update { it.withRemaining(remaining) }

                if (fraction >= 1f || currentKg.isCloseTo(targetKg)) break
                delay(FRAME_DELAY_MS)
            }

            adapter.updateWaterBallast(targetKg)
            _state.update { it.resetAnimation() }
        }
    }

    private suspend fun stopAnimation() {
        animationJob?.cancel()
        animationJob?.join()
        animationJob = null
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
