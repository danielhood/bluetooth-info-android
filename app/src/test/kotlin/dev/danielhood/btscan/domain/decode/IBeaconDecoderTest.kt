package dev.danielhood.btscan.domain.decode

import dev.danielhood.btscan.domain.decode.decoders.IBeaconDecoder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Vectors are constructed from the documented iBeacon layout (02 15, 16-byte UUID, major and minor big-endian,
 * signed measured power); the UUID is the widely used sample UUID E2C56DB5-DFFB-48D2-B060-D0F5A71096E0.
 */
class IBeaconDecoderTest {
    private val full = "02 15 E2C56DB5DFFB48D2B060D0F5A71096E0 0001 0002 C5"

    @Test
    fun `decodes uuid major minor and measured power`() {
        val d = IBeaconDecoder.decode(manufacturer(0x004C, full)).single()
        assertEquals(DecoderId.IBEACON, d.decoder)
        assertEquals(Confidence.SPECIFICATION, d.confidence)
        assertEquals("E2C56DB5-DFFB-48D2-B060-D0F5A71096E0", d.text(FieldKey.BEACON_UUID))
        assertEquals(1.0, d.number(FieldKey.BEACON_MAJOR), 0.0)
        assertEquals(2.0, d.number(FieldKey.BEACON_MINOR), 0.0)
        assertEquals(-59.0, d.number(FieldKey.MEASURED_POWER_1M_DBM), 0.0)
    }

    @Test
    fun `major and minor use the full unsigned 16 bit range`() {
        val d =
            IBeaconDecoder
                .decode(
                    manufacturer(0x004C, "02 15 00112233445566778899AABBCCDDEEFF FFFE 8000 00"),
                ).single()
        assertEquals(65534.0, d.number(FieldKey.BEACON_MAJOR), 0.0)
        assertEquals(32768.0, d.number(FieldKey.BEACON_MINOR), 0.0)
        assertEquals(0.0, d.number(FieldKey.MEASURED_POWER_1M_DBM), 0.0)
    }

    @Test
    fun `other company ids are ignored`() {
        assertTrue(IBeaconDecoder.decode(manufacturer(0x0059, full)).isEmpty())
    }

    @Test
    fun `wrong type or length byte is not an ibeacon`() {
        assertTrue(IBeaconDecoder.decode(manufacturer(0x004C, full.replace("02 15", "02 16"))).isEmpty())
        assertTrue(IBeaconDecoder.decode(manufacturer(0x004C, full.replace("02 15", "10 05"))).isEmpty())
    }

    @Test
    fun `every truncated prefix and an over long payload decode to nothing`() {
        val bytes = hex(full).toByteArray()
        for (n in 0 until bytes.size) {
            val prefix = bytes.copyOf(n).joinToString("") { "%02X".format(it) }
            assertTrue("prefix of $n bytes", IBeaconDecoder.decode(manufacturer(0x004C, prefix)).isEmpty())
        }
        assertTrue(IBeaconDecoder.decode(manufacturer(0x004C, full + "00")).isEmpty())
    }

    @Test
    fun `no manufacturer data decodes to nothing`() {
        assertTrue(
            IBeaconDecoder
                .decode(
                    dev.danielhood.btscan.domain.model
                        .Advertisement(),
                ).isEmpty(),
        )
    }
}
