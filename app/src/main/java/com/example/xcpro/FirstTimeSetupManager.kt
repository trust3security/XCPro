package com.example.xcpro

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import org.json.JSONObject
import java.io.File

class FirstTimeSetupManager private constructor(private val context: Context) {
    
    companion object {
        private const val TAG = "FirstTimeSetup"
        private const val PREFS_NAME = "first_time_setup"
        private const val KEY_FIRST_LAUNCH = "is_first_launch"
        private const val KEY_SETUP_VERSION = "setup_version"
        private const val CURRENT_SETUP_VERSION = 1
        
        @Volatile
        private var INSTANCE: FirstTimeSetupManager? = null
        
        fun getInstance(context: Context): FirstTimeSetupManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: FirstTimeSetupManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    fun isFirstLaunch(): Boolean {
        val isFirst = prefs.getBoolean(KEY_FIRST_LAUNCH, true)
        val setupVersion = prefs.getInt(KEY_SETUP_VERSION, 0)
        
        // Consider it first launch if never launched OR setup version is outdated
        return isFirst || setupVersion < CURRENT_SETUP_VERSION
    }
    
    fun performFirstTimeSetup() {
        if (!isFirstLaunch()) {
            Log.d(TAG, "Not first launch, skipping setup")
            return
        }
        
        Log.i(TAG, "🚀 Performing first-time setup...")
        
        try {
            // 1. Clear any existing cache/config that might interfere
            clearPreviousCache()
            
            // 2. Set default hamburger menu configuration
            setupDefaultHamburgerMenu()
            
            // 3. Set default card positions and sizes
            setupDefaultCardLayout()
            
            // 4. Set default navigation drawer state
            setupDefaultNavigationDrawer()
            
            // 5. Initialize default map settings
            setupDefaultMapSettings()
            
            // 6. Mark setup as complete
            markSetupComplete()
            
            Log.i(TAG, "✅ First-time setup completed successfully")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error during first-time setup", e)
        }
    }
    
    private fun clearPreviousCache() {
        Log.d(TAG, "🧹 Clearing previous cache...")
        
        // Clear potentially conflicting preferences
        val prefsToClean = listOf(
            "hamburger_menu_prefs",
            "card_layout_prefs", 
            "drawer_config_prefs"
        )
        
        prefsToClean.forEach { prefName ->
            try {
                context.getSharedPreferences(prefName, Context.MODE_PRIVATE)
                    .edit()
                    .clear()
                    .apply()
                Log.d(TAG, "Cleared $prefName")
            } catch (e: Exception) {
                Log.w(TAG, "Could not clear $prefName", e)
            }
        }
    }
    
    private fun setupDefaultHamburgerMenu() {
        Log.d(TAG, "🍔 Setting up default hamburger menu...")
        
        val hamburgerPrefs = context.getSharedPreferences("hamburger_menu_prefs", Context.MODE_PRIVATE)
        hamburgerPrefs.edit().apply {
            // Default position: Top-left with standard padding
            putFloat("position_x", 16f) // 16dp from left
            putFloat("position_y", 60f) // Below status bar
            
            // Default size: Standard hamburger icon
            putFloat("icon_size", 24f) // 24dp icon
            putFloat("touch_area_size", 48f) // 48dp touch area (Material Design)
            
            // Default colors
            putString("icon_color", "#FFFFFF") // White icon
            putString("background_color", "transparent")
            
            // Default behavior
            putBoolean("auto_hide", false)
            putInt("animation_duration", 300)
            
            apply()
        }
        
        Log.d(TAG, "✅ Hamburger menu defaults set")
    }
    
    private fun setupDefaultCardLayout() {
        Log.d(TAG, "📱 Setting up default card layout...")
        
        val cardLayoutPrefs = context.getSharedPreferences("card_layout_prefs", Context.MODE_PRIVATE)
        cardLayoutPrefs.edit().apply {
            // Default card arrangement for different screen areas
            putString("top_cards", "[\"altitude\", \"ground_speed\"]")
            putString("middle_cards", "[\"vario\", \"wind\"]") 
            putString("bottom_cards", "[\"distance\", \"bearing\"]")
            
            // Default card sizes
            putString("card_size_mode", "medium") // small, medium, large
            putFloat("card_spacing", 8f) // 8dp between cards
            putFloat("card_corner_radius", 12f) // Rounded corners
            
            // Default margins
            putFloat("margin_horizontal", 16f)
            putFloat("margin_vertical", 8f)
            
            apply()
        }
        
        Log.d(TAG, "✅ Card layout defaults set")
    }
    
    private fun setupDefaultNavigationDrawer() {
        Log.d(TAG, "🗂️ Setting up default navigation drawer...")
        
        try {
            val configFile = File(context.filesDir, "configuration.json")
            val jsonObject = JSONObject().apply {
                put("navDrawer", JSONObject().apply {
                    put("profileExpanded", true) // Show profile section expanded
                    put("mapStyleExpanded", false) // Collapse map style initially  
                    put("settingsExpanded", false) // Collapse settings initially
                })
                
                put("drawer", JSONObject().apply {
                    put("defaultWidth", 280) // 280dp drawer width
                    put("gestureEnabled", true)
                    put("swipeEnabled", true)
                })
            }
            
            configFile.writeText(jsonObject.toString(2))
            Log.d(TAG, "✅ Navigation drawer defaults set")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error setting navigation drawer defaults", e)
        }
    }
    
    private fun setupDefaultMapSettings() {
        Log.d(TAG, "🗺️ Setting up default map settings...")
        
        val mapPrefs = context.getSharedPreferences("MapScreenPrefs", Context.MODE_PRIVATE)
        mapPrefs.edit().apply {
            // Default map style
            putString("map_style", "Topo")
            
            // Default zoom levels
            putFloat("default_zoom", 10f)
            putFloat("min_zoom", 3f)
            putFloat("max_zoom", 18f)
            
            // Default center (approximate world center)
            putFloat("default_lat", 20.0f)
            putFloat("default_lon", 0.0f)
            
            apply()
        }
        
        Log.d(TAG, "✅ Map defaults set")
    }
    
    private fun markSetupComplete() {
        prefs.edit().apply {
            putBoolean(KEY_FIRST_LAUNCH, false)
            putInt(KEY_SETUP_VERSION, CURRENT_SETUP_VERSION)
            putLong("setup_timestamp", System.currentTimeMillis())
            apply()
        }
    }
    
    fun resetToFirstLaunch() {
        Log.i(TAG, "🔄 Resetting to first launch state")
        prefs.edit().clear().apply()
    }
    
    fun getSetupInfo(): String {
        val isFirst = isFirstLaunch()
        val version = prefs.getInt(KEY_SETUP_VERSION, 0)
        val timestamp = prefs.getLong("setup_timestamp", 0)
        
        return "First Launch: $isFirst, Setup Version: $version, Last Setup: ${if (timestamp > 0) java.util.Date(timestamp) else "Never"}"
    }
}