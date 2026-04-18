package com.trust3.xcpro.map

import com.google.gson.GsonBuilder
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.time.Instant
import kotlin.math.ceil
import org.junit.Assume.assumeTrue

internal object MapscreenPerfEvidenceSupport {
    private val gson = GsonBuilder()
        .disableHtmlEscaping()
        .setPrettyPrinting()
        .create()

    fun assumePerfEvidenceEnabled() {
        val enabled = System.getProperty("xcpro.enablePerfEvidence") == "true" ||
            System.getProperty("org.gradle.project.xcpro.enablePerfEvidence") == "true"
        assumeTrue(enabled)
    }

    fun percentileMs(samplesNs: List<Long>, percentile: Int): Double {
        if (samplesNs.isEmpty()) return 0.0
        return percentileLong(samplesNs, percentile) / 1_000_000.0
    }

    fun percentileLong(samples: List<Long>, percentile: Int): Long {
        if (samples.isEmpty()) return 0L
        val sorted = samples.sorted()
        val index = ceil((percentile / 100.0) * sorted.size).toInt().coerceAtLeast(1) - 1
        return sorted[index]
    }

    fun usedHeapBytes(): Long {
        val runtime = Runtime.getRuntime()
        return runtime.totalMemory() - runtime.freeMemory()
    }

    fun forceGc() {
        repeat(2) {
            System.gc()
            System.runFinalization()
        }
    }

    fun writePackageEvidence(packageId: String, payload: Map<String, Any?>) {
        val output = reportPath(packageId)
        Files.createDirectories(output.parent)
        Files.newBufferedWriter(
            output,
            StandardCharsets.UTF_8,
            StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING,
            StandardOpenOption.WRITE
        ).use { writer ->
            writer.write(gson.toJson(payload))
            writer.newLine()
        }
    }

    fun reportPath(packageId: String): Path =
        Path.of("build", "reports", "perf", "mapscreen", "$packageId-evidence.json")

    fun nowIsoString(): String = Instant.now().toString()
}
