package com.trust3.xcpro.map

import com.trust3.xcpro.ConfigurationRepository
import com.trust3.xcpro.core.common.profiles.ProfileSettingsProfileIds
import javax.inject.Inject
import javax.inject.Singleton
import org.json.JSONObject

@Singleton
class MapStyleRepository @Inject constructor(
    private val configurationRepository: ConfigurationRepository
) {
    private var activeProfileId: String = DEFAULT_PROFILE_ID

    fun setActiveProfileId(profileId: String) {
        activeProfileId = ProfileSettingsProfileIds.canonicalOrDefault(profileId)
    }

    fun initialStyle(): String {
        return readProfileStyle(activeProfileId)
    }

    fun readProfileStyle(profileId: String): String {
        val defaultStyle = MapStyleCatalog.defaultSelectableKey()
        val resolvedProfileId = ProfileSettingsProfileIds.canonicalOrDefault(profileId)
        val cached = configurationRepository.getCachedConfig()
        val app = cached?.optJSONObject("app")
        val scopedStyle = app
            ?.optJSONObject(KEY_MAP_STYLE_BY_PROFILE)
            ?.optString(resolvedProfileId)
            ?.takeUnless { it.isNullOrBlank() }
        if (scopedStyle != null) {
            return MapStyleCatalog.normalizeBaseStyleKey(scopedStyle)
        }
        if (isLegacyFallbackEligible(resolvedProfileId)) {
            return MapStyleCatalog.normalizeBaseStyleKey(
                app
                ?.optString(KEY_LEGACY_MAP_STYLE)
                ?.takeUnless { it.isNullOrBlank() }
                    ?: defaultStyle
            )
        }
        return defaultStyle
    }

    suspend fun saveStyle(style: String) {
        writeProfileStyle(activeProfileId, style)
    }

    suspend fun writeProfileStyle(profileId: String, style: String) {
        val resolvedProfileId = ProfileSettingsProfileIds.canonicalOrDefault(profileId)
        val normalizedStyle = MapStyleCatalog.normalizeBaseStyleKey(style)
        configurationRepository.updateConfig { json ->
            val appObject = json.optJSONObject("app") ?: JSONObject()
            val byProfile = appObject.optJSONObject(KEY_MAP_STYLE_BY_PROFILE) ?: JSONObject()
            byProfile.put(resolvedProfileId, normalizedStyle)
            appObject.put(KEY_MAP_STYLE_BY_PROFILE, byProfile)
            if (isLegacyFallbackEligible(resolvedProfileId)) {
                appObject.put(KEY_LEGACY_MAP_STYLE, normalizedStyle)
            }
            json.put("app", appObject)
        }
    }

    suspend fun clearProfile(profileId: String) {
        val resolvedProfileId = ProfileSettingsProfileIds.canonicalOrDefault(profileId)
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

    private fun isLegacyFallbackEligible(profileId: String): Boolean {
        return profileId == DEFAULT_PROFILE_ID
    }

    private companion object {
        private val DEFAULT_PROFILE_ID = ProfileSettingsProfileIds.CANONICAL_DEFAULT_PROFILE_ID
        private const val KEY_LEGACY_MAP_STYLE = "mapStyle"
        private const val KEY_MAP_STYLE_BY_PROFILE = "mapStyleByProfile"
    }
}
