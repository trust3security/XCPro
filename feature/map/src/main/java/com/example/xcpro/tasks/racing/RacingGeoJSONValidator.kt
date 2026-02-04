package com.example.xcpro.tasks.racing

import kotlin.math.*

/**
 * Racing GeoJSON Validator - Prevents MapLibre native crashes
 *
 * Task Separation Compliant: Racing-only validation, no AAT imports
 *
 * This validator ensures all GeoJSON passed to MapLibre from Racing tasks
 * is properly formatted and contains valid coordinates to prevent native crashes.
 */
object RacingGeoJSONValidator {

    /**
     * Validate GeoJSON string before passing to MapLibre
     * Returns true if valid, false if malformed
     */
    fun validateGeoJSON(geoJsonString: String, context: String = "Racing GeoJSON"): Boolean {
        try {
            if (geoJsonString.isBlank()) {
                return false
            }

            // Basic JSON structure validation
            if (!geoJsonString.trim().startsWith("{") || !geoJsonString.trim().endsWith("}")) {
                return false
            }

            // Check for required GeoJSON properties
            if (!geoJsonString.contains("\"type\"") || !geoJsonString.contains("\"geometry\"")) {
                return false
            }

            // Validate coordinates for NaN/Infinity
            if (!validateCoordinates(geoJsonString, context)) {
                return false
            }

            return true

        } catch (e: Exception) {
            return false
        }
    }

    /**
     * Extract and validate all coordinates in GeoJSON
     */
    private fun validateCoordinates(geoJsonString: String, context: String): Boolean {
        try {
            // DEBUG: Print the GeoJSON string to see coordinate format

            // Extract coordinate arrays using regex - handle various number formats
            val coordinatePattern = Regex("""\[(-?\d+(?:\.\d+)?(?:[eE][+-]?\d+)?)\s*,\s*(-?\d+(?:\.\d+)?(?:[eE][+-]?\d+)?)\]""")
            val matches = coordinatePattern.findAll(geoJsonString)

            var coordinateCount = 0
            for (match in matches) {
                coordinateCount++
                val lon = match.groupValues[1].toDoubleOrNull()
                val lat = match.groupValues[2].toDoubleOrNull()

                if (lon == null || lat == null) {
                    return false
                }

                // Validate coordinate bounds and special values
                if (!isValidCoordinate(lat, lon, context)) {
                    return false
                }
            }

            if (coordinateCount == 0) {
                return false
            }

            return true

        } catch (e: Exception) {
            return false
        }
    }

    /**
     * Validate individual coordinate pair
     */
    private fun isValidCoordinate(lat: Double, lon: Double, context: String): Boolean {
        // Check for NaN and Infinity
        if (!lat.isFinite() || !lon.isFinite()) {
            return false
        }

        // Check latitude bounds (-90 to 90)
        if (lat < -90.0 || lat > 90.0) {
            return false
        }

        // Check longitude bounds (-180 to 180)
        if (lon < -180.0 || lon > 180.0) {
            return false
        }

        return true
    }

    /**
     * Safe GeoJSON creation with automatic validation
     * Returns validated GeoJSON or fallback if invalid
     */
    fun createValidatedGeoJSON(
        type: String,
        coordinates: List<Pair<Double, Double>>,
        properties: Map<String, Any> = emptyMap(),
        geometryType: String = "Polygon"
    ): String {
        try {
            // Validate all input coordinates first
            for (coord in coordinates) {
                if (!isValidCoordinate(coord.first, coord.second, "Generated coordinates")) {
                    return createFallbackGeoJSON(type)
                }
            }

            val coordinateString = coordinates.joinToString(",") { "[${it.second}, ${it.first}]" }
            val propertiesString = properties.entries.joinToString(",") { "\"${it.key}\": \"${it.value}\"" }

            val geoJson = when (geometryType) {
                "Point" -> {
                    if (coordinates.isNotEmpty()) {
                        val coord = coordinates.first()
                        """
                        {
                            "type": "Feature",
                            "geometry": {
                                "type": "Point",
                                "coordinates": [${coord.second}, ${coord.first}]
                            },
                            "properties": {$propertiesString}
                        }
                        """.trimIndent()
                    } else {
                        return createFallbackGeoJSON(type)
                    }
                }
                "Polygon" -> {
                    """
                    {
                        "type": "Feature",
                        "geometry": {
                            "type": "Polygon",
                            "coordinates": [[$coordinateString]]
                        },
                        "properties": {$propertiesString}
                    }
                    """.trimIndent()
                }
                else -> return createFallbackGeoJSON(type)
            }

            // Validate the generated GeoJSON
            return if (validateGeoJSON(geoJson, "Generated $type")) {
                geoJson
            } else {
                createFallbackGeoJSON(type)
            }

        } catch (e: Exception) {
            return createFallbackGeoJSON(type)
        }
    }

    /**
     * Create fallback GeoJSON for failed validation
     */
    private fun createFallbackGeoJSON(type: String): String {
        // Return a simple valid point at origin as fallback
        return """
        {
            "type": "Feature",
            "geometry": {
                "type": "Point",
                "coordinates": [0.0, 0.0]
            },
            "properties": {
                "type": "$type",
                "fallback": true,
                "error": "Invalid coordinates detected"
            }
        }
        """.trimIndent()
    }

    /**
     * Validate FeatureCollection GeoJSON
     */
    fun validateFeatureCollection(features: List<String>, context: String = "Racing FeatureCollection"): String? {
        try {
            // Validate each feature individually
            val validFeatures = mutableListOf<String>()

            for (feature in features) {
                if (validateGeoJSON(feature, "$context feature")) {
                    validFeatures.add(feature)
                } else {
                }
            }

            if (validFeatures.isEmpty()) {
                return null
            }

            val featureCollection = """
            {
                "type": "FeatureCollection",
                "features": [${validFeatures.joinToString(",")}]
            }
            """.trimIndent()

            return if (validateGeoJSON(featureCollection, context)) {
                featureCollection
            } else {
                null
            }

        } catch (e: Exception) {
            return null
        }
    }
}
