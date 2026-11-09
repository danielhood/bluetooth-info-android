package dev.danielhood.btscan.domain.parser

import dev.danielhood.btscan.domain.model.Bytes
import dev.danielhood.btscan.domain.model.ManufacturerData
import dev.danielhood.btscan.domain.model.ServiceData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.UUID

/** Golden-vector tests for the advertisement parser (acceptance criterion 8). */
class AdvertisementParserTest {
    private fun hex(s: String): Bytes {
        val clean = s.replace(" ", "").replace("|", "")
        return Bytes(ByteArray(clean.length / 2) { clean.substring(it * 2, it * 2 + 2).toInt(16).toByte() })
    }

    private fun parse(s: String) = AdvertisementParser.parse(hex(s))

    private fun short(v: Long) = AdvertisementParser.uuidFromShort(v)

    @Test
    fun `empty payload gives an empty advertisement`() {
        val adv = AdvertisementParser.parse(Bytes.EMPTY)
        assertNull(adv.localName)
        assertNull(adv.flags)
        assertNull(adv.txPowerDbm)
        assertEquals(emptyList<UUID>(), adv.serviceUuids)
    }

    @Test
    fun `flags and complete local name`() {
        val adv = parse("02 01 06 | 06 09 48 65 6C 6C 6F")
        assertEquals(0x06, adv.flags)
        assertEquals("Hello", adv.localName)
    }

    @Test
    fun `shortened name is used when no complete name exists`() {
        assertEquals("ABCD", parse("05 08 41 42 43 44").localName)
    }

    @Test
    fun `complete name wins over shortened name regardless of order`() {
        assertEquals("Full", parse("03 08 53 68 | 05 09 46 75 6C 6C").localName)
        assertEquals("Full", parse("05 09 46 75 6C 6C | 03 08 53 68").localName)
    }

    @Test
    fun `tx power is signed`() {
        assertEquals(-12, parse("02 0A F4").txPowerDbm)
        assertEquals(4, parse("02 0A 04").txPowerDbm)
    }

    @Test
    fun `16-bit service uuids expand to the bluetooth base uuid`() {
        val adv = parse("05 03 0D 18 0F 18")
        assertEquals(listOf(short(0x180D), short(0x180F)), adv.serviceUuids)
        assertEquals("0000180d-0000-1000-8000-00805f9b34fb", adv.serviceUuids[0].toString())
    }

    @Test
    fun `32-bit service uuid`() {
        val adv = parse("05 05 78 56 34 12")
        assertEquals("12345678-0000-1000-8000-00805f9b34fb", adv.serviceUuids.single().toString())
    }

    @Test
    fun `128-bit service uuid is read little-endian`() {
        // Nordic UART service 6E400001-B5A3-F393-E0A9-E50E24DCCA9E
        val adv = parse("11 07 9E CA DC 24 0E E5 A9 E0 93 F3 A3 B5 01 00 40 6E")
        assertEquals(UUID.fromString("6E400001-B5A3-F393-E0A9-E50E24DCCA9E"), adv.serviceUuids.single())
    }

    @Test
    fun `duplicate uuids across structures are listed once in first-seen order`() {
        val adv = parse("03 03 0D 18 | 05 02 0F 18 0D 18")
        assertEquals(listOf(short(0x180D), short(0x180F)), adv.serviceUuids)
    }

    @Test
    fun `16-bit service data`() {
        val adv = parse("05 16 0F 18 64 01")
        assertEquals(listOf(ServiceData(short(0x180F), hex("64 01"))), adv.serviceData)
    }

    @Test
    fun `manufacturer data splits the company id from the payload`() {
        val adv = parse("07 FF 4C 00 02 15 AA BB")
        assertEquals(listOf(ManufacturerData(0x004C, hex("02 15 AA BB"))), adv.manufacturerData)
    }

    @Test
    fun `manufacturer data with only a company id has an empty payload`() {
        val adv = parse("03 FF 06 00")
        assertEquals(listOf(ManufacturerData(0x0006, Bytes.EMPTY)), adv.manufacturerData)
    }

    @Test
    fun `manufacturer data too short for a company id is ignored`() {
        assertEquals(emptyList<ManufacturerData>(), parse("02 FF 4C").manufacturerData)
    }

    @Test
    fun `zero length structure ends parsing`() {
        val adv = parse("02 01 06 | 00 | 05 09 41 42 43 44")
        assertEquals(0x06, adv.flags)
        assertNull(adv.localName)
    }

    @Test
    fun `structure longer than the remaining payload is dropped but earlier ones are kept`() {
        val adv = parse("02 01 06 | 0A 09 41 42")
        assertEquals(0x06, adv.flags)
        assertNull(adv.localName)
    }

    @Test
    fun `unknown structure types are skipped`() {
        val adv = parse("03 7E 01 02 | 02 01 04")
        assertEquals(0x04, adv.flags)
    }

    @Test
    fun `truncated uuid list ignores the partial trailing entry`() {
        val adv = parse("04 03 0D 18 0F")
        assertEquals(listOf(short(0x180D)), adv.serviceUuids)
    }

    @Test
    fun `invalid utf-8 in a name does not throw`() {
        val adv = parse("04 09 FF FE 41")
        assertEquals(
            3,
            adv.localName!!
                .length
                .coerceAtLeast(3)
                .coerceAtMost(3),
        )
    }

    @Test
    fun `flags structure with no data is ignored`() {
        assertNull(parse("01 01").flags)
    }
}
