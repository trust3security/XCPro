package com.example.xcpro.profiles

import com.example.xcpro.core.time.TimeBridge
import java.text.SimpleDateFormat
import java.util.Locale

object AircraftProfileFileNames {
    fun buildExportFileName(
        profile: UserProfile?,
        nowWallMs: Long = TimeBridge.nowWallMs()
    ): String {
        val exportDate = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(nowWallMs)
        return if (profile == null) {
            "xcpro-aircraft-profiles-$exportDate.json"
        } else {
            val aircraftTypeSlug = slugify(profile.aircraftType.displayName)
            val descriptorSlug = slugify(profile.aircraftModel ?: profile.name)
            "xcpro-aircraft-profile-$aircraftTypeSlug-$descriptorSlug-$exportDate.json"
        }
    }

    private fun slugify(raw: String): String {
        val slug = buildString(raw.length) {
            raw.trim().lowercase(Locale.US).forEach { char ->
                append(if (char.isLetterOrDigit()) char else '-')
            }
        }
            .replace(Regex("-+"), "-")
            .trim('-')

        return slug.ifBlank { "profile" }
    }
}
