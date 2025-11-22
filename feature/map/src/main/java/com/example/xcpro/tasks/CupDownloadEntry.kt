package com.example.xcpro.tasks

import android.net.Uri

data class CupDownloadEntry(
    val uri: Uri,
    val displayName: String,
    val sizeBytes: Long,
    val lastModifiedEpochMillis: Long
)
