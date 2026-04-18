package com.trust3.xcpro.adsb

interface OpenSkyConfiguredCredentialsProvider {
    fun loadConfiguredCredentials(): OpenSkyClientCredentials?
}
