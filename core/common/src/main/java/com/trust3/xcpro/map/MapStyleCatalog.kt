package com.trust3.xcpro.map

data class MapStyleDefinition(
    val key: String,
    val label: String,
    val mapTilerStyleId: String,
    val selectable: Boolean
)

object MapStyleCatalog {
    const val TOPO = "Topo"
    const val SATELLITE = "Satellite"
    const val TERRAIN = "Terrain"
    const val THERMALLING_CONTRAST = "ThermallingContrast"

    private val definitions = linkedMapOf(
        TOPO to MapStyleDefinition(
            key = TOPO,
            label = TOPO,
            mapTilerStyleId = "topo",
            selectable = true
        ),
        SATELLITE to MapStyleDefinition(
            key = SATELLITE,
            label = SATELLITE,
            mapTilerStyleId = "hybrid",
            selectable = true
        ),
        TERRAIN to MapStyleDefinition(
            key = TERRAIN,
            label = TERRAIN,
            mapTilerStyleId = "outdoor",
            selectable = true
        ),
        THERMALLING_CONTRAST to MapStyleDefinition(
            key = THERMALLING_CONTRAST,
            label = "Thermalling Contrast",
            mapTilerStyleId = "dataviz-light",
            selectable = false
        )
    )

    val selectableDefinitions: List<MapStyleDefinition> =
        definitions.values.filter { definition -> definition.selectable }

    fun defaultSelectableKey(): String = TOPO

    fun normalizeStyleKey(styleKey: String?): String {
        val candidate = styleKey?.trim().orEmpty()
        val matched = definitions.keys.firstOrNull { key -> key.equals(candidate, ignoreCase = true) }
        return matched ?: defaultSelectableKey()
    }

    fun normalizeBaseStyleKey(styleKey: String?): String {
        val normalized = normalizeStyleKey(styleKey)
        return definitions[normalized]
            ?.takeIf { definition -> definition.selectable }
            ?.key
            ?: defaultSelectableKey()
    }

    fun definitionFor(styleKey: String?): MapStyleDefinition {
        return definitions.getValue(normalizeStyleKey(styleKey))
    }

    fun isSatellite(styleKey: String?): Boolean = normalizeStyleKey(styleKey) == SATELLITE

    fun isThermallingContrast(styleKey: String?): Boolean =
        normalizeStyleKey(styleKey) == THERMALLING_CONTRAST
}
