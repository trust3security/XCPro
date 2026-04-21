package com.trust3.xcpro.map

/**
 * Imperative map operations that should not live in Compose state.
 * These are consumed by a UI-only runtime controller.
 */
sealed interface MapCommand {
    data class SetStyle(val styleName: String) : MapCommand
    data class ExportDiagnostics(val reason: String) : MapCommand
    object FitCurrentTask : MapCommand
}
