package com.trust3.xcpro.adsb.metadata.domain

sealed interface MetadataAvailability {
    data object Ready : MetadataAvailability
    data object Missing : MetadataAvailability
    data object SyncInProgress : MetadataAvailability
    data class Unavailable(val errorSummary: String) : MetadataAvailability
}

