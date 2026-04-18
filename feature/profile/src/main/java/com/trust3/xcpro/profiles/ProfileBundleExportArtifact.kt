package com.trust3.xcpro.profiles

data class ProfileBundleExportArtifact(
    val bundleJson: String,
    val exportedAtWallMs: Long,
    val suggestedFileName: String
)
