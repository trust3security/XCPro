package com.example.xcpro.adsb.metadata

import com.example.xcpro.adsb.metadata.data.AircraftMetadataCsvParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AircraftMetadataCsvParserTest {

    private val parser = AircraftMetadataCsvParser()

    @Test
    fun parseHeader_handlesBomAndCanonicalColumns() {
        val mapping = parser.parseHeader(
            "\uFEFFicao24,registration,typecode,model,manufacturer_name,owner,operator,operator_callsign,icao_aircraft_type"
        )

        val row = parser.parseRecord(
            rawLine = "abc123,VH-DFV,C208,\"CESSNA, 208 Caravan\",Textron,OwnerName,OpName,QFA,L1T",
            mapping = mapping,
            sourceRowOrder = 1L
        )

        requireNotNull(row)
        assertEquals("abc123", row.icao24)
        assertEquals("VH-DFV", row.registration)
        assertEquals("C208", row.typecode)
        assertEquals("CESSNA, 208 Caravan", row.model)
        assertEquals("Textron", row.manufacturerName)
        assertEquals("OwnerName", row.owner)
        assertEquals("OpName", row.operator)
        assertEquals("QFA", row.operatorCallsign)
        assertEquals("L1T", row.icaoAircraftType)
    }

    @Test
    fun parseHeader_supportsAliasColumns() {
        val mapping = parser.parseHeader(
            "icao_24,tail_number,type_code,aircraft_model,manufacturer,operator,operator_callsign"
        )

        val row = parser.parseRecord(
            rawLine = "def456,N123AB,B738,BOEING 737,BOEING,QANTAS,QFA",
            mapping = mapping,
            sourceRowOrder = 2L
        )

        requireNotNull(row)
        assertEquals("def456", row.icao24)
        assertEquals("N123AB", row.registration)
        assertEquals("B738", row.typecode)
        assertEquals("BOEING 737", row.model)
        assertEquals("BOEING", row.manufacturerName)
        assertEquals("QANTAS", row.operator)
        assertEquals("QFA", row.operatorCallsign)
    }

    @Test
    fun parseRecord_invalidIcaoReturnsNull() {
        val mapping = parser.parseHeader("icao24,registration")
        val row = parser.parseRecord(
            rawLine = "INVALID,N123AB",
            mapping = mapping,
            sourceRowOrder = 3L
        )
        assertNull(row)
    }
}

