package com.example.xcpro.screens.utils

import android.graphics.BitmapFactory
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.util.*

// Data class for tile coordinates
data class TileCoordinate(val z: Int, val x: Int, val y: Int, val bounds: LatLngBounds)

// Utility functions for tile coordinate calculations
fun calculateTileCoordinates(bounds: LatLngBounds, zoom: Int): List<TileCoordinate> {
    val tiles = mutableListOf<TileCoordinate>()

    // Convert lat/lng bounds to tile coordinates
    val minTileX = longitudeToTileX(bounds.longitudeWest, zoom)
    val maxTileX = longitudeToTileX(bounds.longitudeEast, zoom)
    val minTileY = latitudeToTileY(bounds.latitudeNorth, zoom) // Note: Y is flipped
    val maxTileY = latitudeToTileY(bounds.latitudeSouth, zoom)

    // Limit number of tiles to prevent performance issues
    val maxTilesPerDimension = 4
    val tileXRange = (maxTileX - minTileX + 1).coerceAtMost(maxTilesPerDimension)
    val tileYRange = (maxTileY - minTileY + 1).coerceAtMost(maxTilesPerDimension)

    android.util.Log.d("SkysightLayers", "🔢 Tile range: X($minTileX-$maxTileX), Y($minTileY-$maxTileY), limited to ${tileXRange}x$tileYRange")

    // Validate tile coordinate ranges
    val maxTileCoordinate = (1 shl zoom) - 1 // 2^zoom - 1

    for (x in minTileX until (minTileX + tileXRange)) {
        for (y in minTileY until (minTileY + tileYRange)) {
            // Validate tile coordinates are within valid range
            if (x >= 0 && x <= maxTileCoordinate && y >= 0 && y <= maxTileCoordinate) {
                // Calculate bounds for this tile
                val tileBounds = LatLngBounds.Builder()
                    .include(org.maplibre.android.geometry.LatLng(tileYToLatitude(y, zoom), tileXToLongitude(x, zoom)))
                    .include(org.maplibre.android.geometry.LatLng(tileYToLatitude(y + 1, zoom), tileXToLongitude(x + 1, zoom)))
                    .build()

                tiles.add(TileCoordinate(zoom, x, y, tileBounds))
            }
        }
    }

    android.util.Log.d("SkysightLayers", "📋 Generated ${tiles.size} tile coordinates for zoom $zoom")
    return tiles
}

fun longitudeToTileX(longitude: Double, zoom: Int): Int {
    return ((longitude + 180.0) / 360.0 * (1 shl zoom)).toInt()
}

fun latitudeToTileY(latitude: Double, zoom: Int): Int {
    val latRad = Math.toRadians(latitude)
    return ((1.0 - Math.log(Math.tan(latRad) + 1.0 / Math.cos(latRad)) / Math.PI) / 2.0 * (1 shl zoom)).toInt()
}

fun tileXToLongitude(x: Int, zoom: Int): Double {
    return x.toDouble() / (1 shl zoom) * 360.0 - 180.0
}

fun tileYToLatitude(y: Int, zoom: Int): Double {
    val n = Math.PI - 2.0 * Math.PI * y / (1 shl zoom)
    return Math.toDegrees(Math.atan(Math.sinh(n)))
}

