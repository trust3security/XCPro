package com.example.xcpro

import javax.inject.Inject
import org.json.JSONObject

class AppConfigUseCase @Inject constructor(
    private val repository: ConfigurationRepository
) {
    suspend fun readConfig(): JSONObject? = repository.readConfig()
}
