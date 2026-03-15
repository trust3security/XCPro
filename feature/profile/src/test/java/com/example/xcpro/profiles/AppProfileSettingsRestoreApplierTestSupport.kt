package com.example.xcpro.profiles

import com.example.dfcards.CardPreferences
import com.example.dfcards.profiles.CardProfileSettingsContributor
import com.example.xcpro.adsb.AdsbTrafficPreferencesRepository
import com.example.xcpro.adsb.AdsbTrafficProfileSettingsContributor
import com.example.xcpro.common.units.UnitsRepository
import com.example.xcpro.flightdata.FlightMgmtPreferencesRepository
import com.example.xcpro.forecast.ForecastPreferencesRepository
import com.example.xcpro.forecast.ForecastProfileSettingsContributor
import com.example.xcpro.glider.GliderRepository
import com.example.xcpro.MapOrientationSettingsRepository
import com.example.xcpro.map.MapStyleRepository
import com.example.xcpro.map.QnhPreferencesRepository
import com.example.xcpro.map.trail.MapTrailPreferences
import com.example.xcpro.map.widgets.MapWidgetLayoutRepository
import com.example.xcpro.ogn.OgnTrailSelectionPreferencesRepository
import com.example.xcpro.ogn.OgnTrailSelectionProfileSettingsContributor
import com.example.xcpro.ogn.OgnTrafficPreferencesRepository
import com.example.xcpro.ogn.OgnTrafficProfileSettingsContributor
import com.example.xcpro.screens.navdrawer.lookandfeel.LookAndFeelPreferences
import com.example.xcpro.thermalling.ThermallingModePreferencesRepository
import com.example.xcpro.ui.theme.ThemePreferencesRepository
import com.example.xcpro.vario.LevoVarioPreferencesRepository
import com.example.xcpro.variometer.layout.VariometerWidgetProfileSettingsContributor
import com.example.xcpro.variometer.layout.VariometerWidgetRepository
import com.example.xcpro.weather.rain.WeatherOverlayPreferencesRepository
import com.example.xcpro.weather.rain.WeatherOverlayProfileSettingsContributor
import com.example.xcpro.weather.wind.data.WindOverrideRepository
import dagger.Lazy
import org.mockito.kotlin.mock

internal data class AppProfileSettingsRestoreApplierHarness(
    val applier: AppProfileSettingsRestoreApplier,
    val flightMgmtPreferencesRepository: FlightMgmtPreferencesRepository,
    val lookAndFeelPreferences: LookAndFeelPreferences,
    val themePreferencesRepository: ThemePreferencesRepository,
    val mapWidgetLayoutRepository: MapWidgetLayoutRepository,
    val variometerWidgetRepository: VariometerWidgetRepository,
    val gliderRepository: GliderRepository,
    val unitsRepository: UnitsRepository,
    val mapStyleRepository: MapStyleRepository,
    val mapTrailPreferences: MapTrailPreferences,
    val qnhPreferencesRepository: QnhPreferencesRepository,
    val orientationSettingsRepository: MapOrientationSettingsRepository,
    val levoVarioPreferencesRepository: LevoVarioPreferencesRepository,
    val thermallingModePreferencesRepository: ThermallingModePreferencesRepository,
    val windOverrideRepository: WindOverrideRepository
)

