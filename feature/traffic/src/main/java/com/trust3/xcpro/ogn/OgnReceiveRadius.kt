package com.trust3.xcpro.ogn

const val OGN_RECEIVE_RADIUS_MIN_KM = 20
const val OGN_RECEIVE_RADIUS_DEFAULT_KM = 150
const val OGN_RECEIVE_RADIUS_MAX_KM = 300

fun clampOgnReceiveRadiusKm(radiusKm: Int): Int =
    radiusKm.coerceIn(OGN_RECEIVE_RADIUS_MIN_KM, OGN_RECEIVE_RADIUS_MAX_KM)
