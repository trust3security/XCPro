package com.example.xcpro.tasks

import com.example.xcpro.tasks.domain.logic.TaskAdvanceState
import com.example.xcpro.tasks.racing.navigation.RacingAdvanceState

data class TaskAdvanceUiSnapshot(
    val mode: Mode = Mode.AUTO,
    val armState: ArmState = ArmState.START_DISARMED,
    val isArmed: Boolean = false
) {
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
}

internal fun TaskAdvanceState.Snapshot.toUiSnapshot(): TaskAdvanceUiSnapshot =
    TaskAdvanceUiSnapshot(
        mode = when (mode) {
            TaskAdvanceState.Mode.MANUAL -> TaskAdvanceUiSnapshot.Mode.MANUAL
            TaskAdvanceState.Mode.AUTO -> TaskAdvanceUiSnapshot.Mode.AUTO
        },
        armState = when (armState) {
            TaskAdvanceState.ArmState.START_ARMED -> TaskAdvanceUiSnapshot.ArmState.START_ARMED
            TaskAdvanceState.ArmState.START_DISARMED -> TaskAdvanceUiSnapshot.ArmState.START_DISARMED
            TaskAdvanceState.ArmState.TURN_ARMED -> TaskAdvanceUiSnapshot.ArmState.TURN_ARMED
            TaskAdvanceState.ArmState.TURN_DISARMED -> TaskAdvanceUiSnapshot.ArmState.TURN_DISARMED
        },
        isArmed = isArmed
    )

internal fun RacingAdvanceState.Snapshot.toUiSnapshot(): TaskAdvanceUiSnapshot =
    TaskAdvanceUiSnapshot(
        mode = when (mode) {
            RacingAdvanceState.Mode.MANUAL -> TaskAdvanceUiSnapshot.Mode.MANUAL
            RacingAdvanceState.Mode.AUTO -> TaskAdvanceUiSnapshot.Mode.AUTO
        },
        armState = when (armState) {
            RacingAdvanceState.ArmState.START_ARMED -> TaskAdvanceUiSnapshot.ArmState.START_ARMED
            RacingAdvanceState.ArmState.START_DISARMED -> TaskAdvanceUiSnapshot.ArmState.START_DISARMED
            RacingAdvanceState.ArmState.TURN_ARMED -> TaskAdvanceUiSnapshot.ArmState.TURN_ARMED
            RacingAdvanceState.ArmState.TURN_DISARMED -> TaskAdvanceUiSnapshot.ArmState.TURN_DISARMED
        },
        isArmed = isArmed
    )

internal fun TaskAdvanceUiSnapshot.Mode.toTaskAdvanceMode(): TaskAdvanceState.Mode =
    when (this) {
        TaskAdvanceUiSnapshot.Mode.MANUAL -> TaskAdvanceState.Mode.MANUAL
        TaskAdvanceUiSnapshot.Mode.AUTO -> TaskAdvanceState.Mode.AUTO
    }

internal fun TaskAdvanceUiSnapshot.Mode.toRacingAdvanceMode(): RacingAdvanceState.Mode =
    when (this) {
        TaskAdvanceUiSnapshot.Mode.MANUAL -> RacingAdvanceState.Mode.MANUAL
        TaskAdvanceUiSnapshot.Mode.AUTO -> RacingAdvanceState.Mode.AUTO
    }
