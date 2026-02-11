package com.example.xcpro.screens.navdrawer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.xcpro.adsb.ADSB_ICON_SIZE_DEFAULT_PX
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class AdsbSettingsViewModel @Inject constructor(
    private val useCase: AdsbSettingsUseCase
) : ViewModel() {
    val iconSizePx: StateFlow<Int> = useCase.iconSizePxFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ADSB_ICON_SIZE_DEFAULT_PX
        )

    fun setIconSizePx(iconSizePx: Int) {
        viewModelScope.launch {
            useCase.setIconSizePx(iconSizePx)
        }
    }
}
