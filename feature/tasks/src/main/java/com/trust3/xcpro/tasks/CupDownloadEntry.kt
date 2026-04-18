package com.trust3.xcpro.tasks

import com.trust3.xcpro.common.documents.DocumentRef

data class CupDownloadEntry(
    val document: DocumentRef,
    val displayName: String,
    val sizeBytes: Long,
    val lastModifiedEpochMillis: Long
)
