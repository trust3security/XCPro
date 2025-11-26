package com.example.xcpro.sensors

/**
 * Shared helper for the "one sample per elapsed second" behaviour used by our averaging windows.
 *
 * Seeds the window when we have no previous timestamp or the clock moved backwards.
 * Otherwise, writes the current sample once for every whole second that elapsed.
 *
 * @return the latest timestamp that was written (or currentTime when seeded)
 */
internal fun addSamplesForElapsedSeconds(
    window: FixedSampleAverageWindow,
    lastTimestamp: Long,
    currentTime: Long,
    sampleValue: Double
): Long {
    if (lastTimestamp == 0L || currentTime < lastTimestamp) {
        val seed = if (sampleValue.isFinite()) sampleValue else window.average()
        if (seed.isFinite()) window.seed(seed)
        return currentTime
    }

    if (currentTime == lastTimestamp) {
        return lastTimestamp
    }

    var nextTimestamp = lastTimestamp + 1000L
    var latestTimestamp = lastTimestamp
    while (nextTimestamp <= currentTime) {
        window.addSample(sampleValue)
        latestTimestamp = nextTimestamp
        nextTimestamp += 1000L
    }
    return latestTimestamp
}
