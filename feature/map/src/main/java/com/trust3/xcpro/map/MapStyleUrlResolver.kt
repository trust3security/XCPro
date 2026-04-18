package com.trust3.xcpro.map

object MapStyleUrlResolver {
    private const val MAPTILER_STYLE_URL_PREFIX = "https://api.maptiler.com/maps/"
    private const val MAPTILER_STYLE_URL_SUFFIX = "/style.json?key=nYDScLfnBm52GAc3jXEZ"

    fun resolve(styleName: String): String {
        val definition = MapStyleCatalog.definitionFor(styleName)
        return MAPTILER_STYLE_URL_PREFIX +
            definition.mapTilerStyleId +
            MAPTILER_STYLE_URL_SUFFIX
    }
}
