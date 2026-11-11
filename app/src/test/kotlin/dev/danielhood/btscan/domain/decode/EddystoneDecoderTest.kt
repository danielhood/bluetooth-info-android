package dev.danielhood.btscan.domain.decode

import dev.danielhood.btscan.domain.decode.decoders.EddystoneDecoder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Vectors are constructed from the Eddystone protocol specification (github.com/google/eddystone): frame types
 * 0x00 UID, 0x10 URL, 0x20 TLM, 0x30 EID; URL scheme and expansion tables; TLM temperature as signed 8.8 fixed point.
 */
class EddystoneDecoderTest {
    private fun decode(payload: String) = EddystoneDecoder.decode(serviceData(0xFEAA, payload))

    @Test
    fun `uid frame gives namespace instance and calibrated power`() {
        val d = decode("00 EB 00112233445566778899 AABBCCDDEEFF 0000").single()
        assertEquals(DecoderId.EDDYSTONE_UID, d.decoder)
        assertEquals(Confidence.SPECIFICATION, d.confidence)
        assertEquals(-21.0, d.number(FieldKey.TX_POWER_0M_DBM), 0.0)
        assertEquals("00112233445566778899", d.hexOf(FieldKey.EDDYSTONE_NAMESPACE))
        assertEquals("AABBCCDDEEFF", d.hexOf(FieldKey.EDDYSTONE_INSTANCE))
    }

    @Test
    fun `url frame expands the scheme and the suffix code`() {
        val d = decode("10 EB 03 6578616D706C65 00").single()
        assertEquals(DecoderId.EDDYSTONE_URL, d.decoder)
        assertEquals("https://example.com/", d.text(FieldKey.EDDYSTONE_URL))
    }

    @Test
    fun `url frame matches the specification example for http www google com`() {
        // Specification table: scheme 0x00 "http://www." + "google" + 0x07 ".com".
        assertEquals("http://www.google.com", decode("10 EB 00 676F6F676C65 07").single().text(FieldKey.EDDYSTONE_URL))
    }

    @Test
    fun `every expansion code maps to its suffix`() {
        val suffixes =
            listOf(
                ".com/",
                ".org/",
                ".edu/",
                ".net/",
                ".info/",
                ".biz/",
                ".gov/",
                ".com",
                ".org",
                ".edu",
                ".net",
                ".info",
                ".biz",
                ".gov",
            )
        suffixes.forEachIndexed { code, suffix ->
            val d = decode("10 EB 02 61 %02X".format(code)).single()
            assertEquals("http://a$suffix", d.text(FieldKey.EDDYSTONE_URL))
        }
    }

    @Test
    fun `url with a reserved byte or unknown scheme is rejected`() {
        assertTrue(decode("10 EB 03 61 0E").isEmpty())
        assertTrue(decode("10 EB 03 61 20").isEmpty())
        assertTrue(decode("10 EB 03 61 7F").isEmpty())
        assertTrue(decode("10 EB 04 61").isEmpty())
    }

    @Test
    fun `tlm frame gives battery temperature counts and uptime`() {
        val d = decode("20 00 0BB8 1880 000003E8 00008CA0").single()
        assertEquals(DecoderId.EDDYSTONE_TLM, d.decoder)
        assertEquals(3000.0, d.number(FieldKey.BATTERY_MV), 0.0)
        assertEquals(24.5, d.number(FieldKey.TEMPERATURE_C), 0.0)
        assertEquals(1000.0, d.number(FieldKey.ADVERTISEMENT_COUNT), 0.0)
        assertEquals(3600.0, d.number(FieldKey.UPTIME_SECONDS), 0.0)
    }

    @Test
    fun `tlm negative temperature uses the sign`() {
        assertEquals(-25.0, decode("20 00 0BB8 E700 00000000 00000000").single().number(FieldKey.TEMPERATURE_C), 0.0)
    }

    @Test
    fun `tlm unsupported voltage and temperature are omitted`() {
        val d = decode("20 00 0000 8000 00000001 00000002").single()
        assertNull(d.field(FieldKey.BATTERY_MV))
        assertNull(d.field(FieldKey.TEMPERATURE_C))
        assertEquals(1.0, d.number(FieldKey.ADVERTISEMENT_COUNT), 0.0)
    }

    @Test
    fun `encrypted tlm is flagged and carries no values`() {
        val d = decode("20 01 000102030405060708090A0B 0C0D0E0F").single()
        assertEquals(Confidence.ENCRYPTED, d.confidence)
        assertTrue(d.fields.isEmpty())
    }

    @Test
    fun `eid frame is opaque and marked rotating`() {
        val d = decode("30 EB 0123456789ABCDEF").single()
        assertEquals(DecoderId.EDDYSTONE_EID, d.decoder)
        assertEquals(Confidence.ENCRYPTED, d.confidence)
        assertEquals("0123456789ABCDEF", d.hexOf(FieldKey.EDDYSTONE_EID))
    }

    @Test
    fun `unknown frame types and other services are ignored`() {
        assertTrue(decode("40 00 00").isEmpty())
        assertTrue(
            EddystoneDecoder.decode(serviceData(0xFCD2, "00 EB 00112233445566778899 AABBCCDDEEFF 0000")).isEmpty(),
        )
    }

    @Test
    fun `truncated frames decode to nothing`() {
        val vectors =
            listOf(
                "00 EB 00112233445566778899 AABBCCDDEEFF 0000",
                "10 EB 03 6578616D706C65 00",
                "20 00 0BB8 1880 000003E8 00008CA0",
                "30 EB 0123456789ABCDEF",
            )
        vectors.forEach { v ->
            val bytes = hex(v).toByteArray()
            for (n in 0 until bytes.size) {
                val prefix = bytes.copyOf(n).joinToString("") { "%02X".format(it) }
                EddystoneDecoder.decode(serviceData(0xFEAA, prefix)) // must not throw
            }
        }
        assertTrue(decode("").isEmpty())
        assertTrue(decode("00 EB 0011").isEmpty())
        assertTrue(decode("20 00 0BB8").isEmpty())
        assertTrue(decode("30 EB 01").isEmpty())
    }
}
