package com.example.dfcards.dfcards.calculations

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import java.io.*
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream
import kotlin.math.floor

/**
 * KISS SRTM Terrain Database - Global elevation data with on-demand tile loading
 *
 * PRINCIPLES:
 * - KISS: Simple tile download + cache, no complex logic
 * - SSOT: Local files are authoritative terrain source
 * - Global: Works worldwide (SRTM3 covers -60 to 60 latitude)
 * - Offline: Once cached, works without network
 *
 * SRTMGL1 Format (30m resolution):
 * - Resolution: 30m (1 arc-second at equator)
 * - Tile size: 1 x 1 (3601 x 3601 samples)
 * - File size: ~25 MB per tile (zip), ~24.7 MB (hgt)
 * - Coverage: Global (-60 to 60 latitude)
 *
 * Data Source: USGS SRTMGL1 (free, no auth required)
 */
class SrtmTerrainDatabase(
    private val context: Context,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
) {
    companion object {
        private const val TAG = "SrtmTerrain"
        private const val TILE_DIR = "srtm_cache"

        // USGS SRTMGL1 - 30m resolution (free, no authentication)
        private const val SRTM_BASE_URL = "https://e4ftl01.cr.usgs.gov/MEASURES/SRTMGL1.003/2000.02.11/"
    }

    // Cache for loaded tiles (in-memory)
    private val loadedTiles = mutableMapOf<String, SrtmTile>()

    // Track downloads in progress (prevent concurrent downloads of same tile)
    private val downloadingTiles = mutableSetOf<String>()

    /**
     * Get terrain elevation at lat/lon (KISS - one function does it all)
     *
     * @param lat Latitude (-60 to 60)
     * @param lon Longitude (-180 to 180)
     * @return Elevation in meters, or null if unavailable
     */
    suspend fun getElevation(lat: Double, lon: Double): Double? {
        // Check bounds
        if (lat < -60 || lat > 60) {
            Log.w(TAG, "Outside SRTM coverage: lat=$lat")
            return null
        }

        // Get tile name (e.g., "N33E151" for Sydney)
        val tileName = getTileName(lat, lon)

        // Load tile (from memory cache, disk cache, or download)
        val tile = loadTile(tileName) ?: return null

        // Query elevation from tile
        return tile.getElevation(lat, lon)
    }

    /**
     * Get SRTM tile name from coordinates
     * Examples: "N33E151" (Sydney), "S34E151" (south of Sydney)
     */
    private fun getTileName(lat: Double, lon: Double): String {
        val latInt = floor(lat).toInt()
        val lonInt = floor(lon).toInt()

        val latPrefix = if (latInt >= 0) "N" else "S"
        val lonPrefix = if (lonInt >= 0) "E" else "W"

        return String.format(
            "%s%02d%s%03d",
            latPrefix,
            Math.abs(latInt),
            lonPrefix,
            Math.abs(lonInt)
        )
    }

    /**
     * Load SRTM tile (KISS - checks memory, disk, then downloads)
     */
    private suspend fun loadTile(tileName: String): SrtmTile? = withContext(Dispatchers.IO) {
        // Check memory cache
        loadedTiles[tileName]?.let { return@withContext it }

        // Check disk cache
        val cacheFile = getTileCacheFile(tileName)
        if (cacheFile.exists()) {
            Log.d(TAG, "Loading tile $tileName from disk cache")
            val tile = SrtmTile.fromFile(cacheFile, tileName)
            if (tile != null) {
                loadedTiles[tileName] = tile
                return@withContext tile
            }
        }

        // Check if already downloading (prevent concurrent downloads)
        synchronized(downloadingTiles) {
            if (downloadingTiles.contains(tileName)) {
                Log.d(TAG, "Tile $tileName already downloading, skipping")
                return@withContext null
            }
            downloadingTiles.add(tileName)
        }

        // Download tile
        try {
            Log.d(TAG, "Downloading tile $tileName...")
            val downloaded = downloadTile(tileName, cacheFile)
            if (downloaded) {
                val tile = SrtmTile.fromFile(cacheFile, tileName)
                if (tile != null) {
                    loadedTiles[tileName] = tile
                    Log.d(TAG, " Tile $tileName ready")
                    return@withContext tile
                }
            }

            Log.e(TAG, " Failed to load tile $tileName")
            null
        } finally {
            synchronized(downloadingTiles) {
                downloadingTiles.remove(tileName)
            }
        }
    }

    /**
     * Download SRTM tile from USGS (handles ZIP extraction)
     */
    private fun downloadTile(tileName: String, outputFile: File): Boolean {
        return try {
            // USGS path: SRTMGL1.003/2000.02.11/S34E151.SRTMGL1.hgt.zip
            val url = "$SRTM_BASE_URL$tileName.SRTMGL1.hgt.zip"

            Log.d(TAG, "Downloading: $url")

            val connection = URL(url).openConnection() as HttpURLConnection
            connection.connectTimeout = 30000
            connection.readTimeout = 60000

            if (connection.responseCode == 200) {
                outputFile.parentFile?.mkdirs()

                // Extract .hgt file from ZIP
                ZipInputStream(connection.inputStream).use { zipInput ->
                    var entry = zipInput.nextEntry
                    while (entry != null) {
                        if (entry.name.endsWith(".hgt")) {
                            // Found the .hgt file - extract it
                            FileOutputStream(outputFile).use { output ->
                                zipInput.copyTo(output)
                            }
                            Log.d(TAG, " Downloaded and extracted ${outputFile.length() / 1024}KB")
                            return true
                        }
                        entry = zipInput.nextEntry
                    }
                }

                Log.e(TAG, " No .hgt file found in ZIP")
                false
            } else {
                Log.e(TAG, " HTTP ${connection.responseCode}")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, " Download failed: ${e.message}")
            false
        }
    }

    /**
     * Get cache file path for tile
     */
    private fun getTileCacheFile(tileName: String): File {
        val cacheDir = File(context.cacheDir, TILE_DIR)
        cacheDir.mkdirs()
        return File(cacheDir, "$tileName.hgt")
    }

    /**
     * Clear cache (for testing)
     */
    fun clearCache() {
        loadedTiles.clear()
        val cacheDir = File(context.cacheDir, TILE_DIR)
        cacheDir.deleteRecursively()
        Log.d(TAG, "Cache cleared")
    }
}

