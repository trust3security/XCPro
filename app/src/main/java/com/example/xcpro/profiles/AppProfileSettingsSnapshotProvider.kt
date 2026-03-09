package com.example.xcpro.profiles

import com.example.dfcards.CardPreferences
import com.example.dfcards.FlightModeSelection
import com.example.xcpro.adsb.AdsbTrafficPreferencesRepository
import com.example.xcpro.core.common.geometry.OffsetPx
import com.example.xcpro.flightdata.FlightMgmtPreferencesRepository
import com.example.xcpro.forecast.ForecastPreferencesRepository
import com.example.xcpro.glider.GliderRepository
import com.example.xcpro.common.units.UnitsRepository
import com.example.xcpro.map.widgets.MapWidgetId
import com.example.xcpro.map.widgets.MapWidgetLayoutRepository
import com.example.xcpro.ogn.OgnTrailSelectionPreferencesRepository
import com.example.xcpro.ogn.OgnTrafficPreferencesRepository
import com.example.xcpro.screens.navdrawer.lookandfeel.LookAndFeelPreferences
import com.example.xcpro.thermalling.ThermallingModePreferencesRepository
import com.example.xcpro.ui.theme.AppColorTheme
import com.example.xcpro.ui.theme.ThemePreferencesRepository
import com.example.xcpro.vario.LevoVarioPreferencesRepository
import com.example.xcpro.variometer.layout.VariometerWidgetRepository
import com.example.xcpro.weather.rain.WeatherOverlayPreferencesRepository
import com.example.xcpro.weather.wind.data.WindOverrideRepository
import com.google.gson.Gson
import com.google.gson.JsonElement
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

