package com.trust3.xcpro.map.ui

import android.content.Context
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material3.DrawerState
import com.trust3.xcpro.map.MapUiEffect
import kotlinx.coroutines.flow.Flow

/**
 * Side effects and activity-result helpers used by MapScreen.
 */
@Composable
internal fun MapScreenSideEffects(
    uiEffects: Flow<MapUiEffect>,
    drawerState: DrawerState,
    context: Context,
    onDrawerOpenChanged: (Boolean) -> Unit
) {
    LaunchedEffect(uiEffects, context) {
        uiEffects.collect { effect ->
            when (effect) {
                is MapUiEffect.ShowToast -> {
                    Toast.makeText(context, effect.message, Toast.LENGTH_LONG).show()
                }
                MapUiEffect.OpenDrawer -> drawerState.open()
                MapUiEffect.CloseDrawer -> drawerState.close()
            }
        }
    }

    LaunchedEffect(drawerState.isOpen) {
        onDrawerOpenChanged(drawerState.isOpen)
    }
}
