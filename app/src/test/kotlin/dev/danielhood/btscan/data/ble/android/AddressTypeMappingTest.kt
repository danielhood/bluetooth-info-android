package dev.danielhood.btscan.data.ble.android

import dev.danielhood.btscan.domain.model.AddressType
import org.junit.Assert.assertEquals
import org.junit.Test

class AddressTypeMappingTest {
    @Test
    fun `platform values map to address types`() {
        assertEquals(AddressType.PUBLIC, addressTypeOf(0))
        assertEquals(AddressType.RANDOM, addressTypeOf(1))
        assertEquals(AddressType.ANONYMOUS, addressTypeOf(255))
    }

    @Test
    fun `the platform's unknown value and anything else is unknown, never guessed`() {
        assertEquals(AddressType.UNKNOWN, addressTypeOf(65535))
        assertEquals(AddressType.UNKNOWN, addressTypeOf(7))
        assertEquals(AddressType.UNKNOWN, addressTypeOf(-1))
    }
}
