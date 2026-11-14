package dev.danielhood.btscan.ui.detail

import dev.danielhood.btscan.domain.system.AddressType
import dev.danielhood.btscan.domain.system.BondState
import dev.danielhood.btscan.domain.system.DeviceType
import dev.danielhood.btscan.domain.system.MajorClass
import dev.danielhood.btscan.domain.system.MinorClass
import org.junit.Assert.assertTrue
import org.junit.Test

/** Adding an enum value without its string must fail here, not at runtime on a device. */
class SystemInfoLabelsTest {
    @Test
    fun `every system info enum value has a label`() {
        MajorClass.entries.forEach { assertTrue("$it", majorLabel(it) != 0) }
        MinorClass.entries.forEach { assertTrue("$it", minorLabel(it) != 0) }
        BondState.entries.forEach { assertTrue("$it", bondLabel(it) != 0) }
        DeviceType.entries.forEach { assertTrue("$it", typeLabel(it) != 0) }
        AddressType.entries.forEach { assertTrue("$it", addressLabel(it) != 0) }
    }
}
