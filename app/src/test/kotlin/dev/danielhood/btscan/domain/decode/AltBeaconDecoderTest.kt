package dev.danielhood.btscan.domain.decode

import dev.danielhood.btscan.domain.decode.decoders.AltBeaconDecoder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Layout from https://github.com/AltBeacon/spec : beacon code BE AC, 20-byte id, reference RSSI, reserved byte. */
class AltBeaconDecoderTest {
    private val full = "BEAC 2F234454CF6D4A0FADF2F4911BA9FFA6 0001 0002 C5 01"

    @Test
    fun `decodes id and reference rssi for any company`() {
        listOf(0x0118, 0x0059, 0xFFFF).forEach { company ->
            val d = AltBeaconDecoder.decode(manufacturer(company, full)).single()
            assertEquals(DecoderId.ALTBEACON, d.decoder)
            assertEquals("2F234454-CF6D-4A0F-ADF2-F4911BA9FFA6", d.text(FieldKey.BEACON_UUID))
            assertEquals(1.0, d.number(FieldKey.BEACON_MAJOR), 0.0)
            assertEquals(2.0, d.number(FieldKey.BEACON_MINOR), 0.0)
            assertEquals(-59.0, d.number(FieldKey.MEASURED_POWER_1M_DBM), 0.0)
        }
    }

    @Test
    fun `wrong code, wrong length and every truncation decode to nothing`() {
        assertTrue(AltBeaconDecoder.decode(manufacturer(0x0118, full.replace("BEAC", "BEAD"))).isEmpty())
        assertTrue(AltBeaconDecoder.decode(manufacturer(0x0118, "$full 00")).isEmpty())
        val bytes = hex(full).toByteArray()
        for (n in 0 until bytes.size) {
            val prefix = bytes.copyOf(n).joinToString("") { "%02X".format(it) }
            assertTrue("prefix of $n bytes", AltBeaconDecoder.decode(manufacturer(0x0118, prefix)).isEmpty())
        }
    }
}
