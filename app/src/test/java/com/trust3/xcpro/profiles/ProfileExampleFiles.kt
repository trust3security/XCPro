package com.trust3.xcpro.profiles

import java.nio.file.Files
import java.nio.file.Path
import java.nio.charset.StandardCharsets

internal object ProfileExampleFiles {
    private const val RESOURCE_PREFIX = "profiles/examples/"

    fun readString(fileName: String): String {
        val resourcePath = "$RESOURCE_PREFIX$fileName"
        ProfileExampleFiles::class.java.classLoader
            ?.getResourceAsStream(resourcePath)
            ?.use { return String(it.readBytes(), StandardCharsets.UTF_8) }
        return String(Files.readAllBytes(resolveDocsExample(fileName)), StandardCharsets.UTF_8)
    }

    private fun resolveDocsExample(fileName: String): Path {
        var current: Path? = Path.of(System.getProperty("user.dir")).toAbsolutePath()
        while (current != null) {
            val candidate = current
                .resolve("docs")
                .resolve("PROFILES")
                .resolve("examples")
                .resolve(fileName)
            if (Files.exists(candidate)) {
                return candidate
            }
            current = current.parent
        }
        error(
            "Could not resolve test resource ${RESOURCE_PREFIX}$fileName " +
                "or docs/PROFILES/examples/$fileName from ${System.getProperty("user.dir")}"
        )
    }
}
