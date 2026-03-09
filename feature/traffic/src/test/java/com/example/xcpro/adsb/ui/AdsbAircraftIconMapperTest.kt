package com.example.xcpro.adsb.ui

import com.example.xcpro.traffic.R
import org.junit.Assert.assertEquals
import org.junit.Test

class AdsbAircraftIconMapperTest {

    @Test
    fun mapsHelicopterCategory() {
        assertEquals(
            AdsbAircraftIcon.Helicopter,
            iconForCategory(8)
        )
    }

    @Test
    fun mapsGliderCategory() {
        assertEquals(
            AdsbAircraftIcon.Glider,
            iconForCategory(9)
        )
    }

    @Test
    fun mapsLightAircraftCategories() {
        assertEquals(AdsbAircraftIcon.PlaneLight, iconForCategory(2))
        assertEquals(AdsbAircraftIcon.PlaneLight, iconForCategory(3))
    }

    @Test
    fun mapsMediumLargeAndHeavyAircraftCategories() {
        assertEquals(AdsbAircraftIcon.PlaneMedium, iconForCategory(4))
        assertEquals(AdsbAircraftIcon.PlaneLarge, iconForCategory(5))
        assertEquals(AdsbAircraftIcon.PlaneHeavy, iconForCategory(6))
    }

    @Test
    fun mapsHighPerformanceCategoryToLargePlane() {
        assertEquals(AdsbAircraftIcon.PlaneLarge, iconForCategory(7))
    }

    @Test
    fun mapsBalloonParachutistHanggliderAndDroneCategories() {
        assertEquals(AdsbAircraftIcon.Balloon, iconForCategory(10))
        assertEquals(AdsbAircraftIcon.Parachutist, iconForCategory(11))
        assertEquals(AdsbAircraftIcon.Hangglider, iconForCategory(12))
        assertEquals(AdsbAircraftIcon.Drone, iconForCategory(14))
    }

    @Test
    fun mapsNullOrUnsupportedToUnknown() {
        assertEquals(AdsbAircraftIcon.Unknown, iconForCategory(null))
        assertEquals(AdsbAircraftIcon.Unknown, iconForCategory(1))
        assertEquals(AdsbAircraftIcon.Unknown, iconForCategory(20))
    }

    @Test
    fun unresolvedAircraftClassificationStillEmitsUnknownSemanticIcon() {
        assertEquals(
            AdsbAircraftIcon.Unknown,
            iconForAircraft(
                category = 0,
                metadataTypecode = null,
                metadataIcaoAircraftType = null
            )
        )
    }

    @Test
    fun unknownSemanticIconUsesMediumPlaneVisualFallback() {
        assertEquals(
            R.drawable.ic_adsb_plane_medium,
            AdsbAircraftIcon.Unknown.resId
        )
    }

    @Test
    fun unknownSemanticIconRetainsUnknownStyleIdentity() {
        assertEquals(
            "adsb_icon_unknown",
            AdsbAircraftIcon.Unknown.styleImageId
        )
    }

    @Test
    fun labelsNoCategoryInfoForZeroAndOne() {
        assertEquals("No category information", openSkyCategoryLabel(0))
        assertEquals("No ADS-B category information", openSkyCategoryLabel(1))
    }

    @Test
    fun labelsKnownAndUnknownCategories() {
        assertEquals("Rotorcraft", openSkyCategoryLabel(8))
        assertEquals("Unknown", openSkyCategoryLabel(null))
        assertEquals("Unknown", openSkyCategoryLabel(19))
    }

    @Test
    fun usesIcaoAircraftTypeWhenTypecodeIsMissing() {
        assertEquals(
            AdsbAircraftIcon.Helicopter,
            iconForAircraft(
                category = 3,
                metadataTypecode = null,
                metadataIcaoAircraftType = "H1P"
            )
        )
    }

    @Test
    fun prefersHelicopterClassWhenTypecodeConflictsWithIcaoAircraftType() {
        assertEquals(
            AdsbAircraftIcon.Helicopter,
            iconForAircraft(
                category = 3,
                metadataTypecode = "C172",
                metadataIcaoAircraftType = "H1P"
            )
        )
    }

