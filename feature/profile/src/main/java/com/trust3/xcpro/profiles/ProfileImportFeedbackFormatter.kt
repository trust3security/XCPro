package com.trust3.xcpro.profiles

fun formatProfileImportFeedback(result: ProfileImportResult): String {
    if (result.failures.isEmpty()) {
        return "Imported ${result.importedCount} ${profileLabel(result.importedCount)}"
    }
    val preview = result.failures
        .take(2)
        .joinToString(separator = "; ") { it.detail }
    val remaining = result.failures.size - 2
    val suffix = if (remaining > 0) " (+$remaining more)" else ""
    return "Imported ${result.importedCount}/${result.requestedCount} ${profileLabel(result.requestedCount)}, skipped ${result.skippedCount}: $preview$suffix"
}

private fun profileLabel(count: Int): String {
    return if (count == 1) "profile" else "profiles"
}
