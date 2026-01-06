package com.example.xcpro.map

import com.example.xcpro.common.geo.GeoPoint
import com.example.xcpro.sensors.GPSData
import org.maplibre.android.geometry.LatLng

internal fun GeoPoint.toLatLng(): LatLng = LatLng(latitude, longitude)

internal fun LatLng.toGeoPoint(): GeoPoint = GeoPoint(latitude, longitude)

internal fun GPSData.toLatLng(): LatLng = position.toLatLng()
