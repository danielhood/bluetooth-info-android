package dev.danielhood.btscan.domain.decode

import dev.danielhood.btscan.domain.decode.decoders.FastPairDecoder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Rules from https://developers.google.com/nearby/fast-pair/specifications/service/provider (service UUID 0xFE2C). */
class FastPairDecoderTest {
    private fun decode(payload: String) = FastPairDecoder.decode(serviceData(0xFE2C, payload))

    @Test
    fun `three bytes is discoverable mode with a model id shown only as a number`() {
        val d = decode("2A3B4C").single()
        assertEquals(DecoderId.FAST_PAIR, d.decoder)
        assertEquals(Confidence.SPECIFICATION, d.confidence)
        assertEquals(FieldValue.Bool(true), d.field(FieldKey.FAST_PAIR_DISCOVERABLE))
        assertEquals("2A3B4C", d.hexOf(FieldKey.FAST_PAIR_MODEL_ID))
    }

    @Test
    fun `longer data is the not discoverable mode and is not interpreted`() {
        val d = decode("00 11 22 33 44 55 66 77").single()
        assertEquals(FieldValue.Bool(false), d.field(FieldKey.FAST_PAIR_DISCOVERABLE))
        assertNull(d.field(FieldKey.FAST_PAIR_MODEL_ID))
    }

    @Test
    fun `short data and other services are ignored`() {
        assertTrue(decode("").isEmpty())
        assertTrue(decode("2A").isEmpty())
        assertTrue(decode("2A3B").isEmpty())
        assertTrue(FastPairDecoder.decode(serviceData(0xFEAA, "2A3B4C")).isEmpty())
    }
}