    @Test
    fun nonFixedWingCategoryIsAuthoritativeOverMetadata() {
        assertEquals(
            AdsbAircraftIcon.Helicopter,
            iconForAircraft(
                category = 8,
                metadataTypecode = "B738",
                metadataIcaoAircraftType = "L2J"
            )
        )
        assertEquals(
            AdsbAircraftIcon.Glider,
            iconForAircraft(
                category = 9,
                metadataTypecode = "B738",
                metadataIcaoAircraftType = "L2J"
            )
        )
    }

    @Test
    fun fallsBackToTypecodeHeuristicsWhenIcaoAircraftTypeMissing() {
        assertEquals(
            AdsbAircraftIcon.Glider,
            iconForAircraft(
                category = null,
                metadataTypecode = "ASW2",
                metadataIcaoAircraftType = null
            )
        )
    }

    @Test
    fun fallsBackToOpenSkyCategoryWhenMetadataDoesNotClassify() {
        assertEquals(
            AdsbAircraftIcon.Balloon,
            iconForAircraft(
                category = 10,
                metadataTypecode = null,
                metadataIcaoAircraftType = null
            )
        )
    }

    @Test
    fun classifiesTwinEngineJetClassAsTwinJetAircraft() {
        assertEquals(
            AdsbAircraftIcon.PlaneTwinJet,
            iconForAircraft(
                category = null,
                metadataTypecode = "B738",
                metadataIcaoAircraftType = "L2J"
            )
        )
    }

    @Test
    fun classifiesTwinEnginePistonClassAsTwinPropAircraft() {
        assertEquals(
            AdsbAircraftIcon.PlaneTwinProp,
            iconForAircraft(
                category = null,
                metadataTypecode = "BE58",
                metadataIcaoAircraftType = "L2P"
            )
        )
    }

    @Test
    fun classifiesTwinEngineTurbopropTypecodeAsTwinPropAircraft() {
        assertEquals(
            AdsbAircraftIcon.PlaneTwinProp,
            iconForAircraft(
                category = 0,
                metadataTypecode = "AT76",
                metadataIcaoAircraftType = null
            )
        )
    }

    @Test
    fun weakFallbackTypecodeDoesNotOverrideIcaoAircraftClass() {
        assertEquals(
            AdsbAircraftIcon.PlaneTwinProp,
            iconForAircraft(
                category = 0,
                metadataTypecode = "ZZ99",
                metadataIcaoAircraftType = "L2P"
            )
        )
    }

    @Test
    fun classifiesCommonHelicopterTypecodesWithoutIcaoClass() {
        assertEquals(
            AdsbAircraftIcon.Helicopter,
            iconForAircraft(
                category = 0,
                metadataTypecode = "B429",
                metadataIcaoAircraftType = null
            )
        )
        assertEquals(
            AdsbAircraftIcon.Helicopter,
            iconForAircraft(
                category = 0,
                metadataTypecode = "A139",
                metadataIcaoAircraftType = null
            )
        )
    }

    @Test
    fun classifiesAdditionalCommonHelicopterPrefixesWithoutIcaoClass() {
        val helicopterTypecodes = listOf(
            "B06",
            "AS50",
            "H269",
            "B407",
            "H500",
            "A109",
            "MI8",
            "S76",
            "B47G"
        )

        helicopterTypecodes.forEach { typecode ->
            assertEquals(
                AdsbAircraftIcon.Helicopter,
                iconForAircraft(
                    category = 0,
                    metadataTypecode = typecode,
                    metadataIcaoAircraftType = null
                )
            )
        }
    }

    @Test
    fun nonFixedWingCategoriesRemainAuthoritativeOverConflictingMetadata() {
        val conflictingTypecode = "B738"
        val conflictingIcaoClass = "L2J"

        assertEquals(
            AdsbAircraftIcon.Helicopter,
            iconForAircraft(8, conflictingTypecode, conflictingIcaoClass)
        )
        assertEquals(
            AdsbAircraftIcon.Glider,
            iconForAircraft(9, conflictingTypecode, conflictingIcaoClass)
        )
        assertEquals(
            AdsbAircraftIcon.Balloon,
            iconForAircraft(10, conflictingTypecode, conflictingIcaoClass)
        )
        assertEquals(
            AdsbAircraftIcon.Parachutist,
            iconForAircraft(11, conflictingTypecode, conflictingIcaoClass)
        )
        assertEquals(
            AdsbAircraftIcon.Hangglider,
            iconForAircraft(12, conflictingTypecode, conflictingIcaoClass)
        )
        assertEquals(
            AdsbAircraftIcon.Drone,
            iconForAircraft(14, conflictingTypecode, conflictingIcaoClass)
        )
    }

