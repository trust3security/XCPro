package com.example.xcpro.navdrawer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch

@HiltViewModel
class NavDrawerViewModel @Inject constructor(
    private val useCase: NavDrawerConfigUseCase
) : ViewModel() {

    fun saveNavDrawerConfig(
        profileExpanded: Boolean,
        mapStyleExpanded: Boolean,
        settingsExpanded: Boolean
    ) {
        viewModelScope.launch {
            useCase.saveNavDrawerConfig(
                profileExpanded = profileExpanded,
                mapStyleExpanded = mapStyleExpanded,
                settingsExpanded = settingsExpanded
            )
        }
    }
}
