package com.trust3.xcpro.weglide.domain

import com.trust3.xcpro.common.documents.DocumentRef

data class WeGlideFinalizedFlightUploadRequest(
    val localFlightId: String,
    val document: DocumentRef,
    val scoringDate: String? = null
)
