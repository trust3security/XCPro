package com.example.xcpro.weglide.domain

import com.example.xcpro.common.documents.DocumentRef

data class WeGlideFinalizedFlightUploadRequest(
    val localFlightId: String,
    val document: DocumentRef,
    val scoringDate: String? = null
)
