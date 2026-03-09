package com.example.xcpro

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.util.TypedValue
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.AttrRes
import androidx.annotation.ColorInt
import com.example.xcpro.BuildConfig
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import android.widget.Toast
import com.example.xcpro.screens.navdrawer.lookandfeel.StatusBarStyle
import com.example.xcpro.screens.navdrawer.lookandfeel.StatusBarStyleApplier
import com.example.xcpro.screens.navdrawer.lookandfeel.LookAndFeelPreferences
import com.example.xcpro.profiles.ProfileIdResolver
import com.example.xcpro.ui.theme.Baseui1Theme
import com.example.xcpro.service.VarioForegroundService
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch
import org.maplibre.android.MapLibre
import org.maplibre.android.WellKnownTileServer

private const val TAG = "MainActivity"

@AndroidEntryPoint
class MainActivity : ComponentActivity(), StatusBarStyleApplier {

    @Inject
    lateinit var lookAndFeelPreferences: LookAndFeelPreferences

    @Inject
    lateinit var firstTimeSetupManager: FirstTimeSetupManager

    private var currentProfileId: String? = null
    private var hasStartedVarioService = false
    private var keepSplashVisible = true

    private val locationPermissions = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION
    )

    private val locationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            val granted = locationPermissions.any { perm -> result[perm] == true }
            if (granted) {
                Log.i(TAG, "Location permission granted. Starting vario service.")
                startVarioServiceIfNeeded()
            } else {
                Log.w(TAG, "Location permission denied. Vario service not started.")
                Toast.makeText(
                    this,
                    getString(R.string.location_permission_required_message),
                    Toast.LENGTH_LONG
                ).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        splashScreen.setKeepOnScreenCondition { keepSplashVisible }

        super.onCreate(savedInstanceState)
        keepSplashVisible = false

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val mapLibreKey = BuildConfig.MAPLIBRE_API_KEY.ifBlank { "nYDScLfnBm52GAc3jXEZ" }
        MapLibre.getInstance(this, mapLibreKey, WellKnownTileServer.MapTiler)

        lifecycleScope.launch {
            firstTimeSetupManager.runIfNeeded()
            Log.d(TAG, "Setup info: ${firstTimeSetupManager.getSetupInfo()}")
        }

        setContent {
            Baseui1Theme {
                MainActivityScreen()
            }
        }

        ensureLocationPermissionThenStartService()

        Log.d(TAG, "onCreate: applying initial status bar style")
        applyUserStatusBarStyle(null)
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "onResume: reapplying status bar style for profile $currentProfileId")
        applyUserStatusBarStyle(currentProfileId)

        Log.d(TAG, "onResume: notifying sensor pipeline about potential resume")
    }

    override fun applyUserStatusBarStyle(profileId: String?) {
        currentProfileId = ProfileIdResolver.normalizeOrNull(profileId)
        Log.d(TAG, "applyUserStatusBarStyle: profile=$profileId")

        try {
            val resolvedProfileId = ProfileIdResolver.canonicalOrDefault(profileId)
            val styleId = lookAndFeelPreferences.getStatusBarStyleId(resolvedProfileId)

            val selectedStyle = StatusBarStyle.values().find { it.id == styleId } ?: StatusBarStyle.TRANSPARENT
            Log.d(TAG, "applyUserStatusBarStyle: resolved style ${selectedStyle.id}")

            when (selectedStyle) {
                StatusBarStyle.TRANSPARENT -> configureSystemBars(
                    fitsSystemWindows = false,
                    statusBarColor = Color.TRANSPARENT,
                    navigationBarColor = Color.TRANSPARENT,
                    lightStatusIcons = true,
                    lightNavigationIcons = true,
                    hideNavigation = true
                )

                StatusBarStyle.THEMED -> {
                    val themedColor = resolveThemeColor(android.R.attr.colorPrimary)
                    val lightBackground = ColorUtils.calculateLuminance(themedColor) > 0.5
                    configureSystemBars(
                        fitsSystemWindows = true,
                        statusBarColor = themedColor,
                        navigationBarColor = themedColor,
                        lightStatusIcons = lightBackground,
                        lightNavigationIcons = lightBackground,
                        hideNavigation = false
                    )
                }

                StatusBarStyle.EDGE_TO_EDGE -> configureSystemBars(
                    fitsSystemWindows = false,
                    statusBarColor = Color.TRANSPARENT,
                    navigationBarColor = Color.TRANSPARENT,
                    lightStatusIcons = false,
                    lightNavigationIcons = false,
                    hideNavigation = true
                )

                StatusBarStyle.OVERLAY -> {
                    val overlayColor = ColorUtils.setAlphaComponent(Color.BLACK, 0x80)
                    configureSystemBars(
                        fitsSystemWindows = false,
                        statusBarColor = overlayColor,
                        navigationBarColor = overlayColor,
                        lightStatusIcons = false,
                        lightNavigationIcons = false,
                        hideNavigation = true
                    )
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "Failed to apply status bar style", e)
            configureSystemBars(
                fitsSystemWindows = false,
                statusBarColor = Color.TRANSPARENT,
                navigationBarColor = Color.TRANSPARENT,
                lightStatusIcons = true,
                lightNavigationIcons = true,
                hideNavigation = true
            )
        }
    }

    @Suppress("DEPRECATION")
    private fun configureSystemBars(
        fitsSystemWindows: Boolean,
        @ColorInt statusBarColor: Int,
        @ColorInt navigationBarColor: Int,
        lightStatusIcons: Boolean,
        lightNavigationIcons: Boolean,
        hideNavigation: Boolean
    ) {
        WindowCompat.setDecorFitsSystemWindows(window, fitsSystemWindows)
        window.statusBarColor = statusBarColor
        window.navigationBarColor = navigationBarColor

        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.isAppearanceLightStatusBars = lightStatusIcons
        controller.isAppearanceLightNavigationBars = lightNavigationIcons

        if (hideNavigation) {
            controller.hide(WindowInsetsCompat.Type.navigationBars())
        } else {
            controller.show(WindowInsetsCompat.Type.navigationBars())
        }
    }

    @ColorInt
    private fun resolveThemeColor(@AttrRes attr: Int): Int {
        val typedValue = TypedValue()
        return if (theme.resolveAttribute(attr, typedValue, true)) {
            typedValue.data
        } else {
            Color.BLACK
        }
    }

    private fun ensureLocationPermissionThenStartService() {
        val alreadyGranted = locationPermissions.any { perm ->
            ContextCompat.checkSelfPermission(this, perm) == PackageManager.PERMISSION_GRANTED
        }
        if (alreadyGranted) {
            startVarioServiceIfNeeded()
        } else {
            locationPermissionLauncher.launch(locationPermissions)
        }
    }

    private fun startVarioServiceIfNeeded() {
        if (hasStartedVarioService) return
        val started = VarioForegroundService.startIfPermitted(this)
        hasStartedVarioService = started
    }
}
