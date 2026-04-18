package com.trust3.xcpro.tasks.racing.navigation

class RacingAdvanceState {

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

    fun shouldAdvance(eventType: RacingNavigationEventType): Boolean {
        if (mode == Mode.MANUAL) return false
        return when (eventType) {
            RacingNavigationEventType.START -> armState == ArmState.START_ARMED
            RacingNavigationEventType.START_REJECTED -> false
            RacingNavigationEventType.TURNPOINT_NEAR_MISS -> false
            RacingNavigationEventType.TURNPOINT,
            RacingNavigationEventType.FINISH -> armState == ArmState.TURN_ARMED
        }
    }

    fun onStartAdvanced() {
        armState = if (armed) ArmState.TURN_ARMED else ArmState.TURN_DISARMED
    }

    fun resetToStartPhase() {
        armState = if (armed) ArmState.START_ARMED else ArmState.START_DISARMED
    }

    fun snapshot(): Snapshot = Snapshot(mode, armState, armed)

    fun restore(snapshot: Snapshot) {
        mode = snapshot.mode
        armState = snapshot.armState
        armed = snapshot.isArmed
    }

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
