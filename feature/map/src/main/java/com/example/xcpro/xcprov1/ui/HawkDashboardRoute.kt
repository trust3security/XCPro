package com.example.xcpro.xcprov1.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.xcpro.map.LocationManager
import com.example.xcpro.xcprov1.viewmodel.HawkDashboardViewModel

@Composable
fun HawkDashboardRoute(
    locationManager: LocationManager
) {
    val controller = locationManager.xcproV1Controller
    val garminStatusFlow = locationManager.garminStatusFlow
    val viewModel: HawkDashboardViewModel = viewModel(
        factory = remember(locationManager) {
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    if (modelClass.isAssignableFrom(HawkDashboardViewModel::class.java)) {
                        return HawkDashboardViewModel(
                            controller = controller,
                            garminStatusFlow = garminStatusFlow,
                            autoConnectGarmin = { locationManager.connectGarminGlo() },
                            disconnectGarmin = { locationManager.disconnectGarminGlo() }
                        ) as T
                    }
                    throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
                }
            }
        }
    )

    HawkDashboardScreen(
        modifier = Modifier,
        viewModel = viewModel
    )
}
