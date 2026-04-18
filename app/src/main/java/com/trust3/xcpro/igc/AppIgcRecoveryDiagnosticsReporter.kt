package com.trust3.xcpro.igc

import com.trust3.xcpro.core.time.Clock
import com.trust3.xcpro.igc.domain.IgcRecoveryDiagnosticsReporter
import com.trust3.xcpro.igc.domain.LogcatIgcRecoveryDiagnosticsReporter
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class IgcRecoveryDiagnosticEvent(
    val wallTimeMs: Long,
    val event: String,
    val attributes: Map<String, String>
)

@Singleton
class AppIgcRecoveryDiagnosticsReporter @Inject constructor(
    private val clock: Clock
) : IgcRecoveryDiagnosticsReporter {

    private val delegate = LogcatIgcRecoveryDiagnosticsReporter()
    private val _events = MutableStateFlow<List<IgcRecoveryDiagnosticEvent>>(emptyList())
    val events: StateFlow<List<IgcRecoveryDiagnosticEvent>> = _events.asStateFlow()

    override fun report(event: String, attributes: Map<String, String>) {
        delegate.report(event = event, attributes = attributes)
        val captured = IgcRecoveryDiagnosticEvent(
            wallTimeMs = clock.nowWallMs(),
            event = event,
            attributes = attributes.toMap()
        )
        _events.update { current ->
            val updated = current + captured
            if (updated.size <= MAX_EVENTS) {
                updated
            } else {
                updated.takeLast(MAX_EVENTS)
            }
        }
    }

    companion object {
        const val MAX_EVENTS: Int = 200
    }
}
