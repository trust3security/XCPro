package com.trust3.xcpro.map.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.trust3.xcpro.map.model.GpsStatusUiModel
import com.trust3.xcpro.navdrawer.NavigationDrawer

/**
 * Drawer + content scaffold for the map screen, with GPS status and loading overlay.
 */
@Composable
internal fun MapScreenScaffold(
    inputs: MapScreenScaffoldChromeInputs,
    content: @Composable BoxScope.() -> Unit
) {
    val scaffold = inputs
    NavigationDrawer(
        drawerState = scaffold.drawerState,
        navController = scaffold.navController,
        profileExpanded = scaffold.profileExpanded,
        mapStyleExpanded = scaffold.mapStyleExpanded,
        settingsExpanded = scaffold.settingsExpanded,
        selectedMapStyle = scaffold.selectedMapStyle,
        onItemSelected = scaffold.onDrawerItemSelected,
        onMapStyleSelected = scaffold.onMapStyleSelected,
        onOpenGeneralSettings = scaffold.onOpenGeneralSettingsFromDrawer,
        content = {
            Box(modifier = Modifier.fillMaxSize()) {
                GpsStatusBanner(
                    status = scaffold.gpsStatus,
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .padding(top = 8.dp, start = 12.dp, end = 12.dp)
                )
                content()
                if (scaffold.isLoadingWaypoints) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.2f))
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                }
            }
        }
    )
}

@Composable
private fun GpsStatusBanner(status: GpsStatusUiModel, modifier: Modifier = Modifier) {
    val (text, color) = when (status) {
        GpsStatusUiModel.NoPermission -> "Location permission needed" to Color(0xFFB00020)
        GpsStatusUiModel.Disabled -> "GPS is off" to Color(0xFFB00020)
        is GpsStatusUiModel.LostFix -> "Waiting for GPS" to Color(0xFFCA8A04)
        GpsStatusUiModel.Searching -> "Searching for GPS" to Color(0xFFCA8A04)
        GpsStatusUiModel.CondorDisconnected -> "Condor bridge disconnected" to Color(0xFFB00020)
        GpsStatusUiModel.CondorStale -> "Condor stream stale" to Color(0xFFCA8A04)
        GpsStatusUiModel.CondorTransportError -> "Condor transport error" to Color(0xFFB00020)
        is GpsStatusUiModel.Ok -> return
    }
    Surface(
        color = color.copy(alpha = 0.85f),
        tonalElevation = 4.dp,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
        modifier = modifier
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            color = Color.White,
            style = MaterialTheme.typography.labelMedium,
            textAlign = TextAlign.Center
        )
    }
}
