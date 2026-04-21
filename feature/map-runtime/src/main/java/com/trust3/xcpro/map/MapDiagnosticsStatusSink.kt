package com.trust3.xcpro.map

/**
 * Receives aggregate diagnostics status lines emitted by map-runtime owners.
 *
 * Implementations must keep diagnostics non-authoritative and must not record
 * coordinates, tracks, or raw location history.
 */
fun interface MapDiagnosticsStatusSink {
    fun emit(statusLine: String)
}
