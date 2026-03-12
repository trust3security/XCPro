package com.example.xcpro.profiles

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import com.example.dfcards.CardPreferences
import com.example.dfcards.FlightTemplate
import com.example.dfcards.dfcards.CardState
import com.example.dfcards.dfcards.FlightData
import com.example.dfcards.FlightModeSelection
import com.example.xcpro.adsb.AdsbTrafficPreferencesRepository
import com.example.xcpro.core.common.geometry.OffsetPx
import com.example.xcpro.flightdata.FlightMgmtPreferencesRepository
import com.example.xcpro.forecast.ForecastParameterId
import com.example.xcpro.forecast.ForecastPreferencesRepository
import com.example.xcpro.forecast.ForecastWindDisplayMode
import com.example.xcpro.glider.GliderRepository
import com.example.xcpro.MapOrientationSettings
import com.example.xcpro.MapOrientationSettingsRepository
import com.example.xcpro.common.units.UnitsRepository
import com.example.xcpro.map.MapStyleRepository
import com.example.xcpro.map.QnhPreferencesRepository
import com.example.xcpro.map.widgets.MapWidgetId
import com.example.xcpro.map.widgets.MapWidgetLayoutRepository
import com.example.xcpro.common.orientation.MapOrientationMode
import com.example.xcpro.ogn.OgnDisplayUpdateMode
import com.example.xcpro.ogn.OgnTrailSelectionPreferencesRepository
import com.example.xcpro.ogn.OgnTrafficPreferencesRepository
import com.example.xcpro.map.domain.MapShiftBiasMode
import com.example.xcpro.map.trail.MapTrailPreferences
import com.example.xcpro.map.trail.TrailLength
import com.example.xcpro.map.trail.TrailSettings
import com.example.xcpro.map.trail.TrailType
import com.example.xcpro.screens.navdrawer.lookandfeel.LookAndFeelPreferences
import com.example.xcpro.thermalling.ThermallingModePreferencesRepository
import com.example.xcpro.ui.theme.ThemePreferencesRepository
import com.example.xcpro.vario.LevoVarioPreferencesRepository
import com.example.xcpro.variometer.layout.VariometerWidgetRepository
import com.example.xcpro.weather.rain.WeatherOverlayPreferencesRepository
import com.example.xcpro.weather.rain.WeatherRainAnimationSpeed
import com.example.xcpro.weather.rain.WeatherRainAnimationWindow
import com.example.xcpro.weather.rain.WeatherRainTransitionQuality
import com.example.xcpro.weather.rain.WeatherRadarFrameMode
import com.example.xcpro.weather.wind.data.WindOverrideRepository
import com.google.gson.Gson
import com.google.gson.JsonElement
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppProfileSettingsRestoreApplier @Inject constructor(
    private val cardPreferences: CardPreferences,
    private val flightMgmtPreferencesRepository: FlightMgmtPreferencesRepository,
    private val lookAndFeelPreferences: LookAndFeelPreferences,
    private val themePreferencesRepository: ThemePreferencesRepository,
    private val mapWidgetLayoutRepository: MapWidgetLayoutRepository,
    private val variometerWidgetRepository: VariometerWidgetRepository,
    private val gliderRepository: GliderRepository,
    private val unitsRepository: UnitsRepository,
    private val mapStyleRepository: MapStyleRepository,
    private val mapTrailPreferences: MapTrailPreferences,
    private val qnhPreferencesRepository: QnhPreferencesRepository,
    private val orientationSettingsRepository: MapOrientationSettingsRepository,
    private val levoVarioPreferencesRepository: LevoVarioPreferencesRepository,
    private val thermallingModePreferencesRepository: ThermallingModePreferencesRepository,
    private val ognTrafficPreferencesRepository: OgnTrafficPreferencesRepository,
    private val ognTrailSelectionPreferencesRepository: OgnTrailSelectionPreferencesRepository,
    private val adsbTrafficPreferencesRepository: AdsbTrafficPreferencesRepository,
    private val weatherOverlayPreferencesRepository: WeatherOverlayPreferencesRepository,
    private val forecastPreferencesRepository: ForecastPreferencesRepository,
    private val windOverrideRepository: WindOverrideRepository
) : ProfileSettingsRestoreApplier {

    private val gson = Gson()

    override suspend fun apply(
        settingsSnapshot: ProfileSettingsSnapshot,
        importedProfileIdMap: Map<String, String>
    ): ProfileSettingsRestoreResult {
        if (settingsSnapshot.sections.isEmpty()) {
            return ProfileSettingsRestoreResult()
        }
        val applied = linkedSetOf<String>()
        val failed = linkedMapOf<String, String>()

        suspend fun applySection(sectionId: String, action: suspend (JsonElement) -> Unit) {
            val payload = settingsSnapshot.sections[sectionId] ?: return
            runCatching { action(payload) }
                .onSuccess { applied.add(sectionId) }
                .onFailure { error ->
                    failed[sectionId] = error.message ?: error.javaClass.simpleName
                }
        }

        applySection(ProfileSettingsSectionIds.CARD_PREFERENCES) { payload ->
            val section = gson.fromJson(payload, CardPreferencesSectionSnapshot::class.java)
            applyCardPreferences(section, importedProfileIdMap)
        }
        applySection(ProfileSettingsSectionIds.FLIGHT_MGMT_PREFERENCES) { payload ->
            val section = gson.fromJson(payload, FlightMgmtSectionSnapshot::class.java)
            applyFlightMgmtPreferences(section, importedProfileIdMap)
        }
        applySection(ProfileSettingsSectionIds.LOOK_AND_FEEL_PREFERENCES) { payload ->
            val section = gson.fromJson(payload, LookAndFeelSectionSnapshot::class.java)
            applyLookAndFeelPreferences(section, importedProfileIdMap)
        }
        applySection(ProfileSettingsSectionIds.THEME_PREFERENCES) { payload ->
            val section = gson.fromJson(payload, ThemeSectionSnapshot::class.java)
            applyThemePreferences(section, importedProfileIdMap)
        }
        applySection(ProfileSettingsSectionIds.MAP_WIDGET_LAYOUT) { payload ->
            val section = gson.fromJson(payload, MapWidgetLayoutSectionSnapshot::class.java)
            applyMapWidgetLayout(section, importedProfileIdMap)
        }
        applySection(ProfileSettingsSectionIds.VARIOMETER_WIDGET_LAYOUT) { payload ->
            val section = gson.fromJson(payload, VariometerWidgetLayoutSectionSnapshot::class.java)
            applyVariometerWidgetLayout(section, importedProfileIdMap)
        }
        applySection(ProfileSettingsSectionIds.GLIDER_CONFIG) { payload ->
            val section = gson.fromJson(payload, GliderSectionSnapshot::class.java)
            applyGliderSection(section, importedProfileIdMap)
        }
        applySection(ProfileSettingsSectionIds.UNITS_PREFERENCES) { payload ->
            val section = gson.fromJson(payload, UnitsSectionSnapshot::class.java)
            applyUnitsSection(section, importedProfileIdMap)
        }
        applySection(ProfileSettingsSectionIds.MAP_STYLE_PREFERENCES) { payload ->
            val section = gson.fromJson(payload, MapStyleSectionSnapshot::class.java)
            applyMapStyleSection(section, importedProfileIdMap)
        }
        applySection(ProfileSettingsSectionIds.SNAIL_TRAIL_PREFERENCES) { payload ->
            val section = gson.fromJson(payload, SnailTrailSectionSnapshot::class.java)
            applySnailTrailSection(section, importedProfileIdMap)
        }
        applySection(ProfileSettingsSectionIds.ORIENTATION_PREFERENCES) { payload ->
            val section = gson.fromJson(payload, OrientationSectionSnapshot::class.java)
            applyOrientationSection(section, importedProfileIdMap)
        }
        applySection(ProfileSettingsSectionIds.QNH_PREFERENCES) { payload ->
            val section = gson.fromJson(payload, QnhSectionSnapshot::class.java)
            applyQnhSection(section, importedProfileIdMap)
        }
        applySection(ProfileSettingsSectionIds.LEVO_VARIO_PREFERENCES) { payload ->
            val section = gson.fromJson(payload, LevoVarioSectionSnapshot::class.java)
            applyLevoVarioPreferences(section)
        }
        applySection(ProfileSettingsSectionIds.THERMALLING_MODE_PREFERENCES) { payload ->
            val section = gson.fromJson(payload, ThermallingModeSectionSnapshot::class.java)
            applyThermallingPreferences(section)
        }
        applySection(ProfileSettingsSectionIds.OGN_TRAFFIC_PREFERENCES) { payload ->
            val section = gson.fromJson(payload, OgnTrafficSectionSnapshot::class.java)
            applyOgnTrafficPreferences(section)
        }
        applySection(ProfileSettingsSectionIds.OGN_TRAIL_SELECTION_PREFERENCES) { payload ->
            val section = gson.fromJson(payload, OgnTrailSelectionSectionSnapshot::class.java)
            ognTrailSelectionPreferencesRepository.clearSelectedAircraft()
            section.selectedAircraftKeys.forEach { key ->
                ognTrailSelectionPreferencesRepository.setAircraftSelected(key, selected = true)
            }
        }
        applySection(ProfileSettingsSectionIds.ADSB_TRAFFIC_PREFERENCES) { payload ->
            val section = gson.fromJson(payload, AdsbTrafficSectionSnapshot::class.java)
            applyAdsbTrafficPreferences(section)
        }
        applySection(ProfileSettingsSectionIds.WEATHER_OVERLAY_PREFERENCES) { payload ->
            val section = gson.fromJson(payload, WeatherOverlaySectionSnapshot::class.java)
            applyWeatherOverlayPreferences(section)
        }
        applySection(ProfileSettingsSectionIds.FORECAST_PREFERENCES) { payload ->
            val section = gson.fromJson(payload, ForecastSectionSnapshot::class.java)
            applyForecastPreferences(section)
        }
        applySection(ProfileSettingsSectionIds.WIND_OVERRIDE_PREFERENCES) { payload ->
            val section = gson.fromJson(payload, WindOverrideSectionSnapshot::class.java)
            val override = section.manualOverride
            if (override == null) {
                windOverrideRepository.clearManualWind()
            } else {
                windOverrideRepository.setManualWind(
                    speedMs = override.speedMs,
                    directionFromDeg = override.directionFromDeg,
                    timestampMillis = override.timestampMillis
                )
            }
        }

        return ProfileSettingsRestoreResult(
            appliedSections = applied.toSet(),
            failedSections = failed.toMap()
        )
    }

    private suspend fun applyCardPreferences(
        section: CardPreferencesSectionSnapshot,
        importedProfileIdMap: Map<String, String>
    ) {
        val templates = section.templates.map { template ->
            FlightTemplate(
                id = template.id,
                name = template.name,
                description = template.description,
                cardIds = template.cardIds,
                icon = Icons.Default.Star,
                isPreset = template.isPreset,
                createdAt = template.createdAt
            )
        }
        cardPreferences.saveAllTemplates(templates)
        cardPreferences.setCardsAcrossPortrait(section.cardsAcrossPortrait)
        val anchor = runCatching {
            CardPreferences.CardAnchor.valueOf(section.cardsAnchorPortrait)
        }.getOrDefault(CardPreferences.CardAnchor.TOP)
        cardPreferences.setCardsAnchorPortrait(anchor)
        section.lastActiveTemplate?.let { cardPreferences.saveLastActiveTemplate(it) }
        cardPreferences.saveVarioSmoothingAlpha(section.varioSmoothingAlpha)

        section.profileTemplateCards.forEach { (sourceProfileId, templateMap) ->
            val profileId = resolveImportedProfileId(sourceProfileId, importedProfileIdMap)
                ?: return@forEach
            templateMap.forEach { (templateId, cardIds) ->
                cardPreferences.saveProfileTemplateCards(profileId, templateId, cardIds)
            }
        }
        section.profileFlightModeTemplates.forEach { (sourceProfileId, modeMap) ->
            val profileId = resolveImportedProfileId(sourceProfileId, importedProfileIdMap)
                ?: return@forEach
            modeMap.forEach { (mode, templateId) ->
                cardPreferences.saveProfileFlightModeTemplate(profileId, mode, templateId)
            }
        }
        section.profileFlightModeVisibilities.forEach { (sourceProfileId, visibilityMap) ->
            val profileId = resolveImportedProfileId(sourceProfileId, importedProfileIdMap)
                ?: return@forEach
            visibilityMap.forEach { (mode, visible) ->
                cardPreferences.saveProfileFlightModeVisibility(profileId, mode, visible)
            }
        }
        section.profileCardPositions.forEach { (sourceProfileId, modeMap) ->
            val profileId = resolveImportedProfileId(sourceProfileId, importedProfileIdMap)
                ?: return@forEach
            modeMap.forEach { (mode, positionMap) ->
                val states = positionMap.map { (cardId, position) ->
                    CardState(
                        id = cardId,
                        x = position.x,
                        y = position.y,
                        width = position.width,
                        height = position.height,
                        flightData = FlightData(
                            id = cardId,
                            label = "",
                            primaryValue = ""
                        )
                    )
                }
                cardPreferences.saveProfileCardPositions(profileId, mode, states)
            }
        }
    }

    private fun applyFlightMgmtPreferences(
        section: FlightMgmtSectionSnapshot,
        importedProfileIdMap: Map<String, String>
    ) {
        flightMgmtPreferencesRepository.setLastActiveTab(section.lastActiveTab)
        section.profileLastFlightModes.forEach { (sourceProfileId, modeName) ->
            val mode = runCatching { FlightModeSelection.valueOf(modeName) }
                .getOrDefault(FlightModeSelection.CRUISE)
            val profileId = resolveImportedProfileId(sourceProfileId, importedProfileIdMap)
                ?: return@forEach
            flightMgmtPreferencesRepository.setLastFlightMode(profileId, mode)
        }
    }

    private fun applyLookAndFeelPreferences(
        section: LookAndFeelSectionSnapshot,
        importedProfileIdMap: Map<String, String>
    ) {
        section.statusBarStyleByProfile.forEach { (sourceProfileId, styleId) ->
            val profileId = resolveImportedProfileId(sourceProfileId, importedProfileIdMap)
                ?: return@forEach
            lookAndFeelPreferences.setStatusBarStyleId(profileId, styleId)
        }
        section.cardStyleByProfile.forEach { (sourceProfileId, styleId) ->
            val profileId = resolveImportedProfileId(sourceProfileId, importedProfileIdMap)
                ?: return@forEach
            lookAndFeelPreferences.setCardStyleId(profileId, styleId)
        }
        section.colorThemeByProfile.forEach { (sourceProfileId, themeId) ->
            val profileId = resolveImportedProfileId(sourceProfileId, importedProfileIdMap)
                ?: return@forEach
            lookAndFeelPreferences.setColorThemeId(profileId, themeId)
        }
    }

    private fun applyThemePreferences(
        section: ThemeSectionSnapshot,
        importedProfileIdMap: Map<String, String>
    ) {
        section.themeIdByProfile.forEach { (sourceProfileId, themeId) ->
            val profileId = resolveImportedProfileId(sourceProfileId, importedProfileIdMap)
                ?: return@forEach
            themePreferencesRepository.setThemeId(profileId, themeId)
        }
        section.customColorsByProfileAndTheme.forEach { (sourceProfileId, themeMap) ->
            val profileId = resolveImportedProfileId(sourceProfileId, importedProfileIdMap)
                ?: return@forEach
            themeMap.forEach { (themeId, json) ->
                themePreferencesRepository.setCustomColorsJson(profileId, themeId, json)
            }
        }
    }

    private fun applyMapWidgetLayout(
        section: MapWidgetLayoutSectionSnapshot,
        importedProfileIdMap: Map<String, String>
    ) {
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

    private fun applyVariometerWidgetLayout(
        section: VariometerWidgetLayoutSectionSnapshot,
        importedProfileIdMap: Map<String, String>
    ) {
        if (section.layoutsByProfile.isNotEmpty()) {
            section.layoutsByProfile.forEach { (sourceProfileId, snapshot) ->
                val profileId = resolveImportedProfileId(sourceProfileId, importedProfileIdMap)
                    ?: return@forEach
                variometerWidgetRepository.saveOffset(
                    profileId = profileId,
                    offset = OffsetPx(snapshot.offset.x, snapshot.offset.y)
                )
                variometerWidgetRepository.saveSize(
                    profileId = profileId,
                    sizePx = snapshot.sizePx
                )
            }
            return
        }
        val legacyOffset = section.offset ?: return
        val legacySize = section.sizePx ?: return
        val targetProfileIds = importedProfileIdMap.values.toSet().ifEmpty {
            setOf(ProfileIdResolver.CANONICAL_DEFAULT_PROFILE_ID)
        }
        targetProfileIds.forEach { profileId ->
            variometerWidgetRepository.saveOffset(
                profileId = profileId,
                offset = OffsetPx(legacyOffset.x, legacyOffset.y)
            )
            variometerWidgetRepository.saveSize(profileId = profileId, sizePx = legacySize)
        }
    }

    private fun applyGliderSection(
        section: GliderSectionSnapshot,
        importedProfileIdMap: Map<String, String>
    ) {
        if (section.profiles.isNotEmpty()) {
            section.profiles.forEach { (sourceProfileId, snapshot) ->
                val profileId = resolveImportedProfileId(sourceProfileId, importedProfileIdMap)
                    ?: return@forEach
                gliderRepository.saveProfileSnapshot(
                    profileId = profileId,
                    selectedModelId = snapshot.selectedModelId,
                    config = snapshot.config
                )
            }
            return
        }
        val legacyConfig = section.config ?: return
        val targetProfileIds = importedProfileIdMap.values.toSet().ifEmpty {
            setOf(ProfileIdResolver.CANONICAL_DEFAULT_PROFILE_ID)
        }
        targetProfileIds.forEach { profileId ->
            gliderRepository.saveProfileSnapshot(
                profileId = profileId,
                selectedModelId = section.selectedModelId,
                config = legacyConfig
            )
        }
    }

    private suspend fun applyUnitsSection(
        section: UnitsSectionSnapshot,
        importedProfileIdMap: Map<String, String>
    ) {
        section.unitsByProfile.forEach { (sourceProfileId, preferences) ->
            val profileId = resolveImportedProfileId(sourceProfileId, importedProfileIdMap)
                ?: return@forEach
            unitsRepository.writeProfileUnits(profileId, preferences)
        }
    }

    private suspend fun applyMapStyleSection(
        section: MapStyleSectionSnapshot,
        importedProfileIdMap: Map<String, String>
    ) {
        section.stylesByProfile.forEach { (sourceProfileId, styleId) ->
            val profileId = resolveImportedProfileId(sourceProfileId, importedProfileIdMap)
                ?: return@forEach
            mapStyleRepository.writeProfileStyle(profileId, styleId)
        }
    }

    private fun applySnailTrailSection(
        section: SnailTrailSectionSnapshot,
        importedProfileIdMap: Map<String, String>
    ) {
        val defaults = TrailSettings()
        section.settingsByProfile.forEach { (sourceProfileId, snapshot) ->
            val profileId = resolveImportedProfileId(sourceProfileId, importedProfileIdMap)
                ?: return@forEach
            val length = runCatching { TrailLength.valueOf(snapshot.length) }
                .getOrDefault(defaults.length)
            val type = runCatching { TrailType.valueOf(snapshot.type) }
                .getOrDefault(defaults.type)
            mapTrailPreferences.writeProfileSettings(
                profileId = profileId,
                settings = TrailSettings(
                    length = length,
                    type = type,
                    windDriftEnabled = snapshot.windDriftEnabled,
                    scalingEnabled = snapshot.scalingEnabled
                )
            )
        }
    }

    private fun applyOrientationSection(
        section: OrientationSectionSnapshot,
        importedProfileIdMap: Map<String, String>
    ) {
        val defaults = MapOrientationSettings()
        section.settingsByProfile.forEach { (sourceProfileId, snapshot) ->
            val profileId = resolveImportedProfileId(sourceProfileId, importedProfileIdMap)
                ?: return@forEach
            val settings = MapOrientationSettings(
                cruiseMode = parseOrientationMode(snapshot.cruiseMode),
                circlingMode = parseOrientationMode(snapshot.circlingMode),
                minSpeedThresholdMs = snapshot.minSpeedThresholdMs,
                gliderScreenPercent = snapshot.gliderScreenPercent,
                mapShiftBiasMode = parseMapShiftBiasMode(snapshot.mapShiftBiasMode),
                mapShiftBiasStrength = snapshot.mapShiftBiasStrength,
                autoResetEnabled = snapshot.autoResetEnabled ?: defaults.autoResetEnabled,
                autoResetTimeoutSeconds = snapshot.autoResetTimeoutSeconds
                    ?: defaults.autoResetTimeoutSeconds,
                bearingSmoothingEnabled = snapshot.bearingSmoothingEnabled
                    ?: defaults.bearingSmoothingEnabled
            )
            orientationSettingsRepository.writeProfileSettings(profileId, settings)
        }
    }

    private suspend fun applyQnhSection(
        section: QnhSectionSnapshot,
        importedProfileIdMap: Map<String, String>
    ) {
        section.valuesByProfile.forEach { (sourceProfileId, snapshot) ->
            val profileId = resolveImportedProfileId(sourceProfileId, importedProfileIdMap)
                ?: return@forEach
            if (snapshot.manualQnhHpa == null) {
                qnhPreferencesRepository.clearProfile(profileId)
            } else {
                qnhPreferencesRepository.writeProfileManualQnh(
                    profileId = profileId,
                    qnhHpa = snapshot.manualQnhHpa,
                    capturedAtWallMs = snapshot.capturedAtWallMs,
                    source = snapshot.source
                )
            }
        }
    }

    private suspend fun applyLevoVarioPreferences(section: LevoVarioSectionSnapshot) {
        levoVarioPreferencesRepository.setMacCready(section.macCready)
        levoVarioPreferencesRepository.setMacCreadyRisk(section.macCreadyRisk)
        levoVarioPreferencesRepository.setAutoMcEnabled(section.autoMcEnabled)
        levoVarioPreferencesRepository.setTeCompensationEnabled(section.teCompensationEnabled)
        levoVarioPreferencesRepository.setShowWindSpeedOnVario(section.showWindSpeedOnVario)
        levoVarioPreferencesRepository.setShowHawkCard(section.showHawkCard)
        levoVarioPreferencesRepository.setEnableHawkUi(section.enableHawkUi)
        levoVarioPreferencesRepository.updateAudioSettings { existing ->
            existing.copy(
                enabled = section.audioEnabled,
                volume = section.audioVolume,
                liftThreshold = section.audioLiftThreshold,
                sinkSilenceThreshold = section.audioSinkSilenceThreshold,
                dutyCycle = section.audioDutyCycle,
                deadbandMin = section.audioDeadbandMin,
                deadbandMax = section.audioDeadbandMax
            )
        }
        levoVarioPreferencesRepository.setHawkNeedleOmegaMinHz(section.hawkNeedleOmegaMinHz)
        levoVarioPreferencesRepository.setHawkNeedleOmegaMaxHz(section.hawkNeedleOmegaMaxHz)
        levoVarioPreferencesRepository.setHawkNeedleTargetTauSec(section.hawkNeedleTargetTauSec)
        levoVarioPreferencesRepository.setHawkNeedleDriftTauMinSec(section.hawkNeedleDriftTauMinSec)
        levoVarioPreferencesRepository.setHawkNeedleDriftTauMaxSec(section.hawkNeedleDriftTauMaxSec)
    }

    private suspend fun applyThermallingPreferences(section: ThermallingModeSectionSnapshot) {
        thermallingModePreferencesRepository.setEnabled(section.enabled)
        thermallingModePreferencesRepository.setSwitchToThermalMode(section.switchToThermalMode)
        thermallingModePreferencesRepository.setZoomOnlyFallbackWhenThermalHidden(
            section.zoomOnlyFallbackWhenThermalHidden
        )
        thermallingModePreferencesRepository.setEnterDelaySeconds(section.enterDelaySeconds)
        thermallingModePreferencesRepository.setExitDelaySeconds(section.exitDelaySeconds)
        thermallingModePreferencesRepository.setApplyZoomOnEnter(section.applyZoomOnEnter)
        thermallingModePreferencesRepository.setThermalZoomLevel(section.thermalZoomLevel)
        thermallingModePreferencesRepository.setRememberManualThermalZoomInSession(
            section.rememberManualThermalZoomInSession
        )
        thermallingModePreferencesRepository.setRestorePreviousModeOnExit(
            section.restorePreviousModeOnExit
        )
        thermallingModePreferencesRepository.setRestorePreviousZoomOnExit(
            section.restorePreviousZoomOnExit
        )
    }

    private suspend fun applyOgnTrafficPreferences(section: OgnTrafficSectionSnapshot) {
        ognTrafficPreferencesRepository.setEnabled(section.enabled)
        ognTrafficPreferencesRepository.setIconSizePx(section.iconSizePx)
        ognTrafficPreferencesRepository.setReceiveRadiusKm(section.receiveRadiusKm)
        ognTrafficPreferencesRepository.setAutoReceiveRadiusEnabled(section.autoReceiveRadiusEnabled)
        ognTrafficPreferencesRepository.setDisplayUpdateMode(
            OgnDisplayUpdateMode.fromStorage(section.displayUpdateMode)
        )
        ognTrafficPreferencesRepository.setShowSciaEnabled(section.showSciaEnabled)
        ognTrafficPreferencesRepository.setShowThermalsEnabled(section.showThermalsEnabled)
        ognTrafficPreferencesRepository.setThermalRetentionHours(section.thermalRetentionHours)
        ognTrafficPreferencesRepository.setHotspotsDisplayPercent(section.hotspotsDisplayPercent)
        ognTrafficPreferencesRepository.setTargetSelection(
            enabled = section.targetEnabled,
            aircraftKey = section.targetAircraftKey
        )
        ognTrafficPreferencesRepository.setOwnFlarmHex(section.ownFlarmHex)
        ognTrafficPreferencesRepository.setOwnIcaoHex(section.ownIcaoHex)
        section.clientCallsign?.let { ognTrafficPreferencesRepository.setClientCallsign(it) }
    }

    private suspend fun applyAdsbTrafficPreferences(section: AdsbTrafficSectionSnapshot) {
        adsbTrafficPreferencesRepository.setEnabled(section.enabled)
        adsbTrafficPreferencesRepository.setIconSizePx(section.iconSizePx)
        adsbTrafficPreferencesRepository.setMaxDistanceKm(section.maxDistanceKm)
        adsbTrafficPreferencesRepository.setVerticalAboveMeters(section.verticalAboveMeters)
        adsbTrafficPreferencesRepository.setVerticalBelowMeters(section.verticalBelowMeters)
        adsbTrafficPreferencesRepository.setEmergencyFlashEnabled(section.emergencyFlashEnabled)
        adsbTrafficPreferencesRepository.setEmergencyAudioEnabled(section.emergencyAudioEnabled)
        adsbTrafficPreferencesRepository.setEmergencyAudioCooldownMs(section.emergencyAudioCooldownMs)
        adsbTrafficPreferencesRepository.setEmergencyAudioMasterEnabled(
            section.emergencyAudioMasterEnabled
        )
        adsbTrafficPreferencesRepository.setEmergencyAudioShadowMode(section.emergencyAudioShadowMode)
        if (section.emergencyAudioRollbackLatched) {
            adsbTrafficPreferencesRepository.latchEmergencyAudioRollback(
                section.emergencyAudioRollbackReason ?: "imported"
            )
        } else {
            adsbTrafficPreferencesRepository.clearEmergencyAudioRollback()
        }
    }

    private suspend fun applyWeatherOverlayPreferences(section: WeatherOverlaySectionSnapshot) {
        weatherOverlayPreferencesRepository.setEnabled(section.enabled)
        weatherOverlayPreferencesRepository.setOpacity(section.opacity)
        weatherOverlayPreferencesRepository.setAnimatePastWindow(section.animatePastWindow)
        weatherOverlayPreferencesRepository.setAnimationWindow(
            WeatherRainAnimationWindow.fromStorage(section.animationWindow)
        )
        weatherOverlayPreferencesRepository.setAnimationSpeed(
            WeatherRainAnimationSpeed.fromStorage(section.animationSpeed)
        )
        weatherOverlayPreferencesRepository.setTransitionQuality(
            WeatherRainTransitionQuality.fromStorage(section.transitionQuality)
        )
        weatherOverlayPreferencesRepository.setFrameMode(
            WeatherRadarFrameMode.fromStorage(section.frameMode)
        )
        weatherOverlayPreferencesRepository.setManualFrameIndex(section.manualFrameIndex)
        weatherOverlayPreferencesRepository.setSmoothEnabled(section.smooth)
        weatherOverlayPreferencesRepository.setSnowEnabled(section.snow)
    }

    private suspend fun applyForecastPreferences(section: ForecastSectionSnapshot) {
        forecastPreferencesRepository.setOverlayEnabled(section.overlayEnabled)
        forecastPreferencesRepository.setOpacity(section.opacity)
        forecastPreferencesRepository.setWindOverlayScale(section.windOverlayScale)
        forecastPreferencesRepository.setWindOverlayEnabled(section.windOverlayEnabled)
        forecastPreferencesRepository.setWindDisplayMode(
            ForecastWindDisplayMode.fromStorageValue(section.windDisplayMode)
        )
        forecastPreferencesRepository.setSkySightSatelliteOverlayEnabled(
            section.skySightSatelliteOverlayEnabled
        )
        forecastPreferencesRepository.setSkySightSatelliteImageryEnabled(
            section.skySightSatelliteImageryEnabled
        )
        forecastPreferencesRepository.setSkySightSatelliteRadarEnabled(
            section.skySightSatelliteRadarEnabled
        )
        forecastPreferencesRepository.setSkySightSatelliteLightningEnabled(
            section.skySightSatelliteLightningEnabled
        )
        forecastPreferencesRepository.setSkySightSatelliteAnimateEnabled(
            section.skySightSatelliteAnimateEnabled
        )
        forecastPreferencesRepository.setSkySightSatelliteHistoryFrames(
            section.skySightSatelliteHistoryFrames
        )
        forecastPreferencesRepository.setSelectedPrimaryParameterId(
            ForecastParameterId(section.selectedPrimaryParameterId)
        )
        forecastPreferencesRepository.setSelectedWindParameterId(
            ForecastParameterId(section.selectedWindParameterId)
        )
        forecastPreferencesRepository.setSelectedTimeUtcMs(section.selectedTimeUtcMs)
        forecastPreferencesRepository.setSelectedRegion(section.selectedRegion)
        forecastPreferencesRepository.setFollowTimeOffsetMinutes(section.followTimeOffsetMinutes)
        forecastPreferencesRepository.setAutoTimeEnabled(section.autoTimeEnabled)
    }

    private fun resolveImportedProfileId(
        sourceProfileId: String,
        importedProfileIdMap: Map<String, String>
    ): String? {
        val canonicalSource = ProfileIdResolver.canonicalOrDefault(sourceProfileId)
        return importedProfileIdMap[sourceProfileId]
            ?: importedProfileIdMap[canonicalSource]
    }

    private fun parseOrientationMode(raw: String): MapOrientationMode =
        runCatching { MapOrientationMode.valueOf(raw) }
            .getOrDefault(MapOrientationMode.TRACK_UP)

    private fun parseMapShiftBiasMode(raw: String): MapShiftBiasMode =
        runCatching { MapShiftBiasMode.valueOf(raw) }
            .getOrDefault(MapShiftBiasMode.NONE)
}
