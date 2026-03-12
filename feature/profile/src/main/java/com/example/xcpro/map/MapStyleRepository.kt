package com.example.xcpro.map

import com.example.xcpro.ConfigurationRepository
import javax.inject.Inject
import javax.inject.Singleton
import org.json.JSONObject

@Singleton
class MapStyleRepository @Inject constructor(
    private val configurationRepository: ConfigurationRepository
) {
    private var activeProfileId: String = DEFAULT_PROFILE_ID

    fun setActiveProfileId(profileId: String) {
        activeProfileId = resolveProfileId(profileId)
    }

    fun initialStyle(): String {
        return readProfileStyle(activeProfileId)
    }

    fun readProfileStyle(profileId: String): String {
        val defaultStyle = "Topo"
        val resolvedProfileId = resolveProfileId(profileId)
        val cached = configurationRepository.getCachedConfig()
        val app = cached?.optJSONObject("app")
        val scopedStyle = app
            ?.optJSONObject(KEY_MAP_STYLE_BY_PROFILE)
            ?.optString(resolvedProfileId)
            ?.takeUnless { it.isNullOrBlank() }
        if (scopedStyle != null) {
            return scopedStyle
        }
        if (isLegacyFallbackEligible(resolvedProfileId)) {
            return app
                ?.optString(KEY_LEGACY_MAP_STYLE)
                ?.takeUnless { it.isNullOrBlank() }
                ?: defaultStyle
        }
        return defaultStyle
    }

    suspend fun saveStyle(style: String) {
        writeProfileStyle(activeProfileId, style)
    }

    suspend fun writeProfileStyle(profileId: String, style: String) {
        val resolvedProfileId = resolveProfileId(profileId)
        configurationRepository.updateConfig { json ->
            val appObject = json.optJSONObject("app") ?: JSONObject()
            val byProfile = appObject.optJSONObject(KEY_MAP_STYLE_BY_PROFILE) ?: JSONObject()
            byProfile.put(resolvedProfileId, style)
            appObject.put(KEY_MAP_STYLE_BY_PROFILE, byProfile)
            if (isLegacyFallbackEligible(resolvedProfileId)) {
                appObject.put(KEY_LEGACY_MAP_STYLE, style)
            }
            json.put("app", appObject)
        }
    }

    suspend fun clearProfile(profileId: String) {
        val resolvedProfileId = resolveProfileId(profileId)
        configurationRepository.updateConfig { json ->
            val appObject = json.optJSONObject("app") ?: return@updateConfig
            val byProfile = appObject.optJSONObject(KEY_MAP_STYLE_BY_PROFILE)
            byProfile?.remove(resolvedProfileId)
            if (byProfile != null) {
                if (byProfile.length() == 0) {
                    appObject.remove(KEY_MAP_STYLE_BY_PROFILE)
                } else {
                    appObject.put(KEY_MAP_STYLE_BY_PROFILE, byProfile)
                }
            }
            if (isLegacyFallbackEligible(resolvedProfileId)) {
                appObject.remove(KEY_LEGACY_MAP_STYLE)
            }
            json.put("app", appObject)
        }
    }

    private fun resolveProfileId(profileId: String?): String {
        val normalized = profileId?.trim().orEmpty()
        if (normalized.isBlank()) return DEFAULT_PROFILE_ID
        return when (normalized) {
            DEFAULT_PROFILE_ID,
            LEGACY_DEFAULT_ALIAS,
            LEGACY_DF_ALIAS -> DEFAULT_PROFILE_ID
            else -> normalized
        }
    }

    private fun isLegacyFallbackEligible(profileId: String): Boolean {
        return profileId == DEFAULT_PROFILE_ID
    }

    private companion object {
        private const val DEFAULT_PROFILE_ID = "default-profile"
        private const val LEGACY_DEFAULT_ALIAS = "default"
        private const val LEGACY_DF_ALIAS = "__default_profile__"
        private const val KEY_LEGACY_MAP_STYLE = "mapStyle"
        private const val KEY_MAP_STYLE_BY_PROFILE = "mapStyleByProfile"
    }
}
