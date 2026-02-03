package com.example.xcpro.profiles

import com.example.xcpro.ConfigurationRepository
import javax.inject.Inject
import org.json.JSONObject

class ProfilesConfigUseCase @Inject constructor(
    private val repository: ConfigurationRepository
) {
    suspend fun readConfig(): JSONObject? = repository.readConfig()
}
