package com.example.xcpro.screens.navdrawer

import com.example.xcpro.ogn.OgnTrafficPreferencesRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class OgnSettingsUseCase @Inject constructor(
    private val repository: OgnTrafficPreferencesRepository
) {
    val iconSizePxFlow: Flow<Int> = repository.iconSizePxFlow
    val ownFlarmHexFlow: Flow<String?> = repository.ownFlarmHexFlow
    val ownIcaoHexFlow: Flow<String?> = repository.ownIcaoHexFlow

    suspend fun setIconSizePx(iconSizePx: Int) {
        repository.setIconSizePx(iconSizePx)
    }

    suspend fun setOwnFlarmHex(value: String?) {
        repository.setOwnFlarmHex(value)
    }

    suspend fun setOwnIcaoHex(value: String?) {
        repository.setOwnIcaoHex(value)
    }
}