internal fun createAppProfileSettingsRestoreApplierHarness(): AppProfileSettingsRestoreApplierHarness {
    val cardPreferences = mock<CardPreferences>()
    val flightMgmtPreferencesRepository = mock<FlightMgmtPreferencesRepository>()
    val lookAndFeelPreferences = mock<LookAndFeelPreferences>()
    val themePreferencesRepository = mock<ThemePreferencesRepository>()
    val mapWidgetLayoutRepository = mock<MapWidgetLayoutRepository>()
    val variometerWidgetRepository = mock<VariometerWidgetRepository>()
    val gliderRepository = mock<GliderRepository>()
    val unitsRepository = mock<UnitsRepository>()
    val mapStyleRepository = mock<MapStyleRepository>()
    val mapTrailPreferences = mock<MapTrailPreferences>()
    val qnhPreferencesRepository = mock<QnhPreferencesRepository>()
    val orientationSettingsRepository = mock<MapOrientationSettingsRepository>()
    val levoVarioPreferencesRepository = mock<LevoVarioPreferencesRepository>()
    val thermallingModePreferencesRepository = mock<ThermallingModePreferencesRepository>()
    val ognTrafficPreferencesRepository = mock<OgnTrafficPreferencesRepository>()
    val ognTrailSelectionPreferencesRepository = mock<OgnTrailSelectionPreferencesRepository>()
    val adsbTrafficPreferencesRepository = mock<AdsbTrafficPreferencesRepository>()
    val weatherOverlayPreferencesRepository = mock<WeatherOverlayPreferencesRepository>()
    val forecastPreferencesRepository = mock<ForecastPreferencesRepository>()
    val windOverrideRepository = mock<WindOverrideRepository>()
    lateinit var contributorRegistry: ProfileSettingsContributorRegistry
    val contributorRegistryLazy = object : Lazy<ProfileSettingsContributorRegistry> {
        override fun get(): ProfileSettingsContributorRegistry = contributorRegistry
    }

    val applier = AppProfileSettingsRestoreApplier(
        contributorRegistry = contributorRegistryLazy
    )
    val cardContributor = CardProfileSettingsContributor(cardPreferences)
    val flightMgmtContributor = FlightMgmtProfileSettingsContributor(
        flightMgmtPreferencesRepository
    )
    val lookAndFeelContributor = LookAndFeelProfileSettingsContributor(
        lookAndFeelPreferences
    )
    val themeContributor = ThemeProfileSettingsContributor(themePreferencesRepository)
    val qnhContributor = QnhProfileSettingsContributor(qnhPreferencesRepository)
    val mapStyleContributor = MapStyleProfileSettingsContributor(mapStyleRepository)
    val snailTrailContributor = SnailTrailProfileSettingsContributor(mapTrailPreferences)
    val mapWidgetLayoutContributor = MapWidgetLayoutProfileSettingsContributor(
        mapWidgetLayoutRepository
    )
    val variometerWidgetContributor = VariometerWidgetProfileSettingsContributor(
        variometerWidgetRepository
    )
    val ognTrafficContributor = OgnTrafficProfileSettingsContributor(
        ognTrafficPreferencesRepository
    )
    val ognTrailSelectionContributor = OgnTrailSelectionProfileSettingsContributor(
        ognTrailSelectionPreferencesRepository
    )
    val adsbTrafficContributor = AdsbTrafficProfileSettingsContributor(
        adsbTrafficPreferencesRepository
    )
    val weatherOverlayContributor = WeatherOverlayProfileSettingsContributor(
        weatherOverlayPreferencesRepository
    )
    val forecastContributor = ForecastProfileSettingsContributor(
        forecastPreferencesRepository
    )
    val unitsContributor = UnitsProfileSettingsContributor(unitsRepository)
    val orientationContributor = OrientationProfileSettingsContributor(
        orientationSettingsRepository
    )
    val gliderContributor = GliderConfigProfileSettingsContributor(gliderRepository)
    val levoVarioContributor = LevoVarioProfileSettingsContributor(
        levoVarioPreferencesRepository
    )
    val thermallingContributor = ThermallingModeProfileSettingsContributor(
        thermallingModePreferencesRepository
    )
    val windOverrideContributor = WindOverrideProfileSettingsContributor(windOverrideRepository)
    contributorRegistry = ProfileSettingsContributorRegistry(
        captureContributors = setOf(
            cardContributor,
            flightMgmtContributor,
            lookAndFeelContributor,
            themeContributor,
            qnhContributor,
            mapStyleContributor,
            snailTrailContributor,
            mapWidgetLayoutContributor,
            variometerWidgetContributor,
            ognTrafficContributor,
            ognTrailSelectionContributor,
            adsbTrafficContributor,
            weatherOverlayContributor,
            forecastContributor,
            unitsContributor,
            orientationContributor,
            gliderContributor,
            levoVarioContributor,
            thermallingContributor,
            windOverrideContributor
        ),
        applyContributors = setOf(
            cardContributor,
            flightMgmtContributor,
            lookAndFeelContributor,
            themeContributor,
            qnhContributor,
            mapStyleContributor,
            snailTrailContributor,
            mapWidgetLayoutContributor,
            variometerWidgetContributor,
            ognTrafficContributor,
            ognTrailSelectionContributor,
            adsbTrafficContributor,
            weatherOverlayContributor,
            forecastContributor,
            unitsContributor,
            orientationContributor,
            gliderContributor,
            levoVarioContributor,
            thermallingContributor,
            windOverrideContributor
        )
    )

    return AppProfileSettingsRestoreApplierHarness(
        applier = applier,
        flightMgmtPreferencesRepository = flightMgmtPreferencesRepository,
        lookAndFeelPreferences = lookAndFeelPreferences,
        themePreferencesRepository = themePreferencesRepository,
        mapWidgetLayoutRepository = mapWidgetLayoutRepository,
        variometerWidgetRepository = variometerWidgetRepository,
        gliderRepository = gliderRepository,
        unitsRepository = unitsRepository,
        mapStyleRepository = mapStyleRepository,
        mapTrailPreferences = mapTrailPreferences,
        qnhPreferencesRepository = qnhPreferencesRepository,
        orientationSettingsRepository = orientationSettingsRepository,
        levoVarioPreferencesRepository = levoVarioPreferencesRepository,
        thermallingModePreferencesRepository = thermallingModePreferencesRepository,
        windOverrideRepository = windOverrideRepository
    )
}
