package com.example.hawkwind

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.window.DialogProperties
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.hawkwind.ui.AhrsScreen
import com.example.hawkwind.ui.SettingsScreen
import com.example.hawkwind.ui.VarioScreen
import com.example.hawkwind.ui.WindScreen
import com.example.hawkwind.ui.theme.AppTheme
import com.example.hawkwind.core.AppCtx
import com.example.hawkwind.BuildConfig

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppCtx.init(applicationContext)

        if (BuildConfig.HAS_REAL) {
            val request = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }
            request.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        }

        setContent { AppTheme { LaunchGate() } }
    }
}

@Composable
private fun LaunchGate() {
    var picked by remember { mutableStateOf(false) }
    var chosen by remember { mutableStateOf("SIM") }
    if (!picked) {
        ModePickerDialog(
            onPicked = { mode ->
                chosen = mode
                picked = true
            }
        )
    } else {
        AppRoot(chosen)
    }
}

@Composable
private fun ModePickerDialog(onPicked: (String) -> Unit) {
    AlertDialog(
        onDismissRequest = { },
        title = { Text("Select Start Mode") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Pick how to run this session:")
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = { onPicked("REAL") }, enabled = BuildConfig.HAS_REAL) { Text("REAL") }
                    Button(onClick = { onPicked("SIM") }) { Text("SIM") }
                }
                if (!BuildConfig.HAS_REAL) {
                    Text("REAL mode is not available in this build.", color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
    )
}

@Composable
fun AppRoot(mode: String) {
    val nav = rememberNavController()
    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(selected = false, onClick = { nav.navigate("wind") }, label = { Text("Wind") }, icon = { Icon(Icons.Filled.ArrowUpward, null) })
                NavigationBarItem(selected = false, onClick = { nav.navigate("vario") }, label = { Text("Vario") }, icon = { Icon(Icons.Filled.Speed, null) })
                NavigationBarItem(selected = false, onClick = { nav.navigate("ahrs") }, label = { Text("AHRS") }, icon = { Icon(Icons.Filled.Explore, null) })
                NavigationBarItem(selected = false, onClick = { nav.navigate("settings") }, label = { Text("Settings") }, icon = { Icon(Icons.Filled.Settings, null) })
            }
        }
    ) { padding ->
        NavHost(navController = nav, startDestination = "wind", modifier = androidx.compose.ui.Modifier.padding(padding)) {
            composable("wind") { WindScreen(mode) }
            composable("vario") { VarioScreen(mode) }
            composable("ahrs") { AhrsScreen(mode) }
            composable("settings") { SettingsScreen(mode) }
        }
    }
}
