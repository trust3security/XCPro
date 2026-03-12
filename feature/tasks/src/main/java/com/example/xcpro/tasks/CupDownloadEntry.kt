package com.example.xcpro.tasks

import com.example.xcpro.common.documents.DocumentRef

data class CupDownloadEntry(
    val document: DocumentRef,
    val displayName: String,
    val sizeBytes: Long,
    val lastModifiedEpochMillis: Long
)
