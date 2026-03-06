package com.example.xcpro.map.ui

internal fun isTargetableOgnAircraftTypeCode(aircraftTypeCode: Int?): Boolean =
    aircraftTypeCode == OGN_GLIDER_AIRCRAFT_TYPE_CODE ||
        aircraftTypeCode == OGN_PARAGLIDER_AIRCRAFT_TYPE_CODE ||
        aircraftTypeCode == OGN_HANG_GLIDER_AIRCRAFT_TYPE_CODE

private const val OGN_GLIDER_AIRCRAFT_TYPE_CODE = 1
private const val OGN_PARAGLIDER_AIRCRAFT_TYPE_CODE = 4
private const val OGN_HANG_GLIDER_AIRCRAFT_TYPE_CODE = 5
