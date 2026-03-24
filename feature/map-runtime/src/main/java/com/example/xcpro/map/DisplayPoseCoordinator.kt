package com.example.xcpro.map

class DisplayPoseCoordinator(
    private val clock: DisplayTimeSource,
    private val pipeline: PosePipeline
) {

    data class UpdateResult(
        val timeBaseChanged: Boolean,
        val timeBase: DisplayClock.TimeBase
    )

    var timeBase: DisplayClock.TimeBase? = null
        private set

    var replaySpeedMultiplier: Double
        get() = clock.replaySpeedMultiplier
        set(value) {
            clock.replaySpeedMultiplier = value
        }

    fun updateFromFix(envelope: LocationFeedAdapter.RawFixEnvelope): UpdateResult {
        val changed = timeBase != envelope.timeBase
        if (changed) {
            pipeline.resetSmoother()
            timeBase = envelope.timeBase
        }
        clock.updateFromFix(envelope.fix.timestampMs, envelope.timeBase)
        pipeline.pushRawFix(envelope.fix)
        return UpdateResult(timeBaseChanged = changed, timeBase = envelope.timeBase)
    }

    fun nowMs(): Long = clock.nowMs()

    fun clear() {
        timeBase = null
        clock.clear()
        pipeline.clear()
    }

    fun selectPose(
        nowMs: Long,
        mode: DisplayPoseMode,
        smoothingProfile: DisplaySmoothingProfile
    ): DisplayPoseSmoother.DisplayPose? {
        return pipeline.selectPose(nowMs, mode, smoothingProfile)
    }
}

interface DisplayTimeSource {
    var replaySpeedMultiplier: Double
    fun updateFromFix(timestampMs: Long, base: DisplayClock.TimeBase)
    fun nowMs(): Long
    fun clear()
}

class DisplayClockSource(
    private val clock: DisplayClock = DisplayClock()
) : DisplayTimeSource {
    override var replaySpeedMultiplier: Double
        get() = clock.replaySpeedMultiplier
        set(value) {
            clock.replaySpeedMultiplier = value
        }

    override fun updateFromFix(timestampMs: Long, base: DisplayClock.TimeBase) {
        clock.updateFromFix(timestampMs, base)
    }

    override fun nowMs(): Long = clock.nowMs()

    override fun clear() {
        clock.clear()
    }
}

interface PosePipeline {
    fun pushRawFix(fix: DisplayPoseSmoother.RawFix)
    fun resetSmoother()
    fun clear()
    fun selectPose(
        nowMs: Long,
        mode: DisplayPoseMode,
        smoothingProfile: DisplaySmoothingProfile
    ): DisplayPoseSmoother.DisplayPose?
}

class DisplayPosePipelineAdapter(
    private val pipeline: DisplayPosePipeline
) : PosePipeline {
    override fun pushRawFix(fix: DisplayPoseSmoother.RawFix) {
        pipeline.pushRawFix(fix)
    }

    override fun resetSmoother() {
        pipeline.resetSmoother()
    }

    override fun clear() {
        pipeline.clear()
    }

    override fun selectPose(
        nowMs: Long,
        mode: DisplayPoseMode,
        smoothingProfile: DisplaySmoothingProfile
    ): DisplayPoseSmoother.DisplayPose? {
        return pipeline.selectPose(nowMs, mode, smoothingProfile)
    }
}
