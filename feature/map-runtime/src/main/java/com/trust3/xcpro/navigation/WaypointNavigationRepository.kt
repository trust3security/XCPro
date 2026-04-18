package com.trust3.xcpro.navigation

import com.trust3.xcpro.flightdata.FlightDataRepository
import com.trust3.xcpro.sensors.CompleteFlightData
import com.trust3.xcpro.tasks.navigation.NavigationRouteInvalidReason
import com.trust3.xcpro.tasks.navigation.NavigationRouteRepository
import com.trust3.xcpro.tasks.navigation.NavigationRouteSnapshot
import com.trust3.xcpro.tasks.racing.RacingGeometryUtils
import dagger.hilt.android.scopes.ViewModelScoped
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlin.math.roundToLong

@ViewModelScoped
class WaypointNavigationRepository internal constructor(
    flightDataFlow: Flow<CompleteFlightData?>,
    routeFlow: Flow<NavigationRouteSnapshot>
) {
    @Inject
    constructor(
        flightDataRepository: FlightDataRepository,
        navigationRouteRepository: NavigationRouteRepository
    ) : this(
        flightDataFlow = flightDataRepository.flightData,
        routeFlow = navigationRouteRepository.route
    )

    val waypointNavigation: Flow<WaypointNavigationSnapshot> = combine(
        flightDataFlow,
        routeFlow,
        ::resolveWaypointNavigation
    ).distinctUntilChanged()

    private fun resolveWaypointNavigation(
        completeData: CompleteFlightData?,
        route: NavigationRouteSnapshot
    ): WaypointNavigationSnapshot {
        if (!route.valid) {
            val reason = route.invalidReason.toWaypointInvalidReason()
            return WaypointNavigationSnapshot(
                invalidReason = reason,
                etaInvalidReason = reason
            )
        }

        val target = route.remainingWaypoints.firstOrNull()
            ?: return invalidSnapshot(WaypointNavigationInvalidReason.INVALID_ROUTE)
        val gps = completeData?.gps
            ?: return invalidSnapshot(WaypointNavigationInvalidReason.NO_POSITION)

        val latitude = gps.position.latitude
        val longitude = gps.position.longitude
        if (!latitude.isFinite() || !longitude.isFinite()) {
            return invalidSnapshot(WaypointNavigationInvalidReason.NO_POSITION)
        }

        val distanceMeters = RacingGeometryUtils.haversineDistanceMeters(
            latitude,
            longitude,
            target.lat,
            target.lon
        )
        val bearingTrueDegrees = RacingGeometryUtils.calculateBearing(
            latitude,
            longitude,
            target.lat,
            target.lon
        )

        val base = WaypointNavigationSnapshot(
            targetLabel = target.label,
            distanceMeters = distanceMeters,
            bearingTrueDegrees = bearingTrueDegrees,
            valid = true,
            invalidReason = WaypointNavigationInvalidReason.NONE,
            etaInvalidReason = WaypointNavigationInvalidReason.NONE
        )

        val etaGroundSpeedMs = gps.speed.value.takeIf { it.isFinite() && it > ETA_MIN_GROUND_SPEED_MS }
            ?: return base.copy(etaInvalidReason = WaypointNavigationInvalidReason.STATIC)

        val sampleWallTimeMillis = completeData.timestamp.takeIf { it > 0L }
            ?: gps.timestamp.takeIf { it > 0L }
            ?: return base.copy(etaInvalidReason = WaypointNavigationInvalidReason.NO_TIME)

        val etaEpochMillis = sampleWallTimeMillis + ((distanceMeters / etaGroundSpeedMs) * 1_000.0).roundToLong()
        return base.copy(
            etaEpochMillis = etaEpochMillis,
            etaValid = true,
            etaSource = WaypointEtaSource.GROUND_SPEED,
            etaInvalidReason = WaypointNavigationInvalidReason.NONE
        )
    }

    private fun invalidSnapshot(reason: WaypointNavigationInvalidReason): WaypointNavigationSnapshot =
        WaypointNavigationSnapshot(
            invalidReason = reason,
            etaInvalidReason = reason
        )

    private fun NavigationRouteInvalidReason.toWaypointInvalidReason(): WaypointNavigationInvalidReason =
        when (this) {
            NavigationRouteInvalidReason.NO_TASK -> WaypointNavigationInvalidReason.NO_TASK
            NavigationRouteInvalidReason.PRESTART -> WaypointNavigationInvalidReason.PRESTART
            NavigationRouteInvalidReason.FINISHED -> WaypointNavigationInvalidReason.FINISHED
            NavigationRouteInvalidReason.INVALID_ROUTE -> WaypointNavigationInvalidReason.INVALID_ROUTE
            NavigationRouteInvalidReason.INVALID -> WaypointNavigationInvalidReason.INVALID
        }

    private companion object {
        const val ETA_MIN_GROUND_SPEED_MS = 2.0
    }
}