@Singleton
class AppProfileSettingsSnapshotProvider @Inject constructor(
    private val cardPreferences: CardPreferences,
    private val flightMgmtPreferencesRepository: FlightMgmtPreferencesRepository,
    private val lookAndFeelPreferences: LookAndFeelPreferences,
    private val themePreferencesRepository: ThemePreferencesRepository,
    private val mapWidgetLayoutRepository: MapWidgetLayoutRepository,
    private val variometerWidgetRepository: VariometerWidgetRepository,
    private val gliderRepository: GliderRepository,
    private val unitsRepository: UnitsRepository,
    private val levoVarioPreferencesRepository: LevoVarioPreferencesRepository,
    private val thermallingModePreferencesRepository: ThermallingModePreferencesRepository,
    private val ognTrafficPreferencesRepository: OgnTrafficPreferencesRepository,
    private val ognTrailSelectionPreferencesRepository: OgnTrailSelectionPreferencesRepository,
    private val adsbTrafficPreferencesRepository: AdsbTrafficPreferencesRepository,
    private val weatherOverlayPreferencesRepository: WeatherOverlayPreferencesRepository,
    private val forecastPreferencesRepository: ForecastPreferencesRepository,
    private val windOverrideRepository: WindOverrideRepository
) : ProfileSettingsSnapshotProvider {

    private val gson = Gson()

    override suspend fun buildSnapshot(profileIds: Set<String>): ProfileSettingsSnapshot {
        val normalizedProfileIds = profileIds
            .map(ProfileIdResolver::canonicalOrDefault)
            .toSortedSet()

        val sections = linkedMapOf<String, JsonElement>()
        sections[ProfileSettingsSectionIds.CARD_PREFERENCES] = gson.toJsonTree(
            captureCardPreferencesSection(normalizedProfileIds)
        )
        sections[ProfileSettingsSectionIds.FLIGHT_MGMT_PREFERENCES] = gson.toJsonTree(
            captureFlightMgmtSection(normalizedProfileIds)
        )
        sections[ProfileSettingsSectionIds.LOOK_AND_FEEL_PREFERENCES] = gson.toJsonTree(
            captureLookAndFeelSection(normalizedProfileIds)
        )
        sections[ProfileSettingsSectionIds.THEME_PREFERENCES] = gson.toJsonTree(
            captureThemeSection(normalizedProfileIds)
        )
        sections[ProfileSettingsSectionIds.MAP_WIDGET_LAYOUT] = gson.toJsonTree(
            captureMapWidgetLayoutSection(normalizedProfileIds)
        )
        sections[ProfileSettingsSectionIds.VARIOMETER_WIDGET_LAYOUT] = gson.toJsonTree(
            captureVariometerWidgetLayoutSection(normalizedProfileIds)
        )
        sections[ProfileSettingsSectionIds.GLIDER_CONFIG] = gson.toJsonTree(
            captureGliderSection(normalizedProfileIds)
        )
        sections[ProfileSettingsSectionIds.UNITS_PREFERENCES] = gson.toJsonTree(
            captureUnitsSection(normalizedProfileIds)
        )
        sections[ProfileSettingsSectionIds.LEVO_VARIO_PREFERENCES] = gson.toJsonTree(
            captureLevoVarioSection()
        )
        sections[ProfileSettingsSectionIds.THERMALLING_MODE_PREFERENCES] = gson.toJsonTree(
            captureThermallingSection()
        )
        sections[ProfileSettingsSectionIds.OGN_TRAFFIC_PREFERENCES] = gson.toJsonTree(
            captureOgnTrafficSection()
        )
        sections[ProfileSettingsSectionIds.OGN_TRAIL_SELECTION_PREFERENCES] = gson.toJsonTree(
            captureOgnTrailSelectionSection()
        )
        sections[ProfileSettingsSectionIds.ADSB_TRAFFIC_PREFERENCES] = gson.toJsonTree(
            captureAdsbTrafficSection()
        )
        sections[ProfileSettingsSectionIds.WEATHER_OVERLAY_PREFERENCES] = gson.toJsonTree(
            captureWeatherOverlaySection()
        )
        sections[ProfileSettingsSectionIds.FORECAST_PREFERENCES] = gson.toJsonTree(
            captureForecastSection()
        )
        sections[ProfileSettingsSectionIds.WIND_OVERRIDE_PREFERENCES] = gson.toJsonTree(
            captureWindOverrideSection()
        )
        return ProfileSettingsSnapshot(sections = sections.toMap())
    }

    private suspend fun captureCardPreferencesSection(
        profileIds: Set<String>
    ): CardPreferencesSectionSnapshot {
        val templates = cardPreferences.getAllTemplates().first().map { template ->
            CardTemplateSnapshot(
                id = template.id,
                name = template.name,
                description = template.description,
                cardIds = template.cardIds.toList(),
                isPreset = template.isPreset,
                createdAt = template.createdAt
            )
        }
        val templateCardsByProfile = normalizeProfileScopedMap(
            raw = cardPreferences.getAllProfileTemplateCards().first()
        )
        val modeTemplatesByProfile = normalizeProfileScopedMap(
            raw = cardPreferences.getAllProfileFlightModeTemplates().first()
        )

        val profileTemplateCards = profileIds.associateWith { profileId ->
            templateCardsByProfile[profileId].orEmpty()
        }
        val profileFlightModeTemplates = profileIds.associateWith { profileId ->
            modeTemplatesByProfile[profileId].orEmpty()
        }

        val profileFlightModeVisibilities = linkedMapOf<String, Map<String, Boolean>>()
        val profileCardPositions =
            linkedMapOf<String, Map<String, Map<String, CardPositionSnapshot>>>()
        profileIds.forEach { profileId ->
            val visibilities = cardPreferences.getProfileAllFlightModeVisibilities(profileId).first()
            profileFlightModeVisibilities[profileId] = visibilities.toMap()

            val modePositions = linkedMapOf<String, Map<String, CardPositionSnapshot>>()
            FlightModeSelection.values().forEach { mode ->
                val positions = cardPreferences.getProfileCardPositions(profileId, mode.name).first()
                modePositions[mode.name] = positions.mapValues { (_, position) ->
                    CardPositionSnapshot(
                        x = position.x,
                        y = position.y,
                        width = position.width,
                        height = position.height
                    )
                }
            }
            profileCardPositions[profileId] = modePositions.toMap()
        }

        return CardPreferencesSectionSnapshot(
            templates = templates,
            profileTemplateCards = profileTemplateCards.toMap(),
            profileFlightModeTemplates = profileFlightModeTemplates.toMap(),
            profileFlightModeVisibilities = profileFlightModeVisibilities.toMap(),
            profileCardPositions = profileCardPositions.toMap(),
            cardsAcrossPortrait = cardPreferences.getCardsAcrossPortrait().first(),
            cardsAnchorPortrait = cardPreferences.getCardsAnchorPortrait().first().name,
            lastActiveTemplate = cardPreferences.getLastActiveTemplate().first(),
            varioSmoothingAlpha = cardPreferences.getVarioSmoothingAlpha().first()
        )
    }

    private suspend fun captureFlightMgmtSection(profileIds: Set<String>): FlightMgmtSectionSnapshot {
        val profileLastModes = profileIds.associateWith { profileId ->
            flightMgmtPreferencesRepository.getLastFlightMode(profileId).name
        }
        return FlightMgmtSectionSnapshot(
            lastActiveTab = flightMgmtPreferencesRepository.getLastActiveTab(),
            profileLastFlightModes = profileLastModes
        )
    }

    private fun captureLookAndFeelSection(profileIds: Set<String>): LookAndFeelSectionSnapshot {
        val statusBarStyleByProfile = profileIds.associateWith { profileId ->
            lookAndFeelPreferences.getStatusBarStyleId(profileId)
        }
        val cardStyleByProfile = profileIds.associateWith { profileId ->
            lookAndFeelPreferences.getCardStyleId(profileId)
        }
        val colorThemeByProfile = profileIds.associateWith { profileId ->
            lookAndFeelPreferences.getColorThemeId(profileId)
        }
        return LookAndFeelSectionSnapshot(
            statusBarStyleByProfile = statusBarStyleByProfile,
            cardStyleByProfile = cardStyleByProfile,
            colorThemeByProfile = colorThemeByProfile
        )
    }

    private fun captureThemeSection(profileIds: Set<String>): ThemeSectionSnapshot {
        val themeIdsByProfile = linkedMapOf<String, String>()
        val customColorsByProfileAndTheme = linkedMapOf<String, Map<String, String>>()
        profileIds.forEach { profileId ->
            val selectedThemeId = themePreferencesRepository.getThemeId(profileId)
            themeIdsByProfile[profileId] = selectedThemeId

            val candidateThemeIds = linkedSetOf<String>().apply {
                add(selectedThemeId)
                AppColorTheme.entries.forEach { theme -> add(theme.id) }
            }
            val customColorsByTheme = linkedMapOf<String, String>()
            candidateThemeIds.forEach { themeId ->
                val customJson = themePreferencesRepository.getCustomColorsJson(profileId, themeId)
                if (!customJson.isNullOrBlank()) {
                    customColorsByTheme[themeId] = customJson
                }
            }
            customColorsByProfileAndTheme[profileId] = customColorsByTheme.toMap()
        }
        return ThemeSectionSnapshot(
            themeIdByProfile = themeIdsByProfile.toMap(),
            customColorsByProfileAndTheme = customColorsByProfileAndTheme.toMap()
        )
    }

    private fun captureMapWidgetLayoutSection(profileIds: Set<String>): MapWidgetLayoutSectionSnapshot {
        val widgetsByProfile = profileIds.associateWith { profileId ->
            MapWidgetId.entries.associate { widgetId ->
                widgetId.name to MapWidgetPlacementSnapshot(
                    offset = mapWidgetLayoutRepository.readOffset(profileId, widgetId)?.toSnapshotOffset(),
                    sizePx = mapWidgetLayoutRepository.readSizePx(profileId, widgetId)
                )
            }
        }
        return MapWidgetLayoutSectionSnapshot(widgetsByProfile = widgetsByProfile)
    }

    private fun captureVariometerWidgetLayoutSection(
        profileIds: Set<String>
    ): VariometerWidgetLayoutSectionSnapshot {
        val layoutsByProfile = profileIds.associateWith { profileId ->
            val layout = variometerWidgetRepository.load(
                profileId = profileId,
                defaultOffset = OffsetPx.Zero,
                defaultSizePx = 0f
            )
            VariometerLayoutProfileSnapshot(
                offset = layout.offset.toSnapshotOffset(),
                sizePx = layout.sizePx,
                hasPersistedOffset = layout.hasPersistedOffset,
                hasPersistedSize = layout.hasPersistedSize
            )
        }
        return VariometerWidgetLayoutSectionSnapshot(layoutsByProfile = layoutsByProfile)
    }

    private suspend fun captureGliderSection(profileIds: Set<String>): GliderSectionSnapshot {
        val profiles = profileIds.associateWith { profileId ->
            val snapshot = gliderRepository.loadProfileSnapshot(profileId)
            GliderProfileSectionSnapshot(
                selectedModelId = snapshot.selectedModelId,
                effectiveModelId = snapshot.effectiveModelId,
                isFallbackPolarActive = snapshot.isFallbackPolarActive,
                config = snapshot.config
            )
        }
        return GliderSectionSnapshot(profiles = profiles)
    }

    private suspend fun captureUnitsSection(profileIds: Set<String>): UnitsSectionSnapshot {
        val unitsByProfile = profileIds.associateWith { profileId ->
            unitsRepository.readProfileUnits(profileId)
        }
        return UnitsSectionSnapshot(unitsByProfile = unitsByProfile)
    }

    private suspend fun captureLevoVarioSection(): LevoVarioSectionSnapshot {
        val config = levoVarioPreferencesRepository.config.first()
        return LevoVarioSectionSnapshot(
            macCready = config.macCready,
            macCreadyRisk = config.macCreadyRisk,
            autoMcEnabled = config.autoMcEnabled,
            teCompensationEnabled = config.teCompensationEnabled,
            showWindSpeedOnVario = config.showWindSpeedOnVario,
            showHawkCard = config.showHawkCard,
            enableHawkUi = config.enableHawkUi,
            audioEnabled = config.audioSettings.enabled,
            audioVolume = config.audioSettings.volume,
            audioLiftThreshold = config.audioSettings.liftThreshold,
            audioSinkSilenceThreshold = config.audioSettings.sinkSilenceThreshold,
            audioDutyCycle = config.audioSettings.dutyCycle,
            audioDeadbandMin = config.audioSettings.deadbandMin,
            audioDeadbandMax = config.audioSettings.deadbandMax,
            hawkNeedleOmegaMinHz = config.hawkNeedleOmegaMinHz,
            hawkNeedleOmegaMaxHz = config.hawkNeedleOmegaMaxHz,
            hawkNeedleTargetTauSec = config.hawkNeedleTargetTauSec,
            hawkNeedleDriftTauMinSec = config.hawkNeedleDriftTauMinSec,
            hawkNeedleDriftTauMaxSec = config.hawkNeedleDriftTauMaxSec
        )
    }

    private suspend fun captureThermallingSection(): ThermallingModeSectionSnapshot {
        val settings = thermallingModePreferencesRepository.settingsFlow.first()
        return ThermallingModeSectionSnapshot(
            enabled = settings.enabled,
            switchToThermalMode = settings.switchToThermalMode,
            zoomOnlyFallbackWhenThermalHidden = settings.zoomOnlyFallbackWhenThermalHidden,
            enterDelaySeconds = settings.enterDelaySeconds,
            exitDelaySeconds = settings.exitDelaySeconds,
            applyZoomOnEnter = settings.applyZoomOnEnter,
            thermalZoomLevel = settings.thermalZoomLevel,
            rememberManualThermalZoomInSession = settings.rememberManualThermalZoomInSession,
            restorePreviousModeOnExit = settings.restorePreviousModeOnExit,
            restorePreviousZoomOnExit = settings.restorePreviousZoomOnExit
        )
    }

    private suspend fun captureOgnTrafficSection(): OgnTrafficSectionSnapshot {
        return OgnTrafficSectionSnapshot(
            enabled = ognTrafficPreferencesRepository.enabledFlow.first(),
            iconSizePx = ognTrafficPreferencesRepository.iconSizePxFlow.first(),
            receiveRadiusKm = ognTrafficPreferencesRepository.receiveRadiusKmFlow.first(),
            autoReceiveRadiusEnabled = ognTrafficPreferencesRepository.autoReceiveRadiusEnabledFlow.first(),
            displayUpdateMode = ognTrafficPreferencesRepository.displayUpdateModeFlow.first().storageValue,
            showSciaEnabled = ognTrafficPreferencesRepository.showSciaEnabledFlow.first(),
            showThermalsEnabled = ognTrafficPreferencesRepository.showThermalsEnabledFlow.first(),
            thermalRetentionHours = ognTrafficPreferencesRepository.thermalRetentionHoursFlow.first(),
            hotspotsDisplayPercent = ognTrafficPreferencesRepository.hotspotsDisplayPercentFlow.first(),
            targetEnabled = ognTrafficPreferencesRepository.targetEnabledFlow.first(),
            targetAircraftKey = ognTrafficPreferencesRepository.targetAircraftKeyFlow.first(),
            ownFlarmHex = ognTrafficPreferencesRepository.ownFlarmHexFlow.first(),
            ownIcaoHex = ognTrafficPreferencesRepository.ownIcaoHexFlow.first(),
            clientCallsign = ognTrafficPreferencesRepository.clientCallsignFlow.first()
        )
    }

    private suspend fun captureOgnTrailSelectionSection(): OgnTrailSelectionSectionSnapshot {
        val selectedKeys = ognTrailSelectionPreferencesRepository.selectedAircraftKeysFlow.first()
        return OgnTrailSelectionSectionSnapshot(selectedAircraftKeys = selectedKeys.toSortedSet())
    }

    private suspend fun captureAdsbTrafficSection(): AdsbTrafficSectionSnapshot {
        return AdsbTrafficSectionSnapshot(
            enabled = adsbTrafficPreferencesRepository.enabledFlow.first(),
            iconSizePx = adsbTrafficPreferencesRepository.iconSizePxFlow.first(),
            maxDistanceKm = adsbTrafficPreferencesRepository.maxDistanceKmFlow.first(),
            verticalAboveMeters = adsbTrafficPreferencesRepository.verticalAboveMetersFlow.first(),
            verticalBelowMeters = adsbTrafficPreferencesRepository.verticalBelowMetersFlow.first(),
            emergencyFlashEnabled = adsbTrafficPreferencesRepository.emergencyFlashEnabledFlow.first(),
            emergencyAudioEnabled = adsbTrafficPreferencesRepository.emergencyAudioEnabledFlow.first(),
            emergencyAudioCooldownMs = adsbTrafficPreferencesRepository.emergencyAudioCooldownMsFlow.first(),
            emergencyAudioMasterEnabled = adsbTrafficPreferencesRepository.emergencyAudioMasterEnabledFlow.first(),
            emergencyAudioShadowMode = adsbTrafficPreferencesRepository.emergencyAudioShadowModeFlow.first(),
            emergencyAudioCohortPercent = adsbTrafficPreferencesRepository.emergencyAudioCohortPercentFlow.first(),
            emergencyAudioCohortBucket = adsbTrafficPreferencesRepository.emergencyAudioCohortBucketFlow.first(),
            emergencyAudioRollbackLatched = adsbTrafficPreferencesRepository.emergencyAudioRollbackLatchedFlow.first(),
            emergencyAudioRollbackReason = adsbTrafficPreferencesRepository.emergencyAudioRollbackReasonFlow.first()
        )
    }

    private suspend fun captureWeatherOverlaySection(): WeatherOverlaySectionSnapshot {
        val settings = weatherOverlayPreferencesRepository.preferencesFlow.first()
        return WeatherOverlaySectionSnapshot(
            enabled = settings.enabled,
            opacity = settings.opacity,
            animatePastWindow = settings.animatePastWindow,
            animationWindow = settings.animationWindow.storageKey,
            animationSpeed = settings.animationSpeed.storageKey,
            transitionQuality = settings.transitionQuality.storageKey,
            frameMode = settings.frameMode.storageKey,
            manualFrameIndex = settings.manualFrameIndex,
            smooth = settings.renderOptions.smooth,
            snow = settings.renderOptions.snow
        )
    }

    private suspend fun captureForecastSection(): ForecastSectionSnapshot {
        val settings = forecastPreferencesRepository.preferencesFlow.first()
        return ForecastSectionSnapshot(
            overlayEnabled = settings.overlayEnabled,
            opacity = settings.opacity,
            windOverlayScale = settings.windOverlayScale,
            windOverlayEnabled = settings.windOverlayEnabled,
            windDisplayMode = settings.windDisplayMode.storageValue,
            skySightSatelliteOverlayEnabled = settings.skySightSatelliteOverlayEnabled,
            skySightSatelliteImageryEnabled = settings.skySightSatelliteImageryEnabled,
            skySightSatelliteRadarEnabled = settings.skySightSatelliteRadarEnabled,
            skySightSatelliteLightningEnabled = settings.skySightSatelliteLightningEnabled,
            skySightSatelliteAnimateEnabled = settings.skySightSatelliteAnimateEnabled,
            skySightSatelliteHistoryFrames = settings.skySightSatelliteHistoryFrames,
            selectedPrimaryParameterId = settings.selectedPrimaryParameterId.value,
            selectedWindParameterId = settings.selectedWindParameterId.value,
            selectedTimeUtcMs = settings.selectedTimeUtcMs,
            selectedRegion = settings.selectedRegion,
            followTimeOffsetMinutes = settings.followTimeOffsetMinutes,
            autoTimeEnabled = settings.autoTimeEnabled
        )
    }

    private suspend fun captureWindOverrideSection(): WindOverrideSectionSnapshot {
        val manualOverride = windOverrideRepository.manualWind.first()
        return WindOverrideSectionSnapshot(
            manualOverride = manualOverride?.let { wind ->
                ManualWindOverrideSnapshot(
                    speedMs = wind.vector.speed,
                    directionFromDeg = wind.vector.directionFromDeg,
                    timestampMillis = wind.timestampMillis,
                    source = wind.source.name
                )
            }
        )
    }

    private fun OffsetPx.toSnapshotOffset(): OffsetSnapshot = OffsetSnapshot(x = x, y = y)

    private fun <T> normalizeProfileScopedMap(raw: Map<String, T>): Map<String, T> {
        if (raw.isEmpty()) return emptyMap()
        val normalized = linkedMapOf<String, T>()
        raw.entries.sortedBy { it.key }.forEach { (profileId, value) ->
            val resolvedId = ProfileIdResolver.normalizeOrNull(profileId) ?: return@forEach
            if (!normalized.containsKey(resolvedId)) {
                normalized[resolvedId] = value
            }
        }
        return normalized.toMap()
    }
}
