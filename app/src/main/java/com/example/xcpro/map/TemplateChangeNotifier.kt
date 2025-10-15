package com.example.xcpro.map

/**
 * ✅ Singleton to communicate template changes from FlightDataMgmt to MapScreen
 * This allows FlightDataMgmt (in navigation) to notify MapScreen's FlightDataManager
 * when cards are toggled, triggering immediate template reload.
 */
object TemplateChangeNotifier {
    private var onTemplateChangedCallback: (() -> Unit)? = null

    /**
     * Register callback from MapScreen's FlightDataManager
     */
    fun registerCallback(callback: () -> Unit) {
        onTemplateChangedCallback = callback
        android.util.Log.d("TemplateChangeNotifier", "✅ Callback registered from MapScreen")
    }

    /**
     * Unregister callback when MapScreen is disposed
     */
    fun unregisterCallback() {
        onTemplateChangedCallback = null
        android.util.Log.d("TemplateChangeNotifier", "❌ Callback unregistered")
    }

    /**
     * Notify that template changed (called from FlightDataMgmt)
     */
    fun notifyTemplateChanged() {
        onTemplateChangedCallback?.invoke()
        android.util.Log.d("TemplateChangeNotifier", "🔔 Template change notification sent")
    }
}
