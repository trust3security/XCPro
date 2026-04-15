package com.example.xcpro.profiles

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser

data class ProfileBundleImportRequest(
    val json: String,
    val keepCurrentActive: Boolean = true,
    val nameCollisionPolicy: ProfileNameCollisionPolicy = ProfileNameCollisionPolicy.KEEP_BOTH_SUFFIX,
    val preserveImportedPreferences: Boolean = true,
    val settingsImportScope: ProfileSettingsImportScope =
        ProfileSettingsImportScope.PROFILE_SCOPED_SETTINGS,
    val strictSettingsRestore: Boolean = false
)

enum class ProfileSettingsImportScope {
    PROFILES_ONLY,
    PROFILE_SCOPED_SETTINGS,
    FULL_BUNDLE
}

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
    val exportedAtWallMs: Long,
    val activeProfileId: String?,
    val profiles: List<UserProfile>,
    val settings: ProfileSettingsSnapshot = ProfileSettingsSnapshot.empty()
)

data class ParsedProfileBundle(
    val profiles: List<UserProfile>,
    val activeProfileId: String?,
    val settingsSnapshot: ProfileSettingsSnapshot,
    val sourceFormat: ProfileBundleSourceFormat,
    val schemaVersion: String,
    val exportedAtWallMs: Long? = null,
    val exportedAtLabel: String? = null
)

data class ManagedProfileIndexPointer(
    val bundleFileName: String?
)

private const val BUNDLE_VERSION = "2.0"
private val VERSION_REGEX = Regex("""^(\d+)(?:\.(\d+))?.*$""")

object ProfileBundleCodec {
    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()

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
                    "an aircraft profile JSON file, or a full bundle export."
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
        val version = parseVersionOrDefault(
            rawVersion = root.getStringOrNull("version"),
            defaultMajor = 1,
            defaultMinor = 0
        )
        require(version.major == 1) {
            unsupportedVersionMessage(
                format = "backup profile document",
                rawVersion = root.getStringOrNull("version"),
                supported = "1.x"
            )
        }
        val normalizedProfile = gson.parseUserProfileOrNull(root.get("profile"))
            ?.normalizedForPersistence()
            ?: error("No profiles found in selected import file.")
        return ParsedProfileBundle(
            profiles = listOf(normalizedProfile),
            activeProfileId = if (root.getBooleanOrFalse("isActive")) normalizedProfile.id else null,
            settingsSnapshot = ProfileSettingsSnapshot.empty(),
            sourceFormat = ProfileBundleSourceFormat.BACKUP_PROFILE_DOCUMENT_V1,
            schemaVersion = root.getStringOrNull("version") ?: "1.0",
            exportedAtWallMs = root.getLongOrNull("generatedAtWallMs")?.takeIf { it > 0L }
        )
    }

    private fun parseAsBundleOrLegacy(root: JsonObject): ParsedProfileBundle? {
        if (!root.has("profiles")) return null
        val hasBundleMarkers = root.has("settings") ||
            root.has("settingsSnapshot") ||
            root.has("activeProfileId")

        return if (hasBundleMarkers) {
            val version = parseVersionOrDefault(
                rawVersion = root.getStringOrNull("version"),
                defaultMajor = 2,
                defaultMinor = 0
            )
            require(version.major == 1 || version.major == 2) {
                unsupportedVersionMessage(
                    format = "profile bundle",
                    rawVersion = root.getStringOrNull("version"),
                    supported = "1.x, 2.x"
                )
            }
            val activeProfileId = root.getStringOrNull("activeProfileId")
            val settingsElement = when {
                root.has("settings") -> root.get("settings")
                root.has("settingsSnapshot") -> root.get("settingsSnapshot")
                else -> null
            }
            val settings = parseSettingsSnapshot(settingsElement)
            val profiles = normalizeProfilesForPersistence(
                parseProfilesArray(root.get("profiles")).filterNotNull()
            ).profiles
            if (profiles.isEmpty()) {
                error("No profiles found in selected import file.")
            }
            ParsedProfileBundle(
                profiles = profiles,
                activeProfileId = activeProfileId,
                settingsSnapshot = settings,
                sourceFormat = ProfileBundleSourceFormat.BUNDLE_V2,
                schemaVersion = root.getStringOrNull("version") ?: BUNDLE_VERSION,
                exportedAtWallMs = root.getLongOrNull("exportedAtWallMs")
            )
        } else {
            val legacyVersion = parseVersionOrDefault(
                rawVersion = root.getStringOrNull("version"),
                defaultMajor = 1,
                defaultMinor = 0
            )
            require(legacyVersion.major == 1) {
                unsupportedVersionMessage(
                    format = "legacy profile export",
                    rawVersion = root.getStringOrNull("version"),
                    supported = "1.x"
                )
            }
            val profiles = normalizeProfilesForPersistence(
                parseProfilesArray(root.get("profiles")).filterNotNull()
            ).profiles
            if (profiles.isEmpty()) {
                error("No profiles found in selected import file.")
            }
            ParsedProfileBundle(
                profiles = profiles,
                activeProfileId = null,
                settingsSnapshot = ProfileSettingsSnapshot.empty(),
                sourceFormat = ProfileBundleSourceFormat.LEGACY_PROFILE_EXPORT_V1,
                schemaVersion = root.getStringOrNull("version") ?: "1.0",
                exportedAtLabel = root.getStringOrNull("exportDate")
            )
        }
    }

    private fun parseProfilesArray(element: JsonElement?): List<UserProfile?> =
        gson.parseUserProfilesArray(element)

    private fun parseSettingsSnapshot(element: JsonElement?): ProfileSettingsSnapshot {
        if (element == null || element.isJsonNull || !element.isJsonObject) {
            return ProfileSettingsSnapshot.empty()
        }
        val root = element.asJsonObject
        val settingsVersion = parseVersionOrDefault(
            rawVersion = root.getStringOrNull("version"),
            defaultMajor = 1,
            defaultMinor = 0
        )
        require(settingsVersion.major == 1) {
            unsupportedVersionMessage(
                format = "profile settings snapshot",
                rawVersion = root.getStringOrNull("version"),
                supported = "1.x"
            )
        }
        return runCatching {
            gson.fromJson(element, ProfileSettingsSnapshot::class.java)
        }.getOrElse { error ->
            error("Invalid settings snapshot payload: ${error.message ?: "unknown"}")
        }
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

    private fun JsonObject.getLongOrNull(key: String): Long? {
        if (!has(key)) return null
        val value = get(key)
        if (value == null || value.isJsonNull) return null
        return runCatching { value.asLong }.getOrNull()
    }

    private fun JsonObject.getBooleanOrFalse(key: String): Boolean {
        if (!has(key)) return false
        val value = get(key)
        if (value == null || value.isJsonNull) return false
        return runCatching { value.asBoolean }.getOrDefault(false)
    }

    private fun parseVersionOrDefault(
        rawVersion: String?,
        defaultMajor: Int,
        defaultMinor: Int
    ): ParsedVersion {
        if (rawVersion.isNullOrBlank()) {
            return ParsedVersion(defaultMajor, defaultMinor)
        }
        val match = VERSION_REGEX.matchEntire(rawVersion.trim())
            ?: error("Unsupported version '$rawVersion'.")
        val major = match.groupValues[1].toIntOrNull()
            ?: error("Unsupported version '$rawVersion'.")
        val minor = match.groupValues.getOrNull(2)?.takeIf { it.isNotBlank() }?.toIntOrNull() ?: 0
        return ParsedVersion(major, minor)
    }

    private fun unsupportedVersionMessage(
        format: String,
        rawVersion: String?,
        supported: String
    ): String {
        val actual = rawVersion?.ifBlank { "<blank>" } ?: "<missing>"
        return "Unsupported $format version '$actual'. Supported versions: $supported."
    }

    private data class ParsedVersion(val major: Int, val minor: Int)
}