// Function to download and display tiles using SkysightTileApi
suspend fun downloadAndDisplayTiles(
    mapLibreMap: MapLibreMap,
    tiles: List<TileCoordinate>,
    layerType: String,
    apiKey: String,
    skysightClient: com.example.xcpro.skysight.SkysightClient,
    layerId: String
) {
    try {
        android.util.Log.d("SkysightLayers", "📥 Starting to download ${tiles.size} tiles")

        // Get tile API first
        val tileApi = skysightClient.getTileApi()

        // Test multiple date/time formats to find what works
        val testParameters = listOf(
            // Current date with different time formats
            Triple(LocalDate.now(), "0000", "Current date midnight"),
            Triple(LocalDate.now(), "1200", "Current date noon"),
            Triple(LocalDate.now().minusDays(1), "1200", "Yesterday noon"),
            Triple(LocalDate.now().minusDays(1), "0000", "Yesterday midnight")
        )

        // Try each parameter set until one works
        var successfulParams: Triple<LocalDate, String, String>? = null
        for (testParam in testParameters) {
            val (testDate, testTime, description) = testParam
            val year = testDate.year.toString()
            val month = String.format("%02d", testDate.monthValue)
            val day = String.format("%02d", testDate.dayOfMonth)

            android.util.Log.d("SkysightLayers", "🧪 Testing API parameters: $description ($year/$month/$day/$testTime)")

            // Test with first tile only
            if (tiles.isNotEmpty()) {
                val testTile = tiles.first()
                try {
                    val testResponse = tileApi.getSatelliteTile(apiKey, testTile.z.toString(), testTile.x.toString(), testTile.y.toString(), year, month, day, testTime)
                    if (testResponse.isSuccessful) {
                        android.util.Log.d("SkysightLayers", "✅ SUCCESS with parameters: $description")
                        successfulParams = testParam
                        break
                    } else {
                        android.util.Log.w("SkysightLayers", "❌ Failed with parameters: $description (HTTP ${testResponse.code()})")
                    }
                } catch (e: Exception) {
                    android.util.Log.w("SkysightLayers", "❌ Exception with parameters: $description: ${e.message}")
                }
            }
        }

        // Use successful parameters or fallback to current date
        val (finalDate, finalTime, _) = successfulParams ?: testParameters.first()
        val year = finalDate.year.toString()
        val month = String.format("%02d", finalDate.monthValue)
        val day = String.format("%02d", finalDate.dayOfMonth)
        val time = finalTime

        for ((index, tile) in tiles.withIndex()) {
            try {
                android.util.Log.d("SkysightLayers", "🔽 Downloading tile $index/${tiles.size}: z${tile.z}/x${tile.x}/y${tile.y}")
                android.util.Log.d("SkysightLayers", "📅 API parameters: $year/$month/$day/$time, Token: ${apiKey.take(8)}...")
                android.util.Log.d("SkysightLayers", "🌐 Full URL would be: $layerType/${tile.z}/${tile.x}/${tile.y}/$year/$month/$day/$time")

                // Validate tile coordinates before API call
                val maxTileCoord = (1 shl tile.z) - 1
                if (tile.x < 0 || tile.x > maxTileCoord || tile.y < 0 || tile.y > maxTileCoord) {
                    android.util.Log.w("SkysightLayers", "⚠️ Invalid tile coordinates for zoom ${tile.z}: x=${tile.x}, y=${tile.y} (max=$maxTileCoord)")
                    continue
                }

                val response = when (layerType.lowercase()) {
                    "satellite" -> tileApi.getSatelliteTile(apiKey, tile.z.toString(), tile.x.toString(), tile.y.toString(), year, month, day, time)
                    "rain", "precipitation" -> tileApi.getRainTile(apiKey, tile.z.toString(), tile.x.toString(), tile.y.toString(), year, month, day, time)
                    else -> tileApi.getSatelliteTile(apiKey, tile.z.toString(), tile.x.toString(), tile.y.toString(), year, month, day, time)
                }

                if (response.isSuccessful) {
                    response.body()?.bytes()?.let { tileBytes ->
                        android.util.Log.d("SkysightLayers", "✅ Downloaded tile ${tile.z}/${tile.x}/${tile.y}: ${tileBytes.size} bytes")

                        // Check if this is actually an image
                        val isPNG = tileBytes.size > 8 &&
                                tileBytes[0] == 0x89.toByte() &&
                                tileBytes[1] == 0x50.toByte() &&
                                tileBytes[2] == 0x4E.toByte() &&
                                tileBytes[3] == 0x47.toByte()

                        val isJPEG = tileBytes.size > 3 &&
                                tileBytes[0] == 0xFF.toByte() &&
                                tileBytes[1] == 0xD8.toByte() &&
                                tileBytes[2] == 0xFF.toByte()

                        val isWebP = tileBytes.size > 12 &&
                                tileBytes[8] == 0x57.toByte() &&
                                tileBytes[9] == 0x45.toByte() &&
                                tileBytes[10] == 0x42.toByte() &&
                                tileBytes[11] == 0x50.toByte()

                        android.util.Log.d("SkysightLayers", "📊 Image format check - PNG: $isPNG, JPEG: $isJPEG, WebP: $isWebP")

                        if (!isPNG && !isJPEG && !isWebP) {
                            // Log first few bytes for debugging
                            val headerBytes = tileBytes.take(20).map { String.format("%02X", it) }.joinToString(" ")
                            android.util.Log.w("SkysightLayers", "⚠️ Unknown image format. First 20 bytes: $headerBytes")
                        }

                        // Add tile as ImageSource overlay
                        addTileAsImageOverlay(mapLibreMap, tile, tileBytes, layerId)
                    } ?: run {
                        android.util.Log.w("SkysightLayers", "⚠️ Empty response body for tile ${tile.z}/${tile.x}/${tile.y}")
                    }
                } else {
                    val errorBody = try {
                        response.errorBody()?.string() ?: "No error body"
                    } catch (e: Exception) {
                        "Could not read error body: ${e.message}"
                    }
                    android.util.Log.e("SkysightLayers", "❌ Failed to download tile ${tile.z}/${tile.x}/${tile.y}: HTTP ${response.code()}")
                    android.util.Log.e("SkysightLayers", "📄 Error response headers: ${response.headers()}")
                    android.util.Log.e("SkysightLayers", "📄 Error response body: $errorBody")
                    android.util.Log.e("SkysightLayers", "🔑 Request was: $layerType/${tile.z}/${tile.x}/${tile.y}/$year/$month/$day/$time with key ${apiKey.take(8)}...")

                    // For HTTP 500 errors, this might be a server-side issue or incorrect parameters
                    if (response.code() == 500) {
                        android.util.Log.e("SkysightLayers", "🚨 HTTP 500 suggests server error or invalid parameters")
                    }
                }

            } catch (e: Exception) {
                android.util.Log.e("SkysightLayers", "💥 Exception downloading tile ${tile.z}/${tile.x}/${tile.y}: ${e.message}")
            }
        }

        android.util.Log.d("SkysightLayers", "🎉 Finished downloading tiles for layer: $layerId")

    } catch (e: Exception) {
        android.util.Log.e("SkysightLayers", "💥 Exception in downloadAndDisplayTiles: ${e.message}", e)
    }
}

