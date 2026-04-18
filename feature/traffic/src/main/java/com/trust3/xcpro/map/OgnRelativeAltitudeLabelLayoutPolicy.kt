package com.trust3.xcpro.map

data class OgnRelativeAltitudeLabelLayout(
    val deltaOnTop: Boolean
)

object OgnRelativeAltitudeLabelLayoutPolicy {
    fun resolve(band: OgnRelativeAltitudeBand): OgnRelativeAltitudeLabelLayout {
        return when (band) {
            OgnRelativeAltitudeBand.ABOVE -> OgnRelativeAltitudeLabelLayout(deltaOnTop = true)
            OgnRelativeAltitudeBand.BELOW -> OgnRelativeAltitudeLabelLayout(deltaOnTop = false)
            OgnRelativeAltitudeBand.NEAR,
            OgnRelativeAltitudeBand.UNKNOWN -> OgnRelativeAltitudeLabelLayout(deltaOnTop = true)
        }
    }
}
