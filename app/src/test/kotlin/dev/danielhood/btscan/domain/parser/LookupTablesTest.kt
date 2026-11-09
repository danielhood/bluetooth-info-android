package dev.danielhood.btscan.domain.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.UUID

class LookupTablesTest {
    @Test
    fun `known company ids resolve and unknown ones do not`() {
        assertEquals("Apple, Inc.", CompanyIds.nameOf(0x004C))
        assertEquals("Microsoft", CompanyIds.nameOf(0x0006))
        assertNull(CompanyIds.nameOf(0xFFFF))
    }

    @Test
    fun `standard 16-bit services resolve to friendly names`() {
        assertEquals("Heart Rate", ServiceUuids.nameOf(AdvertisementParser.uuidFromShort(0x180D)))
        assertEquals("Battery", ServiceUuids.nameOf(AdvertisementParser.uuidFromShort(0x180F)))
        assertNull(ServiceUuids.nameOf(AdvertisementParser.uuidFromShort(0xFE00)))
    }

    @Test
    fun `vendor services resolve by full uuid`() {
        val nus = UUID.fromString("6E400001-B5A3-F393-E0A9-E50E24DCCA9E")
        assertEquals("Nordic UART Service", ServiceUuids.nameOf(nus))
    }

    @Test
    fun `short form is recovered only for bluetooth base uuids`() {
        assertEquals(0x180D, ServiceUuids.shortForm(AdvertisementParser.uuidFromShort(0x180D)))
        assertNull(ServiceUuids.shortForm(UUID.fromString("6E400001-B5A3-F393-E0A9-E50E24DCCA9E")))
        assertNull(ServiceUuids.shortForm(AdvertisementParser.uuidFromShort(0x12345678)))
    }

    @Test
    fun `flags decode to their named bits`() {
        assertEquals(
            listOf(AdFlag.LE_GENERAL_DISCOVERABLE, AdFlag.BR_EDR_NOT_SUPPORTED),
            AdFlag.decode(0x06),
        )
        assertEquals(emptyList<AdFlag>(), AdFlag.decode(0))
        assertEquals(AdFlag.entries, AdFlag.decode(0x1F))
    }
}
