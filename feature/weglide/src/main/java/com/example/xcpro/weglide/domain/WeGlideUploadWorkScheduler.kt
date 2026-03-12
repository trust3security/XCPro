package com.example.xcpro.weglide.domain

interface WeGlideUploadWorkScheduler {
    fun enqueueUpload(localFlightId: String, wifiOnly: Boolean)
}
