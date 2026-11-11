package dev.danielhood.btscan.ui.list

import dev.danielhood.btscan.domain.decode.Confidence
import dev.danielhood.btscan.domain.decode.Decoded
import dev.danielhood.btscan.domain.decode.DecoderId
import dev.danielhood.btscan.domain.model.BleDevice
import dev.danielhood.btscan.domain.model.Bytes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class DeviceRowKindTest {
    private fun device(decoded: List<Decoded>) =
        BleDevice(
            address = "C0:FF:EE:00:00:01",
            name = null,
            rssi = -60,
            txPowerDbm = null,
            isConnectable = false,
            flags = null,
            serviceUuids = emptyList(),
            serviceData = emptyList(),
            manufacturerData = emptyList(),
            rawBytes = Bytes.EMPTY,
            firstSeen = Instant.EPOCH,
            lastSeen = Instant.EPOCH,
            advertisementCount = 1,
            decoded = decoded,
        )

    @Test
    fun `row kind is the primary decoder or null`() {
        assertNull(device(emptyList()).toRowUi().kind)
        val ibeacon = Decoded(DecoderId.IBEACON, Confidence.SPECIFICATION, emptyList())
        assertEquals(DecoderId.IBEACON, device(listOf(ibeacon)).toRowUi().kind)
    }

    @Test
    fun `every decoder has a chip label`() {
        DecoderId.entries.forEach { assertTrue("$it", kindChipLabel(it) != 0) }
    }
}
