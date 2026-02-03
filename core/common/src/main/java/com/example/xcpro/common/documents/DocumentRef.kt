package com.example.xcpro.common.documents

/**
 * Platform-free document handle for persisted or external files.
 *
 * - uri is a String to avoid exposing Android Uri outside repositories/UI.
 * - displayName is optional; when absent, derive a label from uri.
 */
data class DocumentRef(
    val uri: String,
    val displayName: String? = null
) {
    fun fileName(): String {
        val name = displayName?.takeIf { it.isNotBlank() }
        if (name != null) return name
        val lastSlash = uri.lastIndexOf('/')
        val segment = if (lastSlash >= 0 && lastSlash + 1 < uri.length) {
            uri.substring(lastSlash + 1)
        } else {
            uri
        }
        return segment.ifBlank { "Unknown" }
    }
}
