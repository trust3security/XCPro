package com.trust3.xcpro.map

import com.trust3.xcpro.common.geo.GeoPoint
import com.trust3.xcpro.sensors.GPSData
import org.maplibre.android.geometry.LatLng

internal fun GeoPoint.toLatLng(): LatLng = LatLng(latitude, longitude)

internal fun LatLng.toGeoPoint(): GeoPoint = GeoPoint(latitude, longitude)

internal fun GPSData.toLatLng(): LatLng = position.toLatLng()
