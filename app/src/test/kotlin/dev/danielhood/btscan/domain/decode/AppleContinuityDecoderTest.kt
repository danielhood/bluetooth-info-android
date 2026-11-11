package dev.danielhood.btscan.domain.decode

import dev.danielhood.btscan.domain.decode.decoders.AppleContinuityDecoder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Type bytes verified against github.com/furiousMAC/continuity message notes: AirDrop 0x05, Proximity Pairing 0x07,
 * Find My 0x12; each message is `type, length, payload`. Everything else is deliberately unnamed.
 */
class AppleContinuityDecoderTest {
    private fun decode(payload: String) = AppleContinuityDecoder.decode(manufacturer(0x004C, payload))

    private fun labels(d: Decoded) =
        d.fields
            .filter {
                it.key == FieldKey.APPLE_MESSAGE
            }.map { (it.value as FieldValue.Label).value }

    private fun body(n: Int) = "00".repeat(n)

    @Test
    fun `named types get a label and are marked as unofficial hints`() {
        val findMy = decode("12 19 ${body(25)}").single()
        assertEquals(DecoderId.APPLE_CONTINUITY, findMy.decoder)
        assertEquals(Confidence.UNOFFICIAL_HINT, findMy.confidence)
        assertEquals(listOf(LabelKey.APPLE_FIND_MY), labels(findMy))
        assertEquals(listOf(LabelKey.APPLE_AIRDROP), labels(decode("05 12 ${body(18)}").single()))
        assertEquals(listOf(LabelKey.APPLE_PROXIMITY_PAIRING), labels(decode("07 19 ${body(25)}").single()))
    }

    @Test
    fun `an unverified type is shown as other with its code and nothing else`() {
        val d = decode("10 05 ${body(5)}").single()
        assertEquals(listOf(LabelKey.APPLE_OTHER_MESSAGE), labels(d))
        assertEquals("10", d.hexOf(FieldKey.APPLE_TYPE_CODE))
        assertEquals(setOf(FieldKey.APPLE_MESSAGE, FieldKey.APPLE_TYPE_CODE), d.fields.map { it.key }.toSet())
    }

    @Test
    fun `payload bytes are never exposed`() {
        val d = decode("12 19 ${"AB".repeat(25)}").single()
        assertTrue(
            d.fields.none { (it.value as? FieldValue.Hex)?.value?.toHex()?.contains("ABAB") == true },
        )
    }

    @Test
    fun `several messages are listed in order`() {
        val d = decode("05 02 0000 12 01 00").single()
        assertEquals(listOf(LabelKey.APPLE_AIRDROP, LabelKey.APPLE_FIND_MY), labels(d))
    }

    @Test
    fun `ibeacon payloads are left to the ibeacon decoder`() {
        assertTrue(decode("02 15 ${body(21)}").isEmpty())
    }

    @Test
    fun `malformed chains decode to nothing`() {
        assertTrue(decode("").isEmpty())
        assertTrue(decode("12").isEmpty())
        assertTrue(decode("12 19 0000").isEmpty())
        assertTrue(AppleContinuityDecoder.decode(manufacturer(0x0059, "12 01 00")).isEmpty())
    }

    @Test
    fun `a later truncated message is dropped but earlier ones stay`() {
        assertEquals(listOf(LabelKey.APPLE_AIRDROP), labels(decode("05 01 00 12 19 00").single()))
    }
}
