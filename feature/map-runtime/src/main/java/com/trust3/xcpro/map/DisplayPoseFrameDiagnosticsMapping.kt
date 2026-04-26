package com.trust3.xcpro.map

internal fun MapRenderSurfaceDiagnostics.recordDisplayFrameDispatchDecision(
    decision: DisplayPoseFrameActivityGate.DispatchDecision
) {
    if (decision.shouldDispatch) {
        recordDisplayFrameDispatchAllowed(decision.reason.toDiagnosticsDispatchReason())
    } else {
        recordDisplayFramePreDispatchSuppressed(decision.reason.toDiagnosticsSuppressionReason())
    }
}

internal fun MapRenderSurfaceDiagnostics.recordLocalOwnshipDispatchSuppressed() {
    recordDisplayFramePreDispatchSuppressed(
        MapRenderSurfaceDiagnostics.DisplayFramePreDispatchSuppressionReason.LOCAL_OWNSHIP_DISABLED
    )
}

internal fun MapRenderSurfaceDiagnostics.recordLocalOwnshipRenderSkipped() {
    recordDisplayFrameRenderSkipped(
        MapRenderSurfaceDiagnostics.DisplayFrameRenderSkipReason.LOCAL_OWNSHIP_DISABLED
    )
}

internal fun DisplayPoseFrameActivityGate.DecisionReason.toDiagnosticsDispatchReason():
    MapRenderSurfaceDiagnostics.DisplayFrameDispatchReason =
    when (this) {
        DisplayPoseFrameActivityGate.DecisionReason.REPLAY_TIME_BASE ->
            MapRenderSurfaceDiagnostics.DisplayFrameDispatchReason.REPLAY_TIME_BASE
        DisplayPoseFrameActivityGate.DecisionReason.CONFIG_CHANGED ->
            MapRenderSurfaceDiagnostics.DisplayFrameDispatchReason.CONFIG_CHANGED
        DisplayPoseFrameActivityGate.DecisionReason.ACTIVE_WINDOW ->
            MapRenderSurfaceDiagnostics.DisplayFrameDispatchReason.ACTIVE_WINDOW
        DisplayPoseFrameActivityGate.DecisionReason.NO_RENDERABLE_INPUT,
        DisplayPoseFrameActivityGate.DecisionReason.ACTIVITY_EXPIRED ->
            error("Suppressed display-frame decision cannot be recorded as dispatch allowed")
    }

internal fun DisplayPoseFrameActivityGate.DecisionReason.toDiagnosticsSuppressionReason():
    MapRenderSurfaceDiagnostics.DisplayFramePreDispatchSuppressionReason =
    when (this) {
        DisplayPoseFrameActivityGate.DecisionReason.NO_RENDERABLE_INPUT ->
            MapRenderSurfaceDiagnostics.DisplayFramePreDispatchSuppressionReason.NO_RENDERABLE_INPUT
        DisplayPoseFrameActivityGate.DecisionReason.ACTIVITY_EXPIRED ->
            MapRenderSurfaceDiagnostics.DisplayFramePreDispatchSuppressionReason.ACTIVITY_EXPIRED
        DisplayPoseFrameActivityGate.DecisionReason.REPLAY_TIME_BASE,
        DisplayPoseFrameActivityGate.DecisionReason.CONFIG_CHANGED,
        DisplayPoseFrameActivityGate.DecisionReason.ACTIVE_WINDOW ->
            error("Allowed display-frame decision cannot be recorded as suppression")
    }
