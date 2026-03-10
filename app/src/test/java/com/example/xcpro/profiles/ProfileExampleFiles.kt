package com.example.xcpro.profiles

import java.nio.file.Files
import java.nio.file.Path
import java.nio.charset.StandardCharsets

internal object ProfileExampleFiles {
    fun readString(fileName: String): String =
        String(Files.readAllBytes(resolve(fileName)), StandardCharsets.UTF_8)

    private fun resolve(fileName: String): Path {
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
        error("Could not resolve docs/PROFILES/examples/$fileName from ${System.getProperty("user.dir")}")
    }
}
