package com.example.xcpro.map.ui

internal data class SkySightUiMessages(
    val warningMessage: String?,
    val errorMessage: String?
)

internal fun resolveSkySightUiMessages(
    repositoryWarningMessage: String?,
    regionCoverageWarningMessage: String?,
    runtimeWarningMessage: String?,
    repositoryErrorMessage: String?,
    runtimeErrorMessage: String?
): SkySightUiMessages {
    val errorMessages = normalizeMessages(
        repositoryErrorMessage,
        runtimeErrorMessage
    )
    val warningMessages = normalizeMessages(
        repositoryWarningMessage,
        regionCoverageWarningMessage,
        runtimeWarningMessage
    ).filter { warning ->
        errorMessages.none { error -> warning.equals(error, ignoreCase = true) }
    }

    return SkySightUiMessages(
        warningMessage = warningMessages.joinToString(" | ").ifBlank { null },
        errorMessage = errorMessages.joinToString(" | ").ifBlank { null }
    )
}

private fun normalizeMessages(vararg messages: String?): List<String> = messages.asSequence()
    .filterNotNull()
    .flatMap { message ->
        message.split("|").asSequence()
    }
    .map { it.trim() }
    .filter { it.isNotEmpty() }
    .distinctBy { it.lowercase() }
    .toList()
