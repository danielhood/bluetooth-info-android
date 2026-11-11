package dev.danielhood.btscan.domain.decode

import dev.danielhood.btscan.domain.decode.decoders.TrackerHintDecoder
import dev.danielhood.btscan.domain.model.Advertisement
import dev.danielhood.btscan.domain.model.ManufacturerData
import dev.danielhood.btscan.domain.model.ServiceData
import dev.danielhood.btscan.domain.parser.AdvertisementParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Inputs come from published research, not vendor specifications: Apple type 0x12 (furiousMAC/continuity), Tile
 * UUID 0xFEED (arXiv 2510.00350), Samsung SmartTag UUIDs 0xFD5A and 0xFD59 (arXiv 2210.14702).
 */
class TrackerHintDecoderTest {
    private fun families(ad: Advertisement) =
        TrackerHintDecoder
            .decode(ad)
            .flatMap { it.fields }
            .map { (it.value as FieldValue.Label).value }

    private fun uuid(v: Long) = AdvertisementParser.uuidFromShort(v)

    @Test
    fun `apple find my message gives a cautious unofficial hint`() {
        val ad = manufacturer(0x004C, "12 01 00")
        val d = TrackerHintDecoder.decode(ad).single()
        assertEquals(DecoderId.TRACKER_HINT, d.decoder)
        assertEquals(Confidence.UNOFFICIAL_HINT, d.confidence)
        assertEquals(listOf(LabelKey.TRACKER_FIND_MY), families(ad))
    }

    @Test
    fun `tile uuid in the service list or service data is a hint`() {
        assertEquals(listOf(LabelKey.TRACKER_TILE), families(Advertisement(serviceUuids = listOf(uuid(0xFEED)))))
        assertEquals(
            listOf(LabelKey.TRACKER_TILE),
            families(Advertisement(serviceData = listOf(ServiceData(uuid(0xFEED), hex("0102"))))),
        )
    }

    @Test
    fun `samsung smarttag uuids are a hint`() {
        assertEquals(listOf(LabelKey.TRACKER_SMARTTAG), families(Advertisement(serviceUuids = listOf(uuid(0xFD5A)))))
        assertEquals(listOf(LabelKey.TRACKER_SMARTTAG), families(Advertisement(serviceUuids = listOf(uuid(0xFD59)))))
    }

    @Test
    fun `other apple messages and unrelated services give no hint`() {
        assertTrue(TrackerHintDecoder.decode(manufacturer(0x004C, "05 01 00")).isEmpty())
        assertTrue(TrackerHintDecoder.decode(Advertisement(serviceUuids = listOf(uuid(0x180F)))).isEmpty())
        assertTrue(
            TrackerHintDecoder
                .decode(
                    Advertisement(manufacturerData = listOf(ManufacturerData(0x0059, hex("12 01 00")))),
                ).isEmpty(),
        )
        assertTrue(TrackerHintDecoder.decode(Advertisement()).isEmpty())
    }
}
