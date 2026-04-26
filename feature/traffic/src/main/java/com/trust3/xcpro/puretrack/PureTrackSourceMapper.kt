package com.trust3.xcpro.puretrack

class PureTrackSourceMapper {
    fun map(id: Int?): PureTrackSourceType? = id?.let(KNOWN_SOURCES::get)

    private companion object {
        private val KNOWN_SOURCES = listOf(
            PureTrackSourceType(0, "flarm"),
            PureTrackSourceType(1, "spot"),
            PureTrackSourceType(9, "inreach"),
            PureTrackSourceType(12, "adsb"),
            PureTrackSourceType(16, "puretrack"),
            PureTrackSourceType(18, "celltracker"),
            PureTrackSourceType(23, "xcontest"),
            PureTrackSourceType(24, "skylines"),
            PureTrackSourceType(26, "livegliding"),
            PureTrackSourceType(27, "ADSBExchange"),
            PureTrackSourceType(28, "adsb.lol"),
            PureTrackSourceType(29, "adsb.fi"),
            PureTrackSourceType(34, "Tracker App"),
            PureTrackSourceType(35, "OGN ICAO"),
            PureTrackSourceType(36, "XC Guide"),
            PureTrackSourceType(42, "Gaggle"),
            PureTrackSourceType(43, "Wingman"),
            PureTrackSourceType(45, "airplanes.live"),
            PureTrackSourceType(46, "ADSB"),
            PureTrackSourceType(47, "XCglobe/FlyMe"),
            PureTrackSourceType(50, "Naviter Omni"),
            PureTrackSourceType(51, "Garmin Watch")
        ).associateBy { it.id }
    }
}
