package dev.danielhood.btscan.domain.decode

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PrimaryKindTest {
    private fun decoded(vararg ids: DecoderId) = ids.map { Decoded(it, Confidence.SPECIFICATION, emptyList()) }

    @Test
    fun `nothing matched gives no chip`() {
        assertNull(emptyList<Decoded>().primaryKind())
    }

    @Test
    fun `the most informative decoder wins regardless of order`() {
        assertEquals(DecoderId.RUUVI_V5, decoded(DecoderId.APPLE_CONTINUITY, DecoderId.RUUVI_V5).primaryKind())
        assertEquals(DecoderId.IBEACON, decoded(DecoderId.TRACKER_HINT, DecoderId.IBEACON).primaryKind())
        assertEquals(DecoderId.TRACKER_HINT, decoded(DecoderId.APPLE_CONTINUITY, DecoderId.TRACKER_HINT).primaryKind())
    }

    @Test
    fun `every decoder appears exactly once in the priority list`() {
        assertEquals(DecoderId.entries.toSet(), KIND_PRIORITY.toSet())
        assertEquals(KIND_PRIORITY.size, KIND_PRIORITY.toSet().size)
    }
}
