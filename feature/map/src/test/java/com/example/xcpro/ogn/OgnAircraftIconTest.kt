package com.example.xcpro.ogn

import com.example.xcpro.map.R
import org.junit.Assert.assertEquals
import org.junit.Test

class OgnAircraftIconTest {

    @Test
    fun tugTypeCode_usesTugplaneIcon() {
        assertEquals(
            OgnAircraftIcon.Tugplane,
            iconForOgnAircraftTypeCode(2)
        )
    }

    @Test
    fun hangGliderTypeCode_usesHanggliderIcon() {
        assertEquals(
            OgnAircraftIcon.Hangglider,
            iconForOgnAircraftTypeCode(6)
        )
    }

    @Test
    fun paragliderTypeCode_usesHanggliderIcon() {
        assertEquals(
            OgnAircraftIcon.Hangglider,
            iconForOgnAircraftTypeCode(7)
        )
    }

    @Test
    fun unknownTypeCode_fallsBackToGliderIcon() {
        assertEquals(OgnAircraftIcon.Glider, iconForOgnAircraftTypeCode(null))
        assertEquals(OgnAircraftIcon.Glider, iconForOgnAircraftTypeCode(1))
        assertEquals(OgnAircraftIcon.Glider, iconForOgnAircraftTypeCode(99))
    }

    @Test
    fun iconResources_matchExpectedAssets() {
        assertEquals(R.drawable.ic_adsb_glider, OgnAircraftIcon.Glider.resId)
        assertEquals(R.drawable.ic_ogn_tug, OgnAircraftIcon.Tugplane.resId)
        assertEquals(R.drawable.ic_ogn_hangglider, OgnAircraftIcon.Hangglider.resId)
        assertEquals("ogn_icon_glider", OgnAircraftIcon.Glider.styleImageId)
        assertEquals("ogn_icon_tug", OgnAircraftIcon.Tugplane.styleImageId)
        assertEquals("ogn_icon_hangglider", OgnAircraftIcon.Hangglider.styleImageId)
    }
}
