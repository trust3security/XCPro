package com.example.xcpro.adsb

interface OpenSkyConfiguredCredentialsProvider {
    fun loadConfiguredCredentials(): OpenSkyClientCredentials?
}
