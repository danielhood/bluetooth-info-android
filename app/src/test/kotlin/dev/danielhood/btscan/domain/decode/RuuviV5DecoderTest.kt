package dev.danielhood.btscan.domain.decode

import dev.danielhood.btscan.domain.decode.decoders.RuuviV5Decoder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The valid-data vector is the documentation example from
 * https://docs.ruuvi.com/communication/bluetooth-advertisements/data-format-5-rawv2 ("Case: valid data").
 * The other vectors are derived from the same field table (offsets, scales, documented invalid values).
 */
class RuuviV5DecoderTest {
    private val valid = "05 12FC 5394 C37C 0004 FFFC 040C AC36 42 00CD CBB8334C884F"

    private fun decode(payload: String) = RuuviV5Decoder.decode(manufacturer(0x0499, payload))

    @Test
    fun `documentation example decodes to the documented reading`() {
        val d = decode(valid).single()
        assertEquals(DecoderId.RUUVI_V5, d.decoder)
        assertEquals(Confidence.SPECIFICATION, d.confidence)
        assertEquals(24.3, d.number(FieldKey.TEMPERATURE_C), 1e-9)
        assertEquals(53.49, d.number(FieldKey.HUMIDITY_PERCENT), 1e-9)
        assertEquals(1000.44, d.number(FieldKey.PRESSURE_HPA), 1e-9)
        assertEquals(0.004, d.number(FieldKey.ACCEL_X_G), 1e-9)
        assertEquals(-0.004, d.number(FieldKey.ACCEL_Y_G), 1e-9)
        assertEquals(1.036, d.number(FieldKey.ACCEL_Z_G), 1e-9)
        assertEquals(2977.0, d.number(FieldKey.BATTERY_MV), 0.0)
        assertEquals(4.0, d.number(FieldKey.TX_POWER_DBM), 0.0)
        assertEquals(66.0, d.number(FieldKey.MOVEMENT_COUNTER), 0.0)
        assertEquals(205.0, d.number(FieldKey.SEQUENCE_NUMBER), 0.0)
        assertEquals("CBB8334C884F", d.hexOf(FieldKey.DEVICE_MAC))
    }

    @Test
    fun `documented invalid values are omitted`() {
        val d = decode("05 8000 FFFF FFFF 8000 8000 8000 FFFF FF FFFF CBB8334C884F").single()
        assertEquals(listOf(FieldKey.DEVICE_MAC), d.fields.map { it.key })
    }

    @Test
    fun `extreme valid values keep their sign and scale`() {
        // Temperature -32767 * 0.005, pressure 0 + 50000 Pa, battery 0 + 1600 mV, tx power 0 -> -40 dBm.
        val d = decode("05 8001 0000 0000 8001 7FFF 8001 0000 00 0000 CBB8334C884F").single()
        assertEquals(-163.835, d.number(FieldKey.TEMPERATURE_C), 1e-9)
        assertEquals(0.0, d.number(FieldKey.HUMIDITY_PERCENT), 0.0)
        assertEquals(500.0, d.number(FieldKey.PRESSURE_HPA), 1e-9)
        assertEquals(-32.767, d.number(FieldKey.ACCEL_X_G), 1e-9)
        assertEquals(32.767, d.number(FieldKey.ACCEL_Y_G), 1e-9)
        assertEquals(1600.0, d.number(FieldKey.BATTERY_MV), 0.0)
        assertEquals(-40.0, d.number(FieldKey.TX_POWER_DBM), 0.0)
        assertEquals(0.0, d.number(FieldKey.MOVEMENT_COUNTER), 0.0)
    }

    @Test
    fun `battery and tx power come from one shared word`() {
        // 0xFFDE is the largest valid word: battery 0x7FE = 2046 -> 3646 mV, tx 0x1E = 30 -> 20 dBm.
        val max = decode("05 0000 0000 0000 0000 0000 0000 FFDE 00 0000 CBB8334C884F").single()
        assertEquals(3646.0, max.number(FieldKey.BATTERY_MV), 0.0)
        assertEquals(20.0, max.number(FieldKey.TX_POWER_DBM), 0.0)
        // 0xFFFF: battery 0x7FF and tx 0x1F are both the documented invalid values.
        val invalid = decode("05 0000 0000 0000 0000 0000 0000 FFFF 00 0000 CBB8334C884F").single()
        assertNull(invalid.field(FieldKey.BATTERY_MV))
        assertNull(invalid.field(FieldKey.TX_POWER_DBM))
    }

    @Test
    fun `other company other format and wrong length are ignored`() {
        assertTrue(RuuviV5Decoder.decode(manufacturer(0x004C, valid)).isEmpty())
        assertTrue(decode(valid.replaceFirst("05", "03")).isEmpty())
        assertTrue(decode("$valid 00").isEmpty())
    }

    @Test
    fun `every truncated prefix decodes to nothing`() {
        val bytes = hex(valid).toByteArray()
        for (n in 0 until bytes.size) {
            val prefix = bytes.copyOf(n).joinToString("") { "%02X".format(it) }
            assertTrue("prefix of $n bytes", decode(prefix).isEmpty())
        }
    }
}
