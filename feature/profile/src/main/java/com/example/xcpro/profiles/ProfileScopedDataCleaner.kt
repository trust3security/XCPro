package com.example.xcpro.profiles

interface ProfileScopedDataCleaner {
    suspend fun clearProfileData(profileId: String)
}

class NoOpProfileScopedDataCleaner : ProfileScopedDataCleaner {
    override suspend fun clearProfileData(profileId: String) = Unit
}
