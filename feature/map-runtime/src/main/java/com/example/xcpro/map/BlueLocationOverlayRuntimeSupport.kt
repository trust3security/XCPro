package com.example.xcpro.map

import android.location.Location
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.PropertyFactory.iconAllowOverlap
import org.maplibre.android.style.layers.PropertyFactory.iconAnchor
import org.maplibre.android.style.layers.PropertyFactory.iconIgnorePlacement
import org.maplibre.android.style.layers.PropertyFactory.iconImage
import org.maplibre.android.style.layers.PropertyFactory.iconOffset
import org.maplibre.android.style.layers.PropertyFactory.iconRotate
import org.maplibre.android.style.layers.PropertyFactory.iconRotationAlignment
import org.maplibre.android.style.layers.PropertyFactory.iconSize
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.Point
import kotlin.math.abs

private const val BLUE_LOCATION_COORDINATE_EPSILON = 1e-7
private const val BLUE_LOCATION_ROTATION_EPSILON_DEG = 0.05f

internal fun createBlueLocationLayer(
    layerId: String,
    sourceId: String,
    iconId: String,
    currentIconScale: Float
): SymbolLayer =
    SymbolLayer(layerId, sourceId)
        .withProperties(
            iconImage(iconId),
            iconSize(currentIconScale),
            iconRotate(0f),
            iconAllowOverlap(true),
            iconIgnorePlacement(true),
            iconRotationAlignment("viewport"),
            iconAnchor("center"),
            iconOffset(arrayOf(0f, 0f))
        )

internal fun isValidBlueLocationCoordinate(lat: Double, lon: Double): Boolean {
    if (!lat.isFinite() || !lon.isFinite()) {
        return false
    }
    if (lat < -90.0 || lat > 90.0) {
        return false
    }
    if (lon < -180.0 || lon > 180.0) {
        return false
    }
    return true
}

internal fun normalizeBlueLocationAngle(angle: Double): Double {
    var normalized = angle % 360
    if (normalized > 180) normalized -= 360
    if (normalized < -180) normalized += 360
    return normalized
}

internal fun calculateBlueLocationDistanceMeters(from: LatLng, to: LatLng): Float {
    val results = FloatArray(1)
    Location.distanceBetween(
        from.latitude,
        from.longitude,
        to.latitude,
        to.longitude,
        results
    )
    return results[0]
}

internal fun sameBlueLocation(first: LatLng?, second: LatLng): Boolean {
    val location = first ?: return false
    return abs(location.latitude - second.latitude) < BLUE_LOCATION_COORDINATE_EPSILON &&
        abs(location.longitude - second.longitude) < BLUE_LOCATION_COORDINATE_EPSILON
}

internal fun sameBlueLocationRotation(first: Float?, second: Float): Boolean {
    val rotation = first ?: return false
    return abs(rotation - second) < BLUE_LOCATION_ROTATION_EPSILON_DEG
}

internal fun updateBlueLocationSource(
    source: GeoJsonSource,
    location: LatLng
) {
    source.setGeoJson(
        Feature.fromGeometry(Point.fromLngLat(location.longitude, location.latitude))
    )
}

internal fun ensureBlueLocationRuntimeObjects(
    style: Style,
    iconId: String,
    sourceId: String,
    layerId: String,
    currentIconScale: Float
) {
    val hasIcon = runCatching { style.getImage(iconId) }.getOrNull() != null
    if (!hasIcon) {
        style.addImage(iconId, SailplaneIconBitmapFactory.create(BlueLocationOverlay.ICON_SIZE_PX))
    }
    if (style.getSourceAs<GeoJsonSource>(sourceId) == null) {
        style.addSource(GeoJsonSource(sourceId))
    }
    if (style.getLayerAs<SymbolLayer>(layerId) == null) {
        style.addLayer(createBlueLocationLayer(layerId, sourceId, iconId, currentIconScale))
    }
}

internal fun hasBlueLocationRuntimeObjects(
    style: Style,
    iconId: String,
    sourceId: String,
    layerId: String
): Boolean {
    val hasIcon = runCatching { style.getImage(iconId) }.getOrNull() != null
    return hasIcon &&
        style.getSourceAs<GeoJsonSource>(sourceId) != null &&
        style.getLayerAs<SymbolLayer>(layerId) != null
}
