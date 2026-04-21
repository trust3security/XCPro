package com.trust3.xcpro.variometer.bluetooth.lxnav.runtime

import com.trust3.xcpro.bluetooth.BluetoothConnectionError
import com.trust3.xcpro.variometer.bluetooth.lxnav.LxParseOutcome
import com.trust3.xcpro.variometer.bluetooth.lxnav.LxRejectedReason

internal class LxSentenceDiagnosticsAccumulator(
    private val sentenceRateWindowMs: Long = DEFAULT_SENTENCE_RATE_WINDOW_MS
) {
    private val recentSentenceMonoMs = ArrayDeque<Long>()

    init {
        require(sentenceRateWindowMs > 0L) { "sentenceRateWindowMs must be > 0" }
    }

    fun reset(sessionStartMonoMs: Long): LxBluetoothRuntimeDiagnostics {
        recentSentenceMonoMs.clear()
        return LxBluetoothRuntimeDiagnostics(sessionStartMonoMs = sessionStartMonoMs)
    }

    fun onChunkReceived(
        current: LxBluetoothRuntimeDiagnostics,
        receivedMonoMs: Long
    ): LxBluetoothRuntimeDiagnostics = current.copy(lastReceivedMonoMs = receivedMonoMs)

    fun onOutcomes(
        current: LxBluetoothRuntimeDiagnostics,
        outcomes: List<LxParseOutcome>
    ): LxBluetoothRuntimeDiagnostics {
        if (outcomes.isEmpty()) return current

        var acceptedSentenceCount = current.acceptedSentenceCount
        var rejectedSentenceCount = current.rejectedSentenceCount
        var checksumFailureCount = current.checksumFailureCount
        var parseFailureCount = current.parseFailureCount

        outcomes.forEach { outcome ->
            recentSentenceMonoMs += outcome.receivedMonoMs
            trimWindow(outcome.receivedMonoMs)
            when (outcome) {
                is LxParseOutcome.Accepted -> {
                    acceptedSentenceCount += 1
                }

                is LxParseOutcome.KnownUnsupported,
                is LxParseOutcome.UnknownSentence -> {
                    rejectedSentenceCount += 1
                }

                is LxParseOutcome.Rejected -> {
                    rejectedSentenceCount += 1
                    parseFailureCount += 1
                    if (
                        outcome.reason == LxRejectedReason.INVALID_CHECKSUM ||
                        outcome.reason == LxRejectedReason.MALFORMED_CHECKSUM
                    ) {
                        checksumFailureCount += 1
                    }
                }
            }
        }

        val lastOutcomeMonoMs = outcomes.last().receivedMonoMs
        return current.copy(
            lastReceivedMonoMs = maxOf(current.lastReceivedMonoMs ?: lastOutcomeMonoMs, lastOutcomeMonoMs),
            rollingSentenceRatePerSecond = computeSentenceRate(lastOutcomeMonoMs),
            acceptedSentenceCount = acceptedSentenceCount,
            rejectedSentenceCount = rejectedSentenceCount,
            checksumFailureCount = checksumFailureCount,
            parseFailureCount = parseFailureCount
        )
    }

    fun withTransportError(
        current: LxBluetoothRuntimeDiagnostics,
        error: BluetoothConnectionError
    ): LxBluetoothRuntimeDiagnostics = current.copy(lastTransportError = error)

    fun clearTransportError(
        current: LxBluetoothRuntimeDiagnostics
    ): LxBluetoothRuntimeDiagnostics = current.copy(lastTransportError = null)

    fun clearSession(
        preservedError: BluetoothConnectionError?
    ): LxBluetoothRuntimeDiagnostics {
        recentSentenceMonoMs.clear()
        return LxBluetoothRuntimeDiagnostics(lastTransportError = preservedError)
    }

    private fun trimWindow(referenceMonoMs: Long) {
        while (recentSentenceMonoMs.isNotEmpty()) {
            val oldestMonoMs = recentSentenceMonoMs.first()
            if (referenceMonoMs - oldestMonoMs < sentenceRateWindowMs) break
            recentSentenceMonoMs.removeFirst()
        }
    }

    private fun computeSentenceRate(referenceMonoMs: Long): Double {
        trimWindow(referenceMonoMs)
        return (recentSentenceMonoMs.size * 1_000.0) / sentenceRateWindowMs.toDouble()
    }

    companion object {
        internal const val DEFAULT_SENTENCE_RATE_WINDOW_MS: Long = 1_000L
    }
}


