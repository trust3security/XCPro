package com.example.dfcards.dfcards.calculations

import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

/**
 * Open-Meteo Elevation API Client - KISS approach for global terrain elevation
 *
 * API: https://open-meteo.com/en/docs/elevation-api
 *
 * ADVANTAGES:
 * -  Free, no API key required
 * -  Global coverage (SRTM30 based, 90m resolution)
 * -  Simple HTTP GET request
 * -  No authentication needed
 * -  Reliable open-source project
 * -  Safety checks (permission + network state)
 *
 * USAGE:
 * ```kotlin
 * val api = OpenMeteoElevationApi(context)
 * val elevation = api.fetchElevation(47.5, 13.4) // Returns 1421.0 meters
 * ```
 */
class OpenMeteoElevationApi(private val context: Context) {

    companion object {
        private const val TAG = "OpenMeteoAPI"
        private const val BASE_URL = "https://api.open-meteo.com/v1/elevation"
        private const val TIMEOUT_MS = 10000 // 10 seconds
    }

    /**
     * Check if INTERNET permission is granted (KISS safety check)
     */
    private fun hasInternetPermission(): Boolean {
        val hasPermission = context.checkSelfPermission(android.Manifest.permission.INTERNET) ==
                PackageManager.PERMISSION_GRANTED
        if (!hasPermission) {
            Log.e(TAG, " INTERNET permission not granted")
        }
        return hasPermission
    }

    /**
     * Check if network is available (KISS safety check)
     */
    private fun isNetworkAvailable(): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        if (connectivityManager == null) {
            Log.e(TAG, " ConnectivityManager not available")
            return false
        }

        val network = connectivityManager.activeNetwork
        if (network == null) {
            Log.w(TAG, " No active network")
            return false
        }

        val capabilities = connectivityManager.getNetworkCapabilities(network)
        val isConnected = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        if (!isConnected) {
            Log.w(TAG, " Network has no internet capability")
        }
        return isConnected
    }

    /**
     * Fetch terrain elevation from Open-Meteo API
     *
     * KISS SAFETY: Checks permission + network state before making request
     *
     * @param lat Latitude (-90 to 90)
     * @param lon Longitude (-180 to 180)
     * @return Elevation in meters above sea level, or null if request fails
     */
    suspend fun fetchElevation(lat: Double, lon: Double): Double? = withContext(Dispatchers.IO) {
        // KISS Safety Check #1: Permission
        if (!hasInternetPermission()) {
            return@withContext null
        }

        // KISS Safety Check #2: Network availability
        if (!isNetworkAvailable()) {
            return@withContext null
        }

        // Validate coordinates
        if (lat < -90 || lat > 90 || lon < -180 || lon > 180) {
            Log.e(TAG, "Invalid coordinates: lat=$lat, lon=$lon")
            return@withContext null
        }

        // Build request URL
        val urlString = "$BASE_URL?latitude=$lat&longitude=$lon"

        return@withContext try {
            Log.d(TAG, "Fetching elevation for ($lat, $lon)")

            val url = URL(urlString)
            val connection = url.openConnection() as HttpURLConnection

            // Configure connection
            connection.requestMethod = "GET"
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.setRequestProperty("Accept", "application/json")

            // Check response code
            val responseCode = connection.responseCode
            if (responseCode != HttpURLConnection.HTTP_OK) {
                Log.e(TAG, "HTTP error: $responseCode")
                connection.disconnect()
                return@withContext null
            }

            // Read response
            val response = BufferedReader(InputStreamReader(connection.inputStream)).use { reader ->
                reader.readText()
            }

            connection.disconnect()

            // Parse JSON response
            val json = JSONObject(response)
            val elevationArray = json.getJSONArray("elevation")

            if (elevationArray.length() > 0) {
                val elevation = elevationArray.getDouble(0)
                Log.d(TAG, " Elevation: ${elevation.toInt()}m at ($lat, $lon)")
                elevation
            } else {
                Log.e(TAG, " No elevation data in response")
                null
            }

        } catch (e: Exception) {
            Log.e(TAG, " Failed to fetch elevation: ${e.message}", e)
            null
        }
    }

    /**
     * Fetch elevations for multiple locations (batch request)
     *
     * @param locations List of (lat, lon) pairs
     * @return Map of location to elevation, null for failed requests
     */
    suspend fun fetchElevationsBatch(locations: List<Pair<Double, Double>>): Map<Pair<Double, Double>, Double?> {
        val results = mutableMapOf<Pair<Double, Double>, Double?>()

        locations.forEach { (lat, lon) ->
            results[Pair(lat, lon)] = fetchElevation(lat, lon)
        }

        return results
    }
}
