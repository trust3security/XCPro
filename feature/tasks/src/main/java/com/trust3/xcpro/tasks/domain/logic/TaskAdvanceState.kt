package com.trust3.xcpro.tasks.domain.logic

/**
 * Simplified advance state machine mirroring XC behaviour.
 * AI-NOTE: Kept lean; UI/VM drive SetArmed/Toggle; domain checks readiness.
 */
class TaskAdvanceState {

    enum class Mode {
        MANUAL,
        AUTO
    }

    enum class ArmState {
        START_ARMED,
        START_DISARMED,
        TURN_ARMED,
        TURN_DISARMED
    }

    data class Snapshot(
        val mode: Mode,
        val armState: ArmState,
        val isArmed: Boolean
    )

    private var mode: Mode = Mode.AUTO
    private var armState: ArmState = ArmState.START_DISARMED
    private var armed: Boolean = false

    fun setMode(newMode: Mode) {
        mode = newMode
    }

    fun setArmed(doArm: Boolean) {
        armed = doArm
        updateArmState()
    }

    fun toggleArmed(): Boolean {
        armed = !armed
        updateArmState()
        return armed
    }

    /**
     * Compute whether we should advance when inside/near OZ.
     * @param hasEntered true if OZ boundary crossed
     * @param closeToTarget true if AAT target proximity satisfied
     */
    fun shouldAdvance(hasEntered: Boolean, closeToTarget: Boolean): Boolean {
        return when (mode) {
            Mode.MANUAL -> false
            Mode.AUTO -> when (armState) {
                ArmState.START_ARMED, ArmState.TURN_ARMED -> hasEntered || closeToTarget
                ArmState.START_DISARMED, ArmState.TURN_DISARMED -> false
            }
        }
    }

    fun snapshot(): Snapshot = Snapshot(mode, armState, armed)

    private fun updateArmState() {
        armState = if (armed) {
            when (armState) {
                ArmState.START_DISARMED, ArmState.START_ARMED -> ArmState.START_ARMED
                ArmState.TURN_DISARMED, ArmState.TURN_ARMED -> ArmState.TURN_ARMED
            }
        } else {
            when (armState) {
                ArmState.START_DISARMED, ArmState.START_ARMED -> ArmState.START_DISARMED
                ArmState.TURN_DISARMED, ArmState.TURN_ARMED -> ArmState.TURN_DISARMED
            }
        }
    }
}
