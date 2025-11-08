package com.example.xcpro

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.util.TypedValue
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.AttrRes
import androidx.annotation.ColorInt
import androidx.core.graphics.ColorUtils
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.xcpro.screens.navdrawer.lookandfeel.StatusBarStyle
import com.example.xcpro.screens.navdrawer.lookandfeel.StatusBarStyleApplier
import com.example.xcpro.ui.theme.Baseui1Theme
import com.example.xcpro.service.VarioForegroundService
import dagger.hilt.android.AndroidEntryPoint
import org.maplibre.android.MapLibre
import org.maplibre.android.WellKnownTileServer

private const val TAG = "MainActivity"

@AndroidEntryPoint
class MainActivity : ComponentActivity(), StatusBarStyleApplier {

    private var currentProfileId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        MapLibre.getInstance(this, "nYDScLfnBm52GAc3jXEZ", WellKnownTileServer.MapTiler)

        val setupManager = FirstTimeSetupManager.getInstance(this)
        if (setupManager.isFirstLaunch()) {
            Log.i(TAG, "First launch detected - performing setup")
            setupManager.performFirstTimeSetup()
        }
        Log.d(TAG, "Setup info: ${setupManager.getSetupInfo()}")

        com.example.xcpro.skysight.SkysightAutoTest.runNetworkTests(this)

        // runKeyholeVerificationTest() // TODO: Re-enable after keyhole implementation is completed

        setContent {
            Baseui1Theme {
                MainActivityScreen()
            }
        }

        VarioForegroundService.start(this)

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
        currentProfileId = profileId
        Log.d(TAG, "applyUserStatusBarStyle: profile=$profileId")

        try {
            val sharedPrefs = getSharedPreferences("LookAndFeelPrefs", Context.MODE_PRIVATE)
            val styleId = profileId?.let {
                sharedPrefs.getString("profile_${it}_status_bar_style", StatusBarStyle.TRANSPARENT.id)
            } ?: StatusBarStyle.TRANSPARENT.id

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
}
