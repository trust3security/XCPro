package com.example.xcpro.profiles

import com.example.xcpro.core.time.TimeBridge
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken

data class ProfileBundleImportRequest(
    val json: String,
    val keepCurrentActive: Boolean = true,
    val nameCollisionPolicy: ProfileNameCollisionPolicy = ProfileNameCollisionPolicy.KEEP_BOTH_SUFFIX,
    val preserveImportedPreferences: Boolean = true
)

data class ProfileBundleImportResult(
    val profileImportResult: ProfileImportResult,
    val settingsRestoreResult: ProfileSettingsRestoreResult,
    val sourceFormat: ProfileBundleSourceFormat
)

enum class ProfileBundleSourceFormat {
    BUNDLE_V2,
    LEGACY_PROFILE_EXPORT_V1,
    BACKUP_PROFILE_DOCUMENT_V1
}

data class ProfileBundleDocument(
    val version: String = BUNDLE_VERSION,
    val exportedAtWallMs: Long = TimeBridge.nowWallMs(),
    val activeProfileId: String?,
    val profiles: List<UserProfile>,
    val settings: ProfileSettingsSnapshot = ProfileSettingsSnapshot.empty()
)

data class ParsedProfileBundle(
    val profiles: List<UserProfile>,
    val activeProfileId: String?,
    val settingsSnapshot: ProfileSettingsSnapshot,
    val sourceFormat: ProfileBundleSourceFormat
)

data class ManagedProfileIndexPointer(
    val bundleFileName: String?
)

private data class LegacyProfileExportDocument(
    val version: String = "1.0",
    val exportDate: String? = null,
    val profiles: List<UserProfile> = emptyList()
)

private data class LegacyProfileBackupDocument(
    val version: String = "1.0",
    val generatedAtWallMs: Long = 0L,
    val sequenceNumber: Long = 0L,
    val isActive: Boolean = false,
    val profile: UserProfile
)

private const val BUNDLE_VERSION = "2.0"

object ProfileBundleCodec {
    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()
    private val profileListType = object : TypeToken<List<UserProfile>>() {}.type

    fun serialize(document: ProfileBundleDocument): String = gson.toJson(document)

    fun parse(json: String): Result<ParsedProfileBundle> = runCatching {
        val root = JsonParser.parseString(json)
        require(root.isJsonObject) { "Unsupported profile import format." }
        val rootObject = root.asJsonObject

        parseAsBackupDocument(rootObject)?.let { return@runCatching it }
        parseAsBundleOrLegacy(rootObject)?.let { return@runCatching it }
        val indexPointer = parseManagedIndexPointer(rootObject)
        if (indexPointer != null) {
            val bundleHint = indexPointer.bundleFileName ?: "*_bundle_latest.json"
            error(
                "Index-only backup file selected. Choose $bundleHint, " +
                    "a profile file (profile_*.json), or a full bundle export."
            )
        }
        error("Unsupported profile import format.")
    }

    fun parseManagedIndexPointer(json: String): ManagedProfileIndexPointer? {
        val root = runCatching { JsonParser.parseString(json) }.getOrNull() ?: return null
        if (!root.isJsonObject) return null
        return parseManagedIndexPointer(root.asJsonObject)
    }

    private fun parseAsBackupDocument(root: JsonObject): ParsedProfileBundle? {
        if (!root.has("profile")) return null
        val document = gson.fromJson(root, LegacyProfileBackupDocument::class.java)
        return ParsedProfileBundle(
            profiles = listOf(document.profile),
            activeProfileId = if (document.isActive) document.profile.id else null,
            settingsSnapshot = ProfileSettingsSnapshot.empty(),
            sourceFormat = ProfileBundleSourceFormat.BACKUP_PROFILE_DOCUMENT_V1
        )
    }

    private fun parseAsBundleOrLegacy(root: JsonObject): ParsedProfileBundle? {
        if (!root.has("profiles")) return null
        val profiles = parseProfilesArray(root.get("profiles"))
        if (profiles.isEmpty()) {
            error("No profiles found in selected import file.")
        }

        val hasBundleMarkers = root.has("settings") ||
            root.has("settingsSnapshot") ||
            root.has("activeProfileId")

        return if (hasBundleMarkers) {
            val activeProfileId = root.getStringOrNull("activeProfileId")
            val settingsElement = when {
                root.has("settings") -> root.get("settings")
                root.has("settingsSnapshot") -> root.get("settingsSnapshot")
                else -> null
            }
            val settings = parseSettingsSnapshot(settingsElement)
            ParsedProfileBundle(
                profiles = profiles,
                activeProfileId = activeProfileId,
                settingsSnapshot = settings,
                sourceFormat = ProfileBundleSourceFormat.BUNDLE_V2
            )
        } else {
            val legacy = gson.fromJson(root, LegacyProfileExportDocument::class.java)
            ParsedProfileBundle(
                profiles = legacy.profiles,
                activeProfileId = null,
                settingsSnapshot = ProfileSettingsSnapshot.empty(),
                sourceFormat = ProfileBundleSourceFormat.LEGACY_PROFILE_EXPORT_V1
            )
        }
    }

    private fun parseProfilesArray(element: JsonElement?): List<UserProfile> {
        if (element == null || element.isJsonNull || !element.isJsonArray) return emptyList()
        return gson.fromJson(element, profileListType) ?: emptyList()
    }

    private fun parseSettingsSnapshot(element: JsonElement?): ProfileSettingsSnapshot {
        if (element == null || element.isJsonNull || !element.isJsonObject) {
            return ProfileSettingsSnapshot.empty()
        }
        return runCatching {
            gson.fromJson(element, ProfileSettingsSnapshot::class.java)
        }.getOrElse { ProfileSettingsSnapshot.empty() }
    }

    private fun parseManagedIndexPointer(root: JsonObject): ManagedProfileIndexPointer? {
        if (!root.has("profileFiles")) return null
        return ManagedProfileIndexPointer(
            bundleFileName = root.getStringOrNull("bundleFileName")
        )
    }

    private fun JsonObject.getStringOrNull(key: String): String? {
        if (!has(key)) return null
        val value = get(key)
        if (value == null || value.isJsonNull) return null
        val text = runCatching { value.asString }.getOrNull()?.trim()
        return text?.takeIf { it.isNotEmpty() }
    }
}
