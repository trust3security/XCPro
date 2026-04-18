package com.trust3.xcpro.variometer.bluetooth.lxnav.runtime

import com.trust3.xcpro.variometer.bluetooth.BluetoothConnectionError
import com.trust3.xcpro.variometer.bluetooth.BluetoothReadChunk
import java.nio.charset.StandardCharsets

internal data class LxSanitizedCaptureFixture(
    val sessions: List<LxSanitizedCaptureSession>,
    val sentenceInventory: Set<String>
)

internal data class LxSanitizedCaptureSession(
    val chunks: List<BluetoothReadChunk>,
    val terminalError: BluetoothConnectionError? = null
)

internal object LxSanitizedCaptureFixtureLoader {

    fun load(resourceName: String): LxSanitizedCaptureFixture {
        val resourcePath =
            "/com/trust3/xcpro/variometer/bluetooth/lxnav/fixtures/$resourceName"
        val content = requireNotNull(
            LxSanitizedCaptureFixtureLoader::class.java.getResourceAsStream(resourcePath)
        ) {
            "Missing capture fixture: $resourcePath"
        }.bufferedReader(StandardCharsets.US_ASCII).use { it.readText() }

        val sessions = mutableListOf<LxSanitizedCaptureSession>()
        val sentenceInventory = linkedSetOf<String>()

        var currentLines: MutableList<BluetoothReadChunk>? = null
        var currentTerminalError: BluetoothConnectionError? = null

        content.lineSequence().forEach { rawLine ->
            val line = rawLine.trim()
            if (line.isEmpty() || line.startsWith("#")) return@forEach

            when {
                line == "@session start" -> {
                    require(currentLines == null) { "Nested session start in $resourceName" }
                    currentLines = mutableListOf()
                    currentTerminalError = null
                }

                line == "@session end" -> {
                    val completedLines = requireNotNull(currentLines) {
                        "Session end without active session in $resourceName"
                    }
                    sessions += LxSanitizedCaptureSession(
                        chunks = completedLines.toList(),
                        terminalError = currentTerminalError
                    )
                    currentLines = null
                    currentTerminalError = null
                }

                line.startsWith("@event ") -> {
                    requireNotNull(currentLines) { "Event outside session in $resourceName" }
                    val tokens = line.removePrefix("@event ").split(" ")
                    val event = tokens.associate { token ->
                        val delimiter = token.indexOf('=')
                        require(delimiter > 0) { "Malformed event token '$token' in $resourceName" }
                        token.substring(0, delimiter) to token.substring(delimiter + 1)
                    }
                    if (event["type"] == "error") {
                        currentTerminalError = BluetoothConnectionError.valueOf(
                            requireNotNull(event["error"]) { "Missing error token in $resourceName" }
                        )
                    }
                }

                else -> {
                    val lines = requireNotNull(currentLines) {
                        "Sentence outside session in $resourceName"
                    }
                    val separatorIndex = line.indexOf('|')
                    require(separatorIndex > 0) { "Malformed sentence line '$line' in $resourceName" }
                    val monoMs = line.substring(0, separatorIndex).toLong()
                    val sentence = line.substring(separatorIndex + 1)
                    sentenceInventory += sentence.removePrefix("$").substringBefore(',')
                    lines += BluetoothReadChunk(
                        bytes = "$sentence\n".toByteArray(StandardCharsets.US_ASCII),
                        receivedMonoMs = monoMs
                    )
                }
            }
        }

        require(currentLines == null) { "Unclosed session in $resourceName" }
        return LxSanitizedCaptureFixture(
            sessions = sessions.toList(),
            sentenceInventory = sentenceInventory.toSet()
        )
    }
}
