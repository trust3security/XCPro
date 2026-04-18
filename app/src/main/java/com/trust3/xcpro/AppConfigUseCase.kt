package com.trust3.xcpro

import javax.inject.Inject
import org.json.JSONObject

class AppConfigUseCase @Inject constructor(
    private val repository: ConfigurationRepository
) {
    suspend fun readConfig(): JSONObject? = repository.readConfig()
}
