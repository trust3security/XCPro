package com.example.xcpro.screens.navdrawer

import com.example.xcpro.adsb.AdsbTrafficRepository
import com.example.xcpro.adsb.AdsbTrafficPreferencesRepository
import com.example.xcpro.adsb.OpenSkyClientCredentials
import com.example.xcpro.adsb.OpenSkyCredentialsRepository
import com.example.xcpro.adsb.OpenSkyTokenRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class AdsbSettingsUseCase @Inject constructor(
    private val repository: AdsbTrafficPreferencesRepository,
    private val credentialsRepository: OpenSkyCredentialsRepository,
    private val tokenRepository: OpenSkyTokenRepository,
    private val adsbTrafficRepository: AdsbTrafficRepository
) {
    val iconSizePxFlow: Flow<Int> = repository.iconSizePxFlow

    suspend fun setIconSizePx(iconSizePx: Int) {
        repository.setIconSizePx(iconSizePx)
    }

    fun loadOpenSkyCredentials(): OpenSkyClientCredentials? =
        credentialsRepository.loadCredentials()

    fun saveOpenSkyCredentials(clientId: String, clientSecret: String) {
        credentialsRepository.saveCredentials(clientId = clientId, clientSecret = clientSecret)
        tokenRepository.invalidate()
        adsbTrafficRepository.reconnectNow()
    }

    fun clearOpenSkyCredentials() {
        credentialsRepository.clearCredentials()
        tokenRepository.invalidate()
        adsbTrafficRepository.reconnectNow()
    }
}
