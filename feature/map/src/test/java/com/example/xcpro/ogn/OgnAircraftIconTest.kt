package com.example.xcpro.ogn

import com.example.xcpro.map.R
import org.junit.Assert.assertEquals
import org.junit.Test

class OgnAircraftIconTest {

    @Test
    fun knownTypeCodes_mapToExpectedIcons() {
        assertEquals(OgnAircraftIcon.Glider, iconForOgnAircraftTypeCode(1))
        assertEquals(OgnAircraftIcon.Tugplane, iconForOgnAircraftTypeCode(2))
        assertEquals(OgnAircraftIcon.Helicopter, iconForOgnAircraftTypeCode(3))
        assertEquals(OgnAircraftIcon.Paraglider, iconForOgnAircraftTypeCode(4))
        assertEquals(OgnAircraftIcon.Hangglider, iconForOgnAircraftTypeCode(5))
        assertEquals(OgnAircraftIcon.Balloon, iconForOgnAircraftTypeCode(6))
        assertEquals(OgnAircraftIcon.Uav, iconForOgnAircraftTypeCode(7))
        assertEquals(OgnAircraftIcon.StaticObject, iconForOgnAircraftTypeCode(8))
    }

    @Test
    fun unknownTypeCode_fallsBackToUnknownIcon() {
        assertEquals(OgnAircraftIcon.Unknown, iconForOgnAircraftTypeCode(null))
        assertEquals(OgnAircraftIcon.Unknown, iconForOgnAircraftTypeCode(99))
    }

    @Test
    fun tugCompetitionNumber_overridesTugIcon() {
        assertEquals(OgnAircraftIcon.TugplaneYellow, iconForOgnAircraftIdentity(2, "MRP"))
        assertEquals(OgnAircraftIcon.TugplaneWhite, iconForOgnAircraftIdentity(2, "FOO"))
        assertEquals(OgnAircraftIcon.TugplaneYellow, iconForOgnAircraftIdentity(2, " mrp "))
        assertEquals(OgnAircraftIcon.Tugplane, iconForOgnAircraftIdentity(2, "BAR"))
        assertEquals(OgnAircraftIcon.Tugplane, iconForOgnAircraftIdentity(2, null))
    }

    @Test
    fun nonTugCompetitionNumber_doesNotOverrideIconType() {
        assertEquals(OgnAircraftIcon.Glider, iconForOgnAircraftIdentity(1, "MRP"))
        assertEquals(OgnAircraftIcon.Unknown, iconForOgnAircraftIdentity(null, "FOO"))
    }

    @Test
    fun iconResources_matchExpectedAssets() {
        assertEquals(R.drawable.ic_adsb_glider, OgnAircraftIcon.Glider.resId)
        assertEquals(R.drawable.ic_ogn_redtug, OgnAircraftIcon.Tugplane.resId)
        assertEquals(R.drawable.ic_ogn_yellowtug, OgnAircraftIcon.TugplaneYellow.resId)
        assertEquals(R.drawable.ic_ogn_whitetug, OgnAircraftIcon.TugplaneWhite.resId)
        assertEquals(R.drawable.ic_adsb_helicopter, OgnAircraftIcon.Helicopter.resId)
        assertEquals(R.drawable.ic_ogn_hangglider, OgnAircraftIcon.Paraglider.resId)
        assertEquals(R.drawable.ic_ogn_hangglider, OgnAircraftIcon.Hangglider.resId)
        assertEquals(R.drawable.ic_adsb_balloon, OgnAircraftIcon.Balloon.resId)
        assertEquals(R.drawable.ic_adsb_drone, OgnAircraftIcon.Uav.resId)
        assertEquals(R.drawable.ic_ogn_static, OgnAircraftIcon.StaticObject.resId)
        assertEquals(R.drawable.ic_ogn_ufo, OgnAircraftIcon.Unknown.resId)
        assertEquals("ogn_icon_glider", OgnAircraftIcon.Glider.styleImageId)
        assertEquals("ogn_icon_tug", OgnAircraftIcon.Tugplane.styleImageId)
        assertEquals("ogn_icon_tug_yellow", OgnAircraftIcon.TugplaneYellow.styleImageId)
        assertEquals("ogn_icon_tug_white", OgnAircraftIcon.TugplaneWhite.styleImageId)
        assertEquals("ogn_icon_helicopter", OgnAircraftIcon.Helicopter.styleImageId)
        assertEquals("ogn_icon_paraglider", OgnAircraftIcon.Paraglider.styleImageId)
        assertEquals("ogn_icon_hangglider", OgnAircraftIcon.Hangglider.styleImageId)
        assertEquals("ogn_icon_balloon", OgnAircraftIcon.Balloon.styleImageId)
        assertEquals("ogn_icon_uav", OgnAircraftIcon.Uav.styleImageId)
        assertEquals("ogn_icon_static_object", OgnAircraftIcon.StaticObject.styleImageId)
        assertEquals("ogn_icon_unknown", OgnAircraftIcon.Unknown.styleImageId)
    }
}