    @Test
    fun classifiesFourEngineJetClassAsHeavyAircraft() {
        assertEquals(
            AdsbAircraftIcon.PlaneHeavy,
            iconForAircraft(
                category = null,
                metadataTypecode = "A388",
                metadataIcaoAircraftType = "L4J"
            )
        )
    }

    @Test
    fun keepsLightIconForSmallJetClass() {
        assertEquals(
            AdsbAircraftIcon.PlaneLight,
            iconForAircraft(
                category = null,
                metadataTypecode = "VLJ1",
                metadataIcaoAircraftType = "L1J"
            )
        )
    }

    @Test
    fun prefersHeavyIconWhenCategoryIsHeavyEvenWithMetadata() {
        assertEquals(
            AdsbAircraftIcon.PlaneHeavy,
            iconForAircraft(
                category = 6,
                metadataTypecode = "A359",
                metadataIcaoAircraftType = "L2J"
            )
        )
    }

    @Test
    fun classifiesA359TypecodeAsTwinJetWhenCategoryMissing() {
        assertEquals(
            AdsbAircraftIcon.PlaneTwinJet,
            iconForAircraft(
                category = 0,
                metadataTypecode = "A359",
                metadataIcaoAircraftType = null
            )
        )
    }

    @Test
    fun classifiesC172TypecodeAsLightFixedWingWhenCategoryMissing() {
        assertEquals(
            AdsbAircraftIcon.PlaneLight,
            iconForAircraft(
                category = 0,
                metadataTypecode = "C172",
                metadataIcaoAircraftType = null
            )
        )
    }

    @Test
    fun classifiesB738TypecodeAsTwinJetWhenCategoryMissing() {
        assertEquals(
            AdsbAircraftIcon.PlaneTwinJet,
            iconForAircraft(
                category = 0,
                metadataTypecode = "B738",
                metadataIcaoAircraftType = null
            )
        )
    }

    @Test
    fun classifiesA388TypecodeAsHeavyWhenCategoryMissing() {
        assertEquals(
            AdsbAircraftIcon.PlaneHeavy,
            iconForAircraft(
                category = 0,
                metadataTypecode = "A388",
                metadataIcaoAircraftType = null
            )
        )
    }

    @Test
    fun prefersFourEngineTypecodeOverConflictingTwinJetClass() {
        assertEquals(
            AdsbAircraftIcon.PlaneHeavy,
            iconForAircraft(
                category = 0,
                metadataTypecode = "A388",
                metadataIcaoAircraftType = "L2J"
            )
        )
    }

    @Test
    fun appliesIcaoLargeIconOverrideForConfiguredIcao24() {
        assertEquals(
            AdsbAircraftIcon.PlaneLargeIcaoOverride,
            iconForAircraft(
                category = 2,
                metadataTypecode = "C172",
                metadataIcaoAircraftType = null,
                icao24Raw = "7C7C77"
            )
        )
        assertEquals(
            AdsbAircraftIcon.PlaneLargeIcaoOverride,
            iconForAircraft(
                category = 2,
                metadataTypecode = "A320",
                metadataIcaoAircraftType = null,
                icao24Raw = "7C6C90"
            )
        )
    }

    @Test
    fun doesNotApplyIcaoLargeOverrideWhenCategoryIsHeavy() {
        assertEquals(
            AdsbAircraftIcon.PlaneHeavy,
            iconForAircraft(
                category = 6,
                metadataTypecode = "A320",
                metadataIcaoAircraftType = null,
                icao24Raw = "7C7C77"
            )
        )
    }

    @Test
    fun doesNotApplyIcaoLargeOverrideWhenMetadataClassifiesHeavy() {
        assertEquals(
            AdsbAircraftIcon.PlaneHeavy,
            iconForAircraft(
                category = 2,
                metadataTypecode = "A388",
                metadataIcaoAircraftType = null,
                icao24Raw = "7C7C77"
            )
        )
    }

    @Test
    fun ignoresIcaoOverrideForNonConfiguredIcao24() {
        assertEquals(
            AdsbAircraftIcon.PlaneLight,
            iconForAircraft(
                category = 2,
                metadataTypecode = "C172",
                metadataIcaoAircraftType = null,
                icao24Raw = "7C7C78"
            )
        )
    }
}
