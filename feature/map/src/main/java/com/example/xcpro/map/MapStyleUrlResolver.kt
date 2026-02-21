package com.example.xcpro.map

object MapStyleUrlResolver {
    fun resolve(styleName: String): String {
        return when (styleName) {
            "Topo" -> "https://api.maptiler.com/maps/topo/style.json?key=nYDScLfnBm52GAc3jXEZ"
            "Satellite" -> "https://api.maptiler.com/maps/hybrid/style.json?key=nYDScLfnBm52GAc3jXEZ"
            "Terrain" -> "https://api.maptiler.com/maps/outdoor/style.json?key=nYDScLfnBm52GAc3jXEZ"
            else -> "https://api.maptiler.com/maps/outdoor/style.json?key=nYDScLfnBm52GAc3jXEZ"
        }
    }
}
