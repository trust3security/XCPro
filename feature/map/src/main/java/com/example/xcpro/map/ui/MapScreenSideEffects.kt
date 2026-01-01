package com.example.xcpro.map.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material3.DrawerState
import com.example.xcpro.map.MapUiEffect
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

@Composable
internal fun rememberReplayFilePicker(
    context: Context,
    onReplayFileChosen: (Uri, String?) -> Unit
): ActivityResultLauncher<Array<String>> {
    return rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val type = context.contentResolver.getType(uri)
            Log.i("MapScreen", "REPLAY_PICK uri=$uri type=$type")
            val name = resolveDisplayName(context, uri)
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }.onFailure { t ->
                Log.w("MapScreen", "REPLAY_PICK persistable permission failed: ${t.message}")
            }
            onReplayFileChosen(uri, name)
        } else {
            Log.w("MapScreen", "REPLAY_PICK cancelled (uri is null)")
        }
    }
}