/**
 * SRTM Tile - represents 1 x 1 elevation data
 *
 * Format: .hgt file with 3601x3601 2-byte big-endian elevation samples
 */
private class SrtmTile(
    private val tileName: String,
    private val data: ShortArray,
    private val latMin: Int,
    private val lonMin: Int
) {
    companion object {
        private const val TAG = "SrtmTile"
        private const val SAMPLES = 3601 // SRTMGL1 30m resolution

        /**
         * Load tile from .hgt file (KISS - simple binary read)
         */
        fun fromFile(file: File, tileName: String): SrtmTile? {
            return try {
                val expectedSize = SAMPLES * SAMPLES * 2 // 2 bytes per sample
                if (file.length() != expectedSize.toLong()) {
                    Log.e(TAG, "Invalid file size: ${file.length()} (expected $expectedSize)")
                    return null
                }

                // Read binary data (big-endian signed 16-bit integers)
                val data = ShortArray(SAMPLES * SAMPLES)
                DataInputStream(FileInputStream(file)).use { input ->
                    for (i in data.indices) {
                        data[i] = input.readShort()
                    }
                }

                // Parse tile coordinates
                val latPrefix = tileName[0]
                val latValue = tileName.substring(1, 3).toInt()
                val lonPrefix = tileName[3]
                val lonValue = tileName.substring(4, 7).toInt()

                val latMin = if (latPrefix == 'N') latValue else -latValue
                val lonMin = if (lonPrefix == 'E') lonValue else -lonValue

                SrtmTile(tileName, data, latMin, lonMin)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load $tileName: ${e.message}")
                null
            }
        }
    }

    /**
     * Get elevation at lat/lon within this tile
     */
    fun getElevation(lat: Double, lon: Double): Double? {
        // Normalize to tile coordinates (0-1)
        val latNorm = lat - latMin
        val lonNorm = lon - lonMin

        // Convert to sample indices (0-1200)
        val row = ((1.0 - latNorm) * (SAMPLES - 1)).toInt() // Latitude is inverted in .hgt
        val col = (lonNorm * (SAMPLES - 1)).toInt()

        // Bounds check
        if (row < 0 || row >= SAMPLES || col < 0 || col >= SAMPLES) {
            return null
        }

        // Read elevation value
        val index = row * SAMPLES + col
        val elevation = data[index].toInt()

        // -32768 means "void" (no data)
        return if (elevation == -32768) null else elevation.toDouble()
    }
}
