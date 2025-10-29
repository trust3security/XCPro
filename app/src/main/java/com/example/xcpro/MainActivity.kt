package com.example.xcpro

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import dagger.hilt.android.AndroidEntryPoint
import androidx.core.graphics.toColorInt
import com.example.xcpro.ui.theme.Baseui1Theme
import com.example.xcpro.screens.navdrawer.lookandfeel.StatusBarStyle
import org.maplibre.android.MapLibre
import org.maplibre.android.WellKnownTileServer

private const val TAG = "MainActivity"

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private var currentProfileId: String? = null
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ✅ Only set basic window flags that are safe to set early
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Background sensor work stays tied to the foreground lifecycle; avoiding partial wake locks reduces battery drain.
        MapLibre.getInstance(this, "nYDScLfnBm52GAc3jXEZ", WellKnownTileServer.MapTiler)
        
        // ✅ First-time setup - Clear cache and set defaults
        val setupManager = FirstTimeSetupManager.getInstance(this)
        if (setupManager.isFirstLaunch()) {
            Log.i(TAG, "🚀 First launch detected - performing setup...")
            setupManager.performFirstTimeSetup()
        }
        Log.d(TAG, "Setup Info: ${setupManager.getSetupInfo()}")
        
        // Test Skysight integration
        com.example.xcpro.skysight.SkysightAutoTest.runNetworkTests(this)
        
        // Run keyhole verification test
        // runKeyholeVerificationTest() // TODO: Re-enable after keyhole implementation is completed
        
        setContent {
            Baseui1Theme {
                MainActivityScreen()
            }
        }

        // ✅ Apply user's status bar style after setContent
        Log.d("MainActivity", "🚀 onCreate: Applying initial status bar style")
        applyUserStatusBarStyle(null) // No profile initially
    }

    /**
     * Run keyhole verification test to check calculator/display synchronization
     * TODO: Re-enable after keyhole implementation is completed
     */
    /*
    private fun runKeyholeVerificationTest() {
        try {
            Log.d(TAG, "🔑 Starting keyhole verification test...")
            println("🔑 KEYHOLE VERIFICATION TEST STARTING...")

            val verification = com.example.xcpro.tasks.KeyholeVerification()
            val results = verification.verifyKeyholeImplementation()

            println("🔑 KEYHOLE VERIFICATION RESULTS:")
            println(results)

            Log.d(TAG, "🔑 Keyhole verification completed")
            Log.d(TAG, results)

        } catch (e: Exception) {
            Log.e(TAG, "🔑 ERROR: Keyhole verification failed: ${e.message}", e)
            println("🔑 ERROR: Keyhole verification failed: ${e.message}")
            e.printStackTrace()
        }
    }
    */

    override fun onResume() {
        super.onResume()
        // Reapply status bar style when activity resumes
        Log.d("MainActivity", "📱 onResume: Reapplying status bar style for profile: $currentProfileId")
        applyUserStatusBarStyle(currentProfileId)

        // ✅ Restart sensors if they were suspended during sleep mode
        // This ensures GPS and other sensors resume after screen-off
        Log.d("MainActivity", "📱 onResume: Triggering sensor restart after possible sleep mode")
        // Note: The actual sensor restart is handled in MapScreen via LocationManager
        // This log helps track when the app returns from background/sleep
    }
    
    // ✅ Apply user's selected status bar style with navigation bar handling
    fun applyUserStatusBarStyle(profileId: String?) {
        currentProfileId = profileId // Store for reapplication
        Log.d("MainActivity", "🔍 applyUserStatusBarStyle called with profileId: $profileId")
        
        try {
            val sharedPrefs = getSharedPreferences("LookAndFeelPrefs", Context.MODE_PRIVATE)
            val styleId = if (profileId != null) {
                val savedStyleId = sharedPrefs.getString("profile_${profileId}_status_bar_style", StatusBarStyle.TRANSPARENT.id)
                Log.d("MainActivity", "📋 Retrieved saved style ID: $savedStyleId for profile: $profileId")
                savedStyleId
            } else {
                Log.d("MainActivity", "⚠️ No profileId provided, using default TRANSPARENT")
                StatusBarStyle.TRANSPARENT.id
            }
            
            val selectedStyle = StatusBarStyle.values().find { it.id == styleId } ?: StatusBarStyle.TRANSPARENT
            Log.d("MainActivity", "🎨 Applying status bar style: ${selectedStyle.title} (${selectedStyle.id}) for profile: $profileId")
            Log.d("MainActivity", "📱 Android SDK: ${Build.VERSION.SDK_INT}")
            Log.d("MainActivity", "🪟 Current window attributes:")
            Log.d("MainActivity", "   - statusBarColor: ${String.format("#%08X", window.statusBarColor)}")
            Log.d("MainActivity", "   - navigationBarColor: ${String.format("#%08X", window.navigationBarColor)}")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Log.d("MainActivity", "   - decorFitsSystemWindows: ${window.decorView.rootWindowInsets?.isVisible(WindowInsets.Type.statusBars())}")
            }
            
            when (selectedStyle) {
                StatusBarStyle.TRANSPARENT -> {
                    Log.d("MainActivity", "🔧 Applying TRANSPARENT style")
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        // ✅ CRITICAL: Allow content to draw behind system bars
                        window.setDecorFitsSystemWindows(false)
                        
                        // ✅ Status bar will be automatically transparent on this device
                        // ✅ Configure system UI for optimal map visibility
                        window.insetsController?.let { controller ->
                            controller.hide(WindowInsets.Type.navigationBars())
                            controller.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                            
                            // ✅ CRITICAL: Use DARK icons/text (black) for visibility on transparent map background
                            controller.setSystemBarsAppearance(
                                WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS,
                                WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                            )
                            controller.setSystemBarsAppearance(
                                WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,
                                WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
                            )
                            Log.d("MainActivity", "✅ TRANSPARENT: Dark icons enabled for map visibility")
                        }
                        
                        // ✅ Enable full edge-to-edge layout for map visibility
                        window.decorView.systemUiVisibility = window.decorView.systemUiVisibility or
                            View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        
                        Log.d("MainActivity", "✅ TRANSPARENT: Map displays behind transparent status bar with dark icons")
                    } else {
                        @Suppress("DEPRECATION")
                        window.statusBarColor = android.graphics.Color.TRANSPARENT
                        window.navigationBarColor = android.graphics.Color.TRANSPARENT
                        @Suppress("DEPRECATION")
                        window.decorView.systemUiVisibility = (
                            View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        )
                    }
                }
                StatusBarStyle.THEMED -> {
                    Log.d("MainActivity", "🔧 Applying THEMED style")
                    
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        // THEMED: Use decorFitsSystemWindows TRUE for dedicated status bar background
                        window.setDecorFitsSystemWindows(true)
                        
                        // DEBUG: Set to RED to test if colors are applying
                        val themedColor = "#FF0000".toColorInt() // RED for debugging
                        window.statusBarColor = themedColor
                        window.navigationBarColor = themedColor
                        
                        // Apply immediately without delay
                        window.insetsController?.let { controller ->
                            // Use dark icons on light themed background
                            controller.setSystemBarsAppearance(
                                WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS,
                                WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                            )
                            controller.setSystemBarsAppearance(
                                WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,
                                WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
                            )
                            Log.d("MainActivity", "✅ THEMED: Applied white background with dark icons")
                        }
                        
                        // Clear any conflicting system UI flags
                        window.decorView.systemUiVisibility = 0
                        
                        // Force refresh
                        window.decorView.invalidate()
                        
                        Log.d("MainActivity", "🎨 THEMED - Status bar: ${String.format("#%08X", window.statusBarColor)}")
                        Log.d("MainActivity", "🎨 THEMED - Navigation bar: ${String.format("#%08X", window.navigationBarColor)}")
                    } else {
                        @Suppress("DEPRECATION")
                        window.statusBarColor = "#FFFFFF".toColorInt()
                        window.navigationBarColor = "#FFFFFF".toColorInt()
                        @Suppress("DEPRECATION")
                        window.decorView.systemUiVisibility = (
                            View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or
                            View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
                        )
                    }
                }
                StatusBarStyle.EDGE_TO_EDGE -> {
                    Log.d("MainActivity", "🔧 Applying EDGE_TO_EDGE style")
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        window.setDecorFitsSystemWindows(false)
                        window.statusBarColor = android.graphics.Color.TRANSPARENT
                        window.navigationBarColor = android.graphics.Color.TRANSPARENT
                        
                        // Apply immediately without delay
                        window.insetsController?.let { controller ->
                            controller.hide(WindowInsets.Type.navigationBars())
                            controller.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                            // Edge-to-edge: Use light icons (white) on content background
                            controller.setSystemBarsAppearance(0, WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS)
                            Log.d("MainActivity", "✅ EDGE_TO_EDGE: Light icons enabled")
                        }
                        
                        // Force refresh
                        window.decorView.invalidate()
                        Log.d("MainActivity", "📊 EDGE_TO_EDGE - Status bar: ${String.format("#%08X", window.statusBarColor)}")
                    } else {
                        @Suppress("DEPRECATION")
                        window.statusBarColor = android.graphics.Color.TRANSPARENT
                        window.navigationBarColor = android.graphics.Color.TRANSPARENT
                        @Suppress("DEPRECATION")
                        window.decorView.systemUiVisibility = (
                            View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        )
                    }
                }
                StatusBarStyle.OVERLAY -> {
                    Log.d("MainActivity", "🔧 Applying OVERLAY style")
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        window.setDecorFitsSystemWindows(false)
                        
                        val overlayColor = "#80000000".toColorInt() // Semi-transparent black
                        window.statusBarColor = overlayColor
                        window.navigationBarColor = "#80000000".toColorInt()
                        
                        // Apply immediately without delay
                        window.insetsController?.let { controller ->
                            controller.hide(WindowInsets.Type.navigationBars())
                            controller.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                            // Overlay: Use light icons (white) on dark overlay
                            controller.setSystemBarsAppearance(0, WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS)
                            Log.d("MainActivity", "✅ OVERLAY: Light icons on dark overlay")
                        }
                        
                        // Force refresh
                        window.decorView.invalidate()
                        Log.d("MainActivity", "📊 OVERLAY - Status bar: ${String.format("#%08X", window.statusBarColor)}")
                    } else {

                        @Suppress("DEPRECATION")
                        window.decorView.systemUiVisibility = (
                            View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        )
                    }
                }
            }
            
            Log.d("MainActivity", "✅ Status bar styling applied successfully")
            
        } catch (e: Exception) {
            Log.e("MainActivity", "❌ Error applying status bar style: ${e.message}", e)
            // Fallback to default behavior with light icons
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                window.decorView.post {
                    window.insetsController?.let { controller ->
                        controller.hide(WindowInsets.Type.navigationBars())
                        controller.setSystemBarsAppearance(0, WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS)
                    }
                }
            }
        }
    }
}
