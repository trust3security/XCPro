package com.trust3.xcpro.startup

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.trust3.xcpro.R
import com.trust3.xcpro.livesource.LiveStartupRequirement
import dagger.hilt.android.EntryPointAccessors

@Composable
internal fun StartupChooserScreen(
    onOpenFlying: () -> Unit,
    onOpenFriendsFlying: () -> Unit
) {
    val context = LocalContext.current
    val runtimeEntryPoint = remember(context) {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            StartupChooserRuntimeEntryPoint::class.java
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            runtimeEntryPoint.varioRuntimeControlPort().ensureRunningIfPermitted()
            onOpenFlying()
        } else {
            Toast.makeText(
                context,
                context.getString(R.string.location_permission_required_message),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    fun openFlying() {
        when (runtimeEntryPoint.liveSourceStatePort().state.value.startupRequirement) {
            LiveStartupRequirement.ANDROID_FINE_LOCATION_PERMISSION -> {
                permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            }

            LiveStartupRequirement.NONE -> {
                runtimeEntryPoint.varioRuntimeControlPort().ensureRunningIfPermitted()
                onOpenFlying()
            }
        }
    }

    Surface {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.xcpro_logo),
                    contentDescription = "XCPro",
                    modifier = Modifier.sizeIn(maxWidth = 220.dp, maxHeight = 220.dp),
                    contentScale = ContentScale.Fit
                )
                Text(
                    text = "Choose how to open XCPro",
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center
                )
                Button(
                    onClick = ::openFlying,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Flying")
                }
                Button(
                    onClick = onOpenFriendsFlying,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Friends Flying")
                }
            }
        }
    }
}
