package com.example.xcpro.map

import com.example.xcpro.ConfigurationRepository
import javax.inject.Inject
import javax.inject.Singleton
import org.json.JSONObject

@Singleton
class MapStyleRepository @Inject constructor(
    private val configurationRepository: ConfigurationRepository
) {
    fun initialStyle(): String {
        val defaultStyle = "Topo"
        val cached = configurationRepository.getCachedConfig()
        return cached
            ?.optJSONObject("app")
            ?.optString("mapStyle")
            ?.takeUnless { it.isNullOrBlank() }
            ?: defaultStyle
    }

    suspend fun saveStyle(style: String) {
        configurationRepository.updateConfig { json ->
            val appObject = json.optJSONObject("app") ?: JSONObject()
            appObject.put("mapStyle", style)
            json.put("app", appObject)
        }
    }
}
