package com.trust3.xcpro.profiles

import com.trust3.xcpro.ConfigurationRepository
import javax.inject.Inject
import org.json.JSONObject

class ProfilesConfigUseCase @Inject constructor(
    private val repository: ConfigurationRepository
) {
    suspend fun readConfig(): JSONObject? = repository.readConfig()
}
