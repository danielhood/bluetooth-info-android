package dev.danielhood.btscan.data.ble.android

import dev.danielhood.btscan.domain.model.DataStatus
import dev.danielhood.btscan.domain.model.Phy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RadioInfoMappingTest {
    @Test
    fun `reported values are mapped`() {
        val r = radioInfoOf(1, 3, false, 7, -8, 80, 2)
        assertEquals(Phy.LE_1M, r.primaryPhy)
        assertEquals(Phy.LE_CODED, r.secondaryPhy)
        assertEquals(false, r.isLegacy)
        assertEquals(7, r.advertisingSid)
        assertEquals(-8, r.txPowerDbm)
        assertEquals(80, r.periodicIntervalUnits)
        assertEquals(DataStatus.TRUNCATED, r.dataStatus)
    }

    @Test
    fun `sentinel values become null and the domain never sees them`() {
        val r = radioInfoOf(1, 0, true, 255, 127, 0, 0)
        assertNull(r.secondaryPhy)
        assertNull(r.advertisingSid)
        assertNull(r.txPowerDbm)
        assertNull(r.periodicIntervalUnits)
        assertEquals(DataStatus.COMPLETE, r.dataStatus)
        assertEquals(true, r.isLegacy)
    }

    @Test
    fun `second phy and unknown status are handled`() {
        val r = radioInfoOf(2, 2, false, 0, 0, 0, 1)
        assertEquals(Phy.LE_2M, r.primaryPhy)
        assertNull(r.dataStatus)
        assertEquals(0, r.advertisingSid)
        assertEquals(0, r.txPowerDbm)
    }
}
