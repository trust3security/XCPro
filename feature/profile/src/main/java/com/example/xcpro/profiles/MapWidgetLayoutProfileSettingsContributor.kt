package com.example.xcpro.profiles

import com.example.xcpro.core.common.geometry.OffsetPx
import com.example.xcpro.core.common.profiles.ProfileSettingsApplyContributor
import com.example.xcpro.core.common.profiles.ProfileSettingsCaptureContributor
import com.example.xcpro.map.widgets.MapWidgetId
import com.example.xcpro.map.widgets.MapWidgetLayoutRepository
import com.google.gson.Gson
import com.google.gson.JsonElement
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MapWidgetLayoutProfileSettingsContributor @Inject constructor(
    private val mapWidgetLayoutRepository: MapWidgetLayoutRepository
) : ProfileSettingsCaptureContributor, ProfileSettingsApplyContributor {

    private val gson = Gson()

    override val sectionIds: Set<String> = setOf(ProfileSettingsSectionIds.MAP_WIDGET_LAYOUT)

    override suspend fun captureSection(
        sectionId: String,
        profileIds: Set<String>
    ): JsonElement? {
        if (sectionId != ProfileSettingsSectionIds.MAP_WIDGET_LAYOUT) return null
        val widgetsByProfile = profileIds.associateWith { profileId ->
            MapWidgetId.entries.associate { widgetId ->
                widgetId.name to MapWidgetPlacementSnapshot(
                    offset = mapWidgetLayoutRepository.readOffset(profileId, widgetId)
                        ?.toSnapshotOffset(),
                    sizePx = mapWidgetLayoutRepository.readSizePx(profileId, widgetId)
                )
            }
        }
        return gson.toJsonTree(MapWidgetLayoutSectionSnapshot(widgetsByProfile = widgetsByProfile))
    }

    override suspend fun applySection(
        sectionId: String,
        payload: JsonElement,
        importedProfileIdMap: Map<String, String>
    ) {
        if (sectionId != ProfileSettingsSectionIds.MAP_WIDGET_LAYOUT) return
        val section = gson.fromJson(payload, MapWidgetLayoutSectionSnapshot::class.java)
        if (section.widgetsByProfile.isNotEmpty()) {
            val defaultProfileWidgets = section.widgetsByProfile.entries
                .asSequence()
                .filter { (sourceProfileId, _) ->
                    ProfileIdResolver.canonicalOrDefault(sourceProfileId) ==
                        ProfileIdResolver.CANONICAL_DEFAULT_PROFILE_ID
                }
                .map { (_, widgets) -> widgets }
                .firstOrNull(::hasPersistedMapWidgetData)
            section.widgetsByProfile.forEach { (sourceProfileId, widgetMap) ->
                val profileId = resolveImportedProfileId(sourceProfileId, importedProfileIdMap)
                    ?: return@forEach
                val widgetsToApply = if (!hasPersistedMapWidgetData(widgetMap) &&
                    defaultProfileWidgets != null
                ) {
                    defaultProfileWidgets
                } else {
                    widgetMap
                }
                applyMapWidgetLayoutForProfile(profileId, widgetsToApply)
            }
            return
        }
        val legacyWidgets = section.widgets ?: return
        val targetProfileIds = importedProfileIdMap.values.toSet().ifEmpty {
            setOf(ProfileIdResolver.CANONICAL_DEFAULT_PROFILE_ID)
        }
        targetProfileIds.forEach { profileId ->
            applyMapWidgetLayoutForProfile(profileId, legacyWidgets)
        }
    }

    private fun applyMapWidgetLayoutForProfile(
        profileId: String,
        widgets: Map<String, MapWidgetPlacementSnapshot>
    ) {
        widgets.forEach { (widgetName, placement) ->
            val widgetId = runCatching { MapWidgetId.valueOf(widgetName) }.getOrNull() ?: return@forEach
            placement.offset?.let { offset ->
                mapWidgetLayoutRepository.saveOffset(profileId, widgetId, OffsetPx(offset.x, offset.y))
            }
            placement.sizePx?.let { sizePx ->
                mapWidgetLayoutRepository.saveSizePx(profileId, widgetId, sizePx)
            }
        }
    }

    private fun hasPersistedMapWidgetData(
        widgets: Map<String, MapWidgetPlacementSnapshot>
    ): Boolean {
        return widgets.values.any { placement ->
            placement.offset != null || placement.sizePx != null
        }
    }

    private fun OffsetPx.toSnapshotOffset(): OffsetSnapshot = OffsetSnapshot(x = x, y = y)
}
