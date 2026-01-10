package com.example.xcpro.sensors

/**
 * Shared helper for the "one sample per elapsed second" behaviour used by our averaging windows.
 *
 * Seeds the window when we have no previous timestamp or the clock moved backwards.
 * Otherwise, writes the current sample once for every rounded elapsed second.
 * Treat large forward jumps as time warps and reset like XCSoar.
 *
 * @return the latest timestamp that was written (or currentTime when seeded)
 */
internal fun addSamplesForElapsedSeconds(
    window: FixedSampleAverageWindow,
    lastTimestamp: Long,
    currentTime: Long,
    sampleValue: Double
): Long {
    // Use negative timestamp as "uninitialised" sentinel; 0 is a valid epoch timestamp.
    if (lastTimestamp < 0L || currentTime < lastTimestamp) {
        window.clear()
        window.addSample(sampleValue) // start window with a single sample instead of pre-filling
        return currentTime
    }

    if (currentTime == lastTimestamp) {
        return lastTimestamp
    }

    val deltaMs = currentTime - lastTimestamp
    if (deltaMs < 1000L) {
        return lastTimestamp
    }

    if (deltaMs > TIME_WARP_LONG_MS) {
        window.clear()
        window.addSample(sampleValue)
        return currentTime
    }

    val elapsedSeconds = kotlin.math.round(deltaMs / 1000.0).toLong()
    if (elapsedSeconds <= 0L) {
        return lastTimestamp
    }

    repeat(elapsedSeconds.toInt()) {
        window.addSample(sampleValue)
    }
    return currentTime
}

private const val TIME_WARP_LONG_MS = 4 * 60 * 60 * 1000L
