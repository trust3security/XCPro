package com.example.dfcards.dfcards.calculations

import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.example.xcpro.core.common.logging.AppLogger
import kotlinx.coroutines.CoroutineDispatcher
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
class OpenMeteoElevationApi(
    private val context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    companion object {
        private const val TAG = "OpenMeteoAPI"
        private const val BASE_URL = "https://api.open-meteo.com/v1/elevation"
        private const val TIMEOUT_MS = 10000 // 10 seconds
    }

    private inline fun debug(message: () -> String) {
        AppLogger.d(TAG, message())
    }

    /**
     * Check if INTERNET permission is granted (KISS safety check)
     */
    private fun hasInternetPermission(): Boolean {
        val hasPermission = context.checkSelfPermission(android.Manifest.permission.INTERNET) ==
                PackageManager.PERMISSION_GRANTED
        if (!hasPermission) {
            AppLogger.e(TAG, "INTERNET permission not granted")
        }
        return hasPermission
    }

    /**
     * Check if network is available (KISS safety check)
     */
    private fun isNetworkAvailable(): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        if (connectivityManager == null) {
            AppLogger.e(TAG, "ConnectivityManager not available")
            return false
        }

        val network = connectivityManager.activeNetwork
        if (network == null) {
            AppLogger.w(TAG, "No active network")
            return false
        }

        val capabilities = connectivityManager.getNetworkCapabilities(network)
        val isConnected = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        if (!isConnected) {
            AppLogger.w(TAG, "Network has no internet capability")
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
    suspend fun fetchElevation(lat: Double, lon: Double): Double? = withContext(ioDispatcher) {
        // Reject invalid numeric input before any permission/network work.
        if (!lat.isFinite() || !lon.isFinite()) {
            AppLogger.e(TAG, "Invalid coordinates: non-finite input")
            return@withContext null
        }

        // Validate bounds before network work.
        if (lat < -90 || lat > 90 || lon < -180 || lon > 180) {
            AppLogger.e(TAG, "Invalid coordinates: out of bounds")
            return@withContext null
        }

        // KISS Safety Check #1: Permission
        if (!hasInternetPermission()) {
            return@withContext null
        }

        // KISS Safety Check #2: Network availability
        if (!isNetworkAvailable()) {
            return@withContext null
        }

        // Build request URL
        val urlString = "$BASE_URL?latitude=$lat&longitude=$lon"

        var connection: HttpURLConnection? = null

        return@withContext try {
            debug { "Fetching elevation for ${AppLogger.redactLatLon(lat, lon)}" }

            val url = URL(urlString)
            connection = url.openConnection() as HttpURLConnection

            // Configure connection
            connection.requestMethod = "GET"
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.setRequestProperty("Accept", "application/json")

            // Check response code
            val responseCode = connection.responseCode
            if (responseCode != HttpURLConnection.HTTP_OK) {
                AppLogger.e(TAG, "HTTP error: $responseCode")
                return@withContext null
            }

            // Read response
            val response = BufferedReader(InputStreamReader(connection.inputStream)).use { reader ->
                reader.readText()
            }

            // Parse JSON response
            val json = JSONObject(response)
            val elevationArray = json.getJSONArray("elevation")

            if (elevationArray.length() > 0) {
                val elevation = elevationArray.getDouble(0)
                debug { "Elevation: ${elevation.toInt()}m for ${AppLogger.redactLatLon(lat, lon)}" }
                elevation
            } else {
                AppLogger.e(TAG, "No elevation data in response")
                null
            }

        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to fetch elevation: ${e.message}", e)
            null
        } finally {
            connection?.disconnect()
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
