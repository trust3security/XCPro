package com.trust3.xcpro.profiles

import com.trust3.xcpro.core.time.Clock
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class ProfileDiagnosticEvent(
    val wallTimeMs: Long,
    val event: String,
    val attributes: Map<String, String>
)

@Singleton
class AppProfileDiagnosticsReporter @Inject constructor(
    private val clock: Clock
) : ProfileDiagnosticsReporter {

    private val delegate = LogcatProfileDiagnosticsReporter()
    private val _events = MutableStateFlow<List<ProfileDiagnosticEvent>>(emptyList())
    val events: StateFlow<List<ProfileDiagnosticEvent>> = _events.asStateFlow()

    override fun report(event: String, attributes: Map<String, String>) {
        delegate.report(event = event, attributes = attributes)
        val captured = ProfileDiagnosticEvent(
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
