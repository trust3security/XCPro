package com.example.xcpro.weglide.domain

enum class WeGlideUploadState {
    QUEUED,
    UPLOADING,
    UPLOADED,
    FAILED_RETRYABLE,
    FAILED_PERMANENT,
    SKIPPED_DUPLICATE
}
