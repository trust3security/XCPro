package com.example.xcpro.screens.navdrawer

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DrawerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.example.xcpro.ogn.OGN_ICON_SIZE_MAX_PX
import com.example.xcpro.ogn.OGN_ICON_SIZE_MIN_PX
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OgnSettingsScreen(
    navController: NavHostController,
    drawerState: DrawerState
) {
    val viewModel: OgnSettingsViewModel = hiltViewModel()
    val scope = rememberCoroutineScope()
    val iconSizePx by viewModel.iconSizePx.collectAsStateWithLifecycle()
    var sliderValue by remember { mutableStateOf(iconSizePx.toFloat()) }

    LaunchedEffect(iconSizePx) {
        sliderValue = iconSizePx.toFloat()
    }

    Scaffold(
        topBar = {
            SettingsTopAppBar(
                title = "OGN",
                onNavigateUp = { navController.navigateUp() },
                onSecondaryNavigate = {
                    scope.launch {
                        navController.popBackStack("map", inclusive = false)
                        drawerState.open()
                    }
                },
                onNavigateToMap = {
                    scope.launch {
                        drawerState.close()
                        navController.popBackStack("map", inclusive = false)
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentAlignment = Alignment.TopCenter
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("OGN", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Adjust the OGN glider icon size shown on the map.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "OGN icon size: ${sliderValue.roundToInt()} px",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Slider(
                        value = sliderValue,
                        onValueChange = { value ->
                            val snapped = value.roundToInt().coerceIn(
                                OGN_ICON_SIZE_MIN_PX,
                                OGN_ICON_SIZE_MAX_PX
                            )
                            sliderValue = snapped.toFloat()
                            if (snapped != iconSizePx) {
                                viewModel.setIconSizePx(snapped)
                            }
                        },
                        valueRange = OGN_ICON_SIZE_MIN_PX.toFloat()..OGN_ICON_SIZE_MAX_PX.toFloat(),
                        steps = OGN_ICON_SIZE_MAX_PX - OGN_ICON_SIZE_MIN_PX - 1
                    )
                    Text(
                        text = "Minimum ${OGN_ICON_SIZE_MIN_PX}px, maximum ${OGN_ICON_SIZE_MAX_PX}px.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
