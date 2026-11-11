package dev.danielhood.btscan.domain.estimate

import dev.danielhood.btscan.domain.decode.Confidence
import dev.danielhood.btscan.domain.decode.Decoded
import dev.danielhood.btscan.domain.decode.DecodedField
import dev.danielhood.btscan.domain.decode.DecoderId
import dev.danielhood.btscan.domain.decode.FieldKey
import dev.danielhood.btscan.domain.decode.FieldValue
import dev.danielhood.btscan.domain.decode.Unit
import dev.danielhood.btscan.domain.model.BleDevice
import dev.danielhood.btscan.domain.model.Bytes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class EstimatesTest {
    private fun device(
        count: Int = 10,
        observedMs: Long = 9000,
        rssi: Int = -60,
        decoded: List<Decoded> = emptyList(),
    ) = BleDevice(
        address = "C0:FF:EE:00:00:01",
        name = null,
        rssi = rssi,
        txPowerDbm = null,
        isConnectable = true,
        flags = null,
        serviceUuids = emptyList(),
        serviceData = emptyList(),
        manufacturerData = emptyList(),
        rawBytes = Bytes.EMPTY,
        firstSeen = Instant.EPOCH,
        lastSeen = Instant.EPOCH.plusMillis(observedMs),
        advertisementCount = count,
        decoded = decoded,
    )

    private fun power(decoder: DecoderId, key: FieldKey, dbm: Int) =
        Decoded(
            decoder,
            Confidence.SPECIFICATION,
            listOf(DecodedField(key, FieldValue.Number(dbm.toDouble(), Unit.DBM))),
        )

    private fun ibeacon(dbm: Int) = power(DecoderId.IBEACON, FieldKey.MEASURED_POWER_1M_DBM, dbm)

    // ---- interval ----

    @Test
    fun `interval is the observed time divided by the gaps between packets`() {
        assertEquals(1000L, Estimates.advertisingIntervalMs(device(count = 10, observedMs = 9000)))
        assertEquals(100L, Estimates.advertisingIntervalMs(device(count = 51, observedMs = 5000)))
    }

    @Test
    fun `interval needs at least five packets and two seconds`() {
        assertNull(Estimates.advertisingIntervalMs(device(count = 4, observedMs = 30_000)))
        assertNull(Estimates.advertisingIntervalMs(device(count = 100, observedMs = 1999)))
        assertEquals(500L, Estimates.advertisingIntervalMs(device(count = 5, observedMs = 2000)))
    }

    // ---- distance ----

    @Test
    fun `no calibration means no distance`() {
        assertNull(Estimates.distanceBand(device()))
        assertNull(Estimates.distanceBand(device(decoded = listOf(ibeacon(0)))))
        assertNull(Estimates.distanceBand(device(decoded = listOf(ibeacon(-200)))))
        assertNull(Estimates.distanceBand(device(decoded = listOf(ibeacon(10)))))
    }

    @Test
    fun `ibeacon measured power at one metre gives bands under free space path loss`() {
        fun band(rssi: Int) = Estimates.distanceBand(device(rssi = rssi, decoded = listOf(ibeacon(-59))))
        assertEquals(DistanceBand.VERY_CLOSE, band(-50)) // 0.35 m
        assertEquals(DistanceBand.ABOUT_ONE_METRE, band(-59)) // 1 m
        assertEquals(DistanceBand.ABOUT_ONE_METRE, band(-64)) // 1.8 m
        assertEquals(DistanceBand.A_FEW_METRES, band(-69)) // 3.2 m
        assertEquals(DistanceBand.FAR, band(-79)) // 10 m
    }

    @Test
    fun `eddystone power at zero metres has 41 dB subtracted`() {
        val eddy = power(DecoderId.EDDYSTONE_UID, FieldKey.TX_POWER_0M_DBM, -21) // 1 m reference -62
        assertEquals(DistanceBand.ABOUT_ONE_METRE, Estimates.distanceBand(device(rssi = -62, decoded = listOf(eddy))))
        assertEquals(DistanceBand.FAR, Estimates.distanceBand(device(rssi = -82, decoded = listOf(eddy))))
    }

    @Test
    fun `altbeacon reference rssi is a one metre value`() {
        val alt = power(DecoderId.ALTBEACON, FieldKey.MEASURED_POWER_1M_DBM, -59)
        assertEquals(DistanceBand.ABOUT_ONE_METRE, Estimates.distanceBand(device(rssi = -59, decoded = listOf(alt))))
    }

    @Test
    fun `decoders without a calibration give no distance`() {
        val tlm = Decoded(DecoderId.EDDYSTONE_TLM, Confidence.SPECIFICATION, emptyList())
        assertNull(Estimates.distanceBand(device(decoded = listOf(tlm))))
    }

    @Test
    fun `path loss maths`() {
        assertEquals(1.0, Estimates.distanceMetres(-59, -59), 1e-9)
        assertEquals(10.0, Estimates.distanceMetres(-59, -79), 1e-9)
        assertEquals(0.1, Estimates.distanceMetres(-59, -39), 1e-9)
    }
}
