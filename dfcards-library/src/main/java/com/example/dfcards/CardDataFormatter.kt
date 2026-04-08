package com.example.dfcards

import com.example.xcpro.common.units.UnitsPreferences
import com.example.xcpro.core.flight.RealTimeFlightData

internal object CardDataFormatter {
    private val defaultTimeFormatter = SystemCardTimeFormatter()
    private val defaultStrings = DefaultCardStrings()

    fun mapLiveDataToCard(
        cardId: CardId,
        liveData: RealTimeFlightData?,
        units: UnitsPreferences = UnitsPreferences(),
        strings: CardStrings = defaultStrings,
        timeFormatter: CardTimeFormatter = defaultTimeFormatter
    ): Pair<String, String?> =
        mapLiveDataToCard(cardId.raw, liveData, units, strings, timeFormatter)

    fun mapLiveDataToCard(
        cardId: String,
        liveData: RealTimeFlightData?,
        units: UnitsPreferences = UnitsPreferences(),
        strings: CardStrings = defaultStrings,
        timeFormatter: CardTimeFormatter = defaultTimeFormatter
    ): Pair<String, String?> {
        val knownId = KnownCardId.fromRaw(cardId)
        if (liveData == null) {
            if (knownId == KnownCardId.HAWK_VARIO) {
                return Pair("--.- m/s", "ACCEL UNREL BARO DEG CONF --")
            }
            return Pair(placeholderFor(knownId, units, strings), strings.noData)
        }
        if (knownId == null) {
            return Pair("--", strings.unknown)
        }
        val spec = CardFormatSpecs.specs[knownId] ?: return Pair("--", strings.unknown)
        return spec.format(liveData, units, strings, timeFormatter)
    }
}