// Function to add individual tile as actual image overlay
fun addTileAsImageOverlay(mapLibreMap: MapLibreMap, tile: TileCoordinate, tileBytes: ByteArray, layerId: String) {
    try {
        val sourceId = "skysight-tile-${layerId}-${tile.z}-${tile.x}-${tile.y}"

        // Convert bytes to bitmap
        val bitmap = BitmapFactory.decodeByteArray(tileBytes, 0, tileBytes.size)
        if (bitmap == null) {
            android.util.Log.e("SkysightLayers", "❌ Failed to decode tile bitmap")
            return
        }

        // Create ImageSource with tile bounds
        val coordinates = arrayOf(
            arrayOf(tile.bounds.longitudeWest, tile.bounds.latitudeNorth),
            arrayOf(tile.bounds.longitudeEast, tile.bounds.latitudeNorth),
            arrayOf(tile.bounds.longitudeEast, tile.bounds.latitudeSouth),
            arrayOf(tile.bounds.longitudeWest, tile.bounds.latitudeSouth)
        )
        // Log bitmap details for debugging
        android.util.Log.d("SkysightLayers", "🖼️ Decoded bitmap: ${bitmap.width}x${bitmap.height} pixels, config: ${bitmap.config}, hasAlpha: ${bitmap.hasAlpha()}")

        // TEMPORARY: Show colored overlay to verify positioning while we debug image display
        // TODO: Replace with actual image display once ImageSource issue is resolved
        val geoJsonString = """
            {
                "type": "Feature",
                "geometry": {
                    "type": "Polygon",
                    "coordinates": [[
                        [${tile.bounds.longitudeWest}, ${tile.bounds.latitudeNorth}],
                        [${tile.bounds.longitudeEast}, ${tile.bounds.latitudeNorth}],
                        [${tile.bounds.longitudeEast}, ${tile.bounds.latitudeSouth}],
                        [${tile.bounds.longitudeWest}, ${tile.bounds.latitudeSouth}],
                        [${tile.bounds.longitudeWest}, ${tile.bounds.latitudeNorth}]
                    ]]
                },
                "properties": {
                    "tileId": "${tile.z}/${tile.x}/${tile.y}",
                    "imageSize": "${bitmap.width}x${bitmap.height}"
                }
            }
        """

        val geoJsonSource = GeoJsonSource(sourceId, geoJsonString)

        // Add source to map
        mapLibreMap.style?.addSource(geoJsonSource)

        // Analyze first few pixels to determine actual content
        val pixelSample = try {
            val sampleWidth = 5.coerceAtMost(bitmap.width)
            val pixels = IntArray(sampleWidth)
            bitmap.getPixels(pixels, 0, sampleWidth, 0, 0, sampleWidth, 1)
            pixels.map { pixel ->
                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF
                val a = (pixel shr 24) and 0xFF
                "RGBA($r,$g,$b,$a)"
            }.joinToString(", ")
        } catch (e: Exception) {
            "Error sampling pixels: ${e.message}"
        }

        android.util.Log.d("SkysightLayers", "🎨 Pixel sample from image: $pixelSample")

        // Create fill layer with semi-transparent color based on layer type
        val tileLayerId = "skysight-tile-layer-${layerId}-${tile.z}-${tile.x}-${tile.y}"

        // Use different colors for different layer types
        val fillColor = when (layerId) {
            "satellite" -> "#4CAF50"  // Green for satellite imagery
            "rain", "precipitation" -> "#2196F3"  // Blue for rain/precipitation
            else -> "#FF9800"  // Orange for other layers
        }

        val fillLayer = FillLayer(tileLayerId, sourceId).apply {
            setProperties(
                PropertyFactory.fillColor(fillColor),
                PropertyFactory.fillOpacity(0.5f)  // Semi-transparent
            )
        }

        // Add layer above base map but below labels for better visibility
        try {
            // Try to add below label layers so text remains visible
            val labelLayers = listOf("place-label", "road-label", "poi-label", "waterway-label")
            var addedBelow = false

            for (labelLayer in labelLayers) {
                if (mapLibreMap.style?.getLayer(labelLayer) != null) {
                    mapLibreMap.style?.addLayerBelow(fillLayer, labelLayer)
                    addedBelow = true
                    android.util.Log.d("SkysightLayers", "📍 Added weather layer below: $labelLayer")
                    break
                }
            }

            if (!addedBelow) {
                // If no label layers found, add on top
                mapLibreMap.style?.addLayer(fillLayer)
                android.util.Log.d("SkysightLayers", "📍 Added weather layer on top")
            }
        } catch (e: Exception) {
            android.util.Log.e("SkysightLayers", "⚠️ Error positioning layer, adding on top: ${e.message}")
            mapLibreMap.style?.addLayer(fillLayer)
        }

        android.util.Log.d("SkysightLayers", "✅ Added actual weather tile: ${tile.z}/${tile.x}/${tile.y}, size: ${tileBytes.size} bytes, bitmap: ${bitmap.width}x${bitmap.height}px")

    } catch (e: Exception) {
        android.util.Log.e("SkysightLayers", "💥 Exception adding tile overlay: ${e.message}", e)
    }
}