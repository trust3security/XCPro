package com.example.xcpro.skysight

import android.content.Context
import android.content.SharedPreferences
import android.provider.Settings
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import com.example.xcpro.common.debug.DebugUtils
import com.google.gson.GsonBuilder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

class SkysightClient private constructor(private val context: Context) {
    
    companion object {
        private const val BASE_URL = "https://skysight.io/api/"
        private const val API_KEY = "XCPRO"
        private const val PREFS_NAME = "skysight_prefs"
        private const val ENCRYPTED_PREFS_NAME = "skysight_secure_prefs"
        private const val KEY_API_TOKEN = "api_token"
        private const val KEY_TOKEN_EXPIRY = "token_expiry"
        private const val KEY_USERNAME = "username"
        private const val KEY_PASSWORD = "password"
        private const val KEY_SELECTED_REGION = "selected_region"
        private const val KEY_SHOW_SATELLITE = "show_satellite"
        private const val KEY_SHOW_RAIN = "show_rain"
        
        @Volatile
        private var INSTANCE: SkysightClient? = null
        
        fun getInstance(context: Context): SkysightClient {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SkysightClient(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    private val encryptedPrefs: SharedPreferences by lazy {
        val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        EncryptedSharedPreferences.create(
            ENCRYPTED_PREFS_NAME,
            masterKeyAlias,
            context,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }
    
    private val _isAuthenticated = MutableStateFlow(false)
    val isAuthenticated: StateFlow<Boolean> = _isAuthenticated
    
    private val _availableRegions = MutableStateFlow<List<Region>>(emptyList())
    val availableRegions: StateFlow<List<Region>> = _availableRegions
    
    private val _selectedRegion = MutableStateFlow<String?>(null)
    val selectedRegion: StateFlow<String?> = _selectedRegion
    
    private val _availableRegionsList = MutableStateFlow<List<String>>(emptyList())
    val availableRegionsList: StateFlow<List<String>> = _availableRegionsList
    
    private val _availableLayers = MutableStateFlow<List<LayerInfo>>(emptyList())
    val availableLayers: StateFlow<List<LayerInfo>> = _availableLayers
    
    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        })
        .build()
    
    private val retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create(
            GsonBuilder()
                .setLenient()
                .create()
        ))
        .build()
    
    private val authApi = retrofit.create(SkysightAuthApi::class.java)
    private val dataApi = retrofit.create(SkysightDataApi::class.java)
    private val tileApi = retrofit.create(SkysightTileApi::class.java)
    
    init {
        setupDevCredentials() // Auto-setup for dev builds
        checkExistingAuth()
        tryAutoLogin()
    }
    
    private fun checkExistingAuth() {
        val token = prefs.getString(KEY_API_TOKEN, null)
        val expiry = prefs.getLong(KEY_TOKEN_EXPIRY, 0)
        val currentTime = System.currentTimeMillis() / 1000
        
        if (token != null && expiry > currentTime) {
            _isAuthenticated.value = true
            loadCachedData()
        }
    }
    
    suspend fun authenticate(username: String, password: String): Result<Unit> {
        return try {
            android.util.Log.d("SkysightClient", "🔐 Starting authentication for user: $username")
            val deviceId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            android.util.Log.d("SkysightClient", "📱 Device ID: $deviceId")
            
            val request = SkysightAuthRequest(
                username = username,
                password = password,
                device_serial = deviceId
            )
            
            android.util.Log.d("SkysightClient", "🌐 Making auth request to: $BASE_URL with API key: $API_KEY")
            val response = authApi.authenticate(API_KEY, request)
            
            android.util.Log.d("SkysightClient", "📡 Response code: ${response.code()}")
            
            if (response.isSuccessful) {
                android.util.Log.d("SkysightClient", "✅ Authentication successful")
                response.body()?.let { authResponse ->
                    android.util.Log.d("SkysightClient", "🔑 Got API key: ${authResponse.key.take(8)}...")
                    prefs.edit()
                        .putString(KEY_API_TOKEN, authResponse.key)
                        .putLong(KEY_TOKEN_EXPIRY, authResponse.valid_until)
                        .putString(KEY_USERNAME, username)
                        .apply()
                    
                    saveCredentials(username, password)
                    
                    _isAuthenticated.value = true
                    
                    // Store the available regions from auth response  
                    authResponse.allowed_regions?.let { regions ->
                        _availableRegionsList.value = regions
                        android.util.Log.d("SkysightClient", "📍 Available regions from auth: ${regions.joinToString(", ")}")
                    }
                    
                    loadRegions()
                    Result.success(Unit)
                } ?: Result.failure(Exception("Empty response"))
            } else {
                val errorBody = response.errorBody()?.string() ?: "Unknown error"
                android.util.Log.e("SkysightClient", "❌ Auth failed: ${response.code()} - $errorBody")
                Result.failure(Exception("Authentication failed: ${response.code()} - $errorBody"))
            }
        } catch (e: Exception) {
            DebugUtils.logError("SkysightClient", "Authentication error", e)
            Result.failure(e)
        }
    }
    
    suspend fun loadRegions() {
        try {
            val authToken = prefs.getString(KEY_API_TOKEN, null)
            if (authToken == null) {
                android.util.Log.e("SkysightClient", "❌ No auth token available for regions")
                return
            }
            
            android.util.Log.d("SkysightClient", "🔄 Loading regions with auth token: ${authToken.take(8)}...")
            val response = dataApi.getRegions(authToken)
            if (response.isSuccessful) {
                response.body()?.let { regions ->
                    _availableRegions.value = regions
                    
                    // Update the region list that UI uses
                    val regionIds = regions.map { it.id }
                    _availableRegionsList.value = regionIds
                    android.util.Log.d("SkysightClient", "📍 Loaded regions from API: ${regionIds.joinToString(", ")}")
                    
                    // Load selected region if cached
                    val selectedRegionId = prefs.getString(KEY_SELECTED_REGION, null)
                    selectedRegionId?.let { regionId ->
                        // Check if cached region is still available
                        if (regionIds.contains(regionId)) {
                            _selectedRegion.value = regionId
                            android.util.Log.d("SkysightClient", "🔄 Restored cached region: $regionId")
                        } else {
                            android.util.Log.w("SkysightClient", "⚠️ Cached region $regionId no longer available")
                            _selectedRegion.value = null
                            prefs.edit().remove(KEY_SELECTED_REGION).apply()
                        }
                    }
                }
            } else {
                val errorBody = response.errorBody()?.string() ?: "Unknown error"
                android.util.Log.e("SkysightClient", "❌ Failed to load regions: ${response.code()} - $errorBody")
            }
        } catch (e: Exception) {
            android.util.Log.e("SkysightClient", "💥 Exception loading regions", e)
            DebugUtils.logError("SkysightClient", "Error loading regions", e)
        }
    }
    
    suspend fun selectRegion(regionId: String) {
        _selectedRegion.value = regionId
        prefs.edit().putString(KEY_SELECTED_REGION, regionId).apply()
        android.util.Log.d("SkysightClient", "✅ Selected region: $regionId")
        loadLayers(regionId)
    }
    
    private suspend fun loadLayers(regionId: String) {
        try {
            android.util.Log.d("SkysightClient", "🔄 Loading layers for region: $regionId")
            val authToken = prefs.getString(KEY_API_TOKEN, null)
            if (authToken == null) {
                android.util.Log.e("SkysightClient", "❌ No auth token available for layers")
                return
            }
            
            val response = dataApi.getLayers(authToken, regionId)
            android.util.Log.d("SkysightClient", "📡 Layers API response code: ${response.code()}")
            
            if (response.isSuccessful) {
                response.body()?.let { layers ->
                    _availableLayers.value = layers
                    android.util.Log.d("SkysightClient", "✅ Loaded ${layers.size} layers: ${layers.map { it.name }}")
                } ?: run {
                    android.util.Log.w("SkysightClient", "⚠️ Layers response body is null")
                }
            } else {
                val errorBody = response.errorBody()?.string() ?: "Unknown error"
                android.util.Log.e("SkysightClient", "❌ Failed to load layers: ${response.code()} - $errorBody")
            }
        } catch (e: Exception) {
            android.util.Log.e("SkysightClient", "❌ Exception loading layers", e)
            DebugUtils.logError("SkysightClient", "Error loading layers", e)
        }
    }
    
    private fun loadCachedData() {
        // Load cached selected region
        val selectedRegionId = prefs.getString(KEY_SELECTED_REGION, null)
        selectedRegionId?.let { regionId ->
            _selectedRegion.value = regionId
            android.util.Log.d("SkysightClient", "🔄 Loaded cached region: $regionId")
        }
        
        // Load regions if authenticated
        CoroutineScope(Dispatchers.IO).launch {
            loadRegions()
            
            // Also load layers for the cached region
            selectedRegionId?.let { regionId ->
                android.util.Log.d("SkysightClient", "🔄 Loading layers for cached region: $regionId")
                loadLayers(regionId)
            }
        }
    }
    
    fun logout() {
        prefs.edit().clear().apply()
        encryptedPrefs.edit().clear().apply()
        _isAuthenticated.value = false
        _availableRegions.value = emptyList()
        _selectedRegion.value = null
        _availableLayers.value = emptyList()
    }
    
    private fun saveCredentials(username: String, password: String) {
        try {
            encryptedPrefs.edit()
                .putString(KEY_USERNAME, username)
                .putString(KEY_PASSWORD, password)
                .apply()
            android.util.Log.d("SkysightClient", "🔐 Credentials saved securely")
        } catch (e: Exception) {
            DebugUtils.logError("SkysightClient", "Failed to save credentials", e)
        }
    }
    
    private fun getSavedCredentials(): Pair<String?, String?> {
        return try {
            val username = encryptedPrefs.getString(KEY_USERNAME, null)
            val password = encryptedPrefs.getString(KEY_PASSWORD, null)
            Pair(username, password)
        } catch (e: Exception) {
            DebugUtils.logError("SkysightClient", "Failed to load saved credentials", e)
            Pair(null, null)
        }
    }
    
    private fun tryAutoLogin() {
        val (username, password) = getSavedCredentials()
        if (username != null && password != null && !_isAuthenticated.value) {
            android.util.Log.d("SkysightClient", "🔄 Attempting auto-login with saved credentials")
            CoroutineScope(Dispatchers.IO).launch {
                authenticate(username, password)
                    .onSuccess {
                        android.util.Log.d("SkysightClient", "✅ Auto-login successful")
                
                // Load layers for cached region after successful authentication
                val selectedRegionId = prefs.getString(KEY_SELECTED_REGION, null)
                selectedRegionId?.let { regionId ->
                    android.util.Log.d("SkysightClient", "🔄 Loading layers for cached region after auto-login: $regionId")
                    loadLayers(regionId)
                }
                    }
                    .onFailure { error ->
                        android.util.Log.w("SkysightClient", "⚠️ Auto-login failed: ${error.message}")
                    }
            }
        }
    }
    
    // Settings getters/setters
    fun getShowSatellite(): Boolean = prefs.getBoolean(KEY_SHOW_SATELLITE, true)
    fun setShowSatellite(show: Boolean) = prefs.edit().putBoolean(KEY_SHOW_SATELLITE, show).apply()
    
    fun getShowRain(): Boolean = prefs.getBoolean(KEY_SHOW_RAIN, true)
    fun setShowRain(show: Boolean) = prefs.edit().putBoolean(KEY_SHOW_RAIN, show).apply()
    
    fun getApiKey(): String = API_KEY
    
    fun getAuthToken(): String? = prefs.getString(KEY_API_TOKEN, null)
    
    fun getTileApi(): SkysightTileApi = tileApi
    
    fun getDataApi(): SkysightDataApi = dataApi
    
    fun hasStoredCredentials(): Boolean {
        val (username, password) = getSavedCredentials()
        return !username.isNullOrBlank() && !password.isNullOrBlank()
    }
    
    
    // Auto-setup for development builds
    fun setupDevCredentials() {
        if (!hasStoredCredentials()) {
            android.util.Log.d("SkysightClient", "🛠️ Setting up dev credentials")
            saveCredentials("david@trust3security.com", "")
            prefs.edit().putString(KEY_SELECTED_REGION, "EAST_AUS").apply()
        }
    }
}
