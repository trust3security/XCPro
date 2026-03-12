package com.example.xcpro.weglide.data

import com.example.xcpro.core.time.Clock
import com.example.xcpro.weglide.api.WeGlideApi
import com.example.xcpro.weglide.api.toDomain
import com.example.xcpro.weglide.auth.WeGlideAuthManager
import com.example.xcpro.weglide.domain.WeGlideLocalStateRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WeGlideAircraftSyncService @Inject constructor(
    private val api: WeGlideApi,
    private val authManager: WeGlideAuthManager,
    private val repository: WeGlideLocalStateRepository,
    private val clock: Clock
) {
    suspend fun sync(): Result<Int> = runCatching {
        val token = authManager.getValidAccessToken()
            ?.trim()
            ?.takeIf { value -> value.isNotEmpty() }
        val response = api.getAircraft(
            authorization = token?.let { value -> "Bearer $value" }
        )
        check(response.isSuccessful) {
            "Aircraft sync failed with HTTP ${response.code()}"
        }
        val items = response.body().orEmpty().map { dto -> dto.toDomain() }
        repository.replaceAircraft(
            aircraft = items,
            updatedAtEpochMs = clock.nowWallMs()
        )
        items.size
    }
}
