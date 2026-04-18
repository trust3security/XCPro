package com.trust3.xcpro.weglide.domain

interface WeGlideUploadWorkScheduler {
    fun enqueueUpload(localFlightId: String, wifiOnly: Boolean)
}
