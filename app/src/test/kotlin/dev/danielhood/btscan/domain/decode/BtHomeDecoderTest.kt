package dev.danielhood.btscan.domain.decode

import dev.danielhood.btscan.domain.decode.decoders.BtHomeDecoder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Vectors come from https://bthome.io/format/ : the full example `40 02 C4 09 03 BF 13` (25.00 C, 50.55 %) and the
 * per-object examples in its sensor table (for example `02 CA 09` = 25.06 C, `04 13 8A 01` = 1008.83 hPa).
 */
class BtHomeDecoderTest {
    private fun decode(payload: String) = BtHomeDecoder.decode(serviceData(0xFCD2, payload))

    private fun single(payload: String, key: FieldKey): Double = decode(payload).single().number(key)

    @Test
    fun `specification example decodes temperature and humidity`() {
        val d = decode("40 02C409 03BF13").single()
        assertEquals(DecoderId.BTHOME, d.decoder)
        assertEquals(Confidence.SPECIFICATION, d.confidence)
        assertEquals(listOf(FieldKey.TEMPERATURE_C, FieldKey.HUMIDITY_PERCENT), d.fields.map { it.key })
        assertEquals(25.0, d.number(FieldKey.TEMPERATURE_C), 1e-9)
        assertEquals(50.55, d.number(FieldKey.HUMIDITY_PERCENT), 1e-9)
    }

    @Test
    fun `sensor table examples decode to the printed results`() {
        assertEquals(97.0, single("40 0161", FieldKey.BATTERY_PERCENT), 0.0)
        assertEquals(25.06, single("40 02CA09", FieldKey.TEMPERATURE_C), 1e-9)
        assertEquals(1008.83, single("40 04138A01", FieldKey.PRESSURE_HPA), 1e-9)
        assertEquals(3.074, single("40 0C020C", FieldKey.VOLTAGE_V), 1e-9)
        assertEquals(3090.0, single("40 0D120C", FieldKey.PM25), 0.0)
        assertEquals(7170.0, single("40 0E021C", FieldKey.PM10), 0.0)
        assertEquals(1250.0, single("40 12E204", FieldKey.CO2_PPM), 0.0)
        assertEquals(307.0, single("40 133301", FieldKey.TVOC), 0.0)
        assertEquals(35.0, single("40 2E23", FieldKey.HUMIDITY_PERCENT), 0.0)
        assertEquals(27.3, single("40 451101", FieldKey.TEMPERATURE_C), 1e-9)
        assertEquals(9.0, single("40 0009", FieldKey.PACKET_ID), 0.0)
    }

    @Test
    fun `negative temperature uses the sign`() {
        assertEquals(-5.0, single("40 020CFE", FieldKey.TEMPERATURE_C), 1e-9)
    }

    @Test
    fun `several objects in ascending order all decode`() {
        val d = decode("40 0009 0161 02CA09 03BF13 04138A01").single()
        assertEquals(
            listOf(
                FieldKey.PACKET_ID,
                FieldKey.BATTERY_PERCENT,
                FieldKey.TEMPERATURE_C,
                FieldKey.HUMIDITY_PERCENT,
                FieldKey.PRESSURE_HPA,
            ),
            d.fields.map { it.key },
        )
    }

    @Test
    fun `encrypted frames are flagged and carry no values`() {
        val d = decode("41 A1B2C3D4E5F60718 00000001 DEADBEEF").single()
        assertEquals(Confidence.ENCRYPTED, d.confidence)
        assertTrue(d.fields.isEmpty())
    }

    @Test
    fun `parsing stops at an unknown object and keeps what was read`() {
        val d = decode("40 0161 FE0102 02CA09").single()
        assertEquals(listOf(FieldKey.BATTERY_PERCENT), d.fields.map { it.key })
    }

    @Test
    fun `a truncated last object is dropped without error`() {
        val d = decode("40 0161 02CA").single()
        assertEquals(listOf(FieldKey.BATTERY_PERCENT), d.fields.map { it.key })
    }

    @Test
    fun `wrong version, empty and object-less frames decode to nothing`() {
        assertTrue(decode("20 0161").isEmpty())
        assertTrue(decode("00 0161").isEmpty())
        assertTrue(decode("").isEmpty())
        assertTrue(decode("40").isEmpty())
        assertTrue(decode("40 FE01").isEmpty())
    }

    @Test
    fun `other services are ignored`() {
        assertTrue(BtHomeDecoder.decode(serviceData(0xFEAA, "40 0161")).isEmpty())
        assertNull(decode("40 0161").single().field(FieldKey.TEMPERATURE_C))
    }
}
