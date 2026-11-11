package dev.danielhood.btscan.ui.detail

import dev.danielhood.btscan.domain.decode.Confidence
import dev.danielhood.btscan.domain.decode.DecoderId
import dev.danielhood.btscan.domain.decode.FieldKey
import org.junit.Assert.assertTrue
import org.junit.Test

/** Adding a decoder, field or confidence without its string must fail here, not at runtime on a device. */
class DecodedSectionsTest {
    @Test
    fun `every field key has a label`() {
        FieldKey.entries.forEach { assertTrue("$it", fieldLabel(it) != 0) }
    }

    @Test
    fun `every decoder has a title and every confidence a label`() {
        DecoderId.entries.forEach { assertTrue("$it", decoderTitle(it) != 0) }
        Confidence.entries.forEach { assertTrue("$it", confidenceLabel(it) != 0) }
    }
}
