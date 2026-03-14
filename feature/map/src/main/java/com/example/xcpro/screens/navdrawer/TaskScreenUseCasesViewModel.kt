package com.example.xcpro.screens.navdrawer

import androidx.lifecycle.ViewModel
import com.example.xcpro.airspace.AirspaceUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class TaskScreenUseCasesViewModel @Inject constructor(
    val airspaceUseCase: AirspaceUseCase
) : ViewModel()
