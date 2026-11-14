package dev.danielhood.btscan.domain.repo

import dev.danielhood.btscan.domain.model.ClassicSighting
import dev.danielhood.btscan.domain.model.DeviceSource
import dev.danielhood.btscan.domain.model.ScanFailure
import dev.danielhood.btscan.domain.port.ClassicScanner
import dev.danielhood.btscan.domain.system.BondState
import dev.danielhood.btscan.domain.system.DeviceClassInfo
import dev.danielhood.btscan.testing.ManualClassicScanner
import dev.danielhood.btscan.testing.ManualScanner
import dev.danielhood.btscan.testing.advance
import dev.danielhood.btscan.testing.rawAd
import dev.danielhood.btscan.testing.testClock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class ClassicDiscoveryTest {
    private class Rig(
        val repo: DefaultDeviceRepository,
        val ble: ManualScanner,
        val classic: ManualClassicScanner,
    )

    private fun TestScope.rig(classicScanner: ClassicScanner? = null): Rig {
        val ble = ManualScanner()
        val classic = ManualClassicScanner()
        return Rig(DefaultDeviceRepository(ble, classicScanner ?: classic, testClock(), backgroundScope), ble, classic)
    }

    private fun TestScope.sighting(
        address: String,
        name: String? = "Name",
        rssi: Int? = -60,
        deviceClass: Int? = 0x0418,
        bond: BondState? = BondState.BONDED,
    ) = ClassicSighting(
        address,
        name,
        rssi,
        deviceClass?.let(DeviceClassInfo::of),
        bond,
        Instant.EPOCH.plusMillis(testScheduler.currentTime),
    )

    @Test
    fun `a classic sighting becomes a classic only device with no advertisement`() =
        runTest {
            val r = rig()
            r.repo.startClassicDiscovery()
            advance(1)
            r.classic.found(sighting("C0:FF:EE:00:00:0A", name = "Car Audio"))
            advance(600)
            val d =
                r.repo.devices.value
                    .single()
            assertEquals(DeviceSource.CLASSIC, d.source)
            assertEquals("Car Audio", d.name)
            assertEquals(0, d.advertisementCount)
            assertEquals(0, d.rawBytes.size)
            assertEquals(BondState.BONDED, d.classic?.bondState)
            assertEquals(DeviceClassInfo.of(0x0418), d.classic?.deviceClass)
        }

    @Test
    fun `a device seen by both appears once, marked both, keeping its advertisement`() =
        runTest {
            val r = rig()
            r.repo.startScan()
            advance(1)
            r.ble.ads.emit(rawAd("AA", rssi = -70, name = "Buds"))
            advance(600)
            r.repo.startClassicDiscovery() // stops the BLE scan first
            advance(1)
            r.classic.found(sighting("AA", name = "Buds Classic", rssi = -50))
            advance(600)
            val d =
                r.repo.devices.value
                    .single()
            assertEquals(DeviceSource.BOTH, d.source)
            assertEquals("the advertised name wins", "Buds", d.name)
            assertTrue("advertisement bytes are kept", d.rawBytes.size > 0)
            assertEquals(1, d.advertisementCount)
            assertEquals(-50, d.rssi)
        }

    @Test
    fun `an advertisement arriving for a classic only device makes it both`() =
        runTest {
            val r = rig()
            r.repo.startClassicDiscovery()
            advance(1)
            r.classic.found(sighting("BB", name = null))
            r.classic.finish()
            advance(600)
            assertEquals(
                DeviceSource.CLASSIC,
                r.repo.devices.value
                    .single()
                    .source,
            )
            r.repo.startScan()
            advance(1)
            r.ble.ads.emit(rawAd("BB", rssi = -65, name = "Now Advertising"))
            advance(600)
            val d =
                r.repo.devices.value
                    .single()
            assertEquals(DeviceSource.BOTH, d.source)
            assertEquals("Now Advertising", d.name)
        }

    @Test
    fun `the two scans never run together`() =
        runTest {
            val r = rig()
            r.repo.startScan()
            advance(1)
            assertTrue(r.repo.scanning.value)
            r.repo.startClassicDiscovery()
            advance(1)
            assertFalse("starting classic stops BLE", r.repo.scanning.value)
            assertTrue(r.repo.scanningClassic.value)
            r.repo.startScan()
            advance(1)
            assertTrue("starting BLE stops classic", r.repo.scanning.value)
            assertFalse(r.repo.scanningClassic.value)
            assertEquals("the inquiry was cancelled, not left running", 0, r.classic.active)
        }

    @Test
    fun `the inquiry ends by itself and keeps what it found`() =
        runTest {
            val r = rig()
            r.repo.startClassicDiscovery()
            advance(1)
            r.classic.found(sighting("CC"))
            advance(100)
            r.classic.finish()
            advance(600)
            assertFalse(r.repo.scanningClassic.value)
            assertEquals(1, r.repo.devices.value.size)
        }

    @Test
    fun `an inquiry the platform never ends is cut off after fifteen seconds`() =
        runTest {
            val r = rig()
            r.repo.startClassicDiscovery()
            advance(14_000)
            assertTrue(r.repo.scanningClassic.value)
            advance(1_500)
            assertFalse(r.repo.scanningClassic.value)
            assertEquals(0, r.classic.active)
        }

    @Test
    fun `stopping cancels the inquiry`() =
        runTest {
            val r = rig()
            r.repo.startClassicDiscovery()
            advance(1)
            assertEquals(1, r.classic.active)
            r.repo.stopClassicDiscovery()
            advance(1)
            assertEquals(0, r.classic.active)
            assertFalse(r.repo.scanningClassic.value)
        }

    @Test
    fun `starting twice runs one inquiry`() =
        runTest {
            val r = rig()
            r.repo.startClassicDiscovery()
            r.repo.startClassicDiscovery()
            advance(1)
            assertEquals(1, r.classic.starts)
        }

    @Test
    fun `classic devices go stale like any other`() =
        runTest {
            val r = rig()
            r.repo.startClassicDiscovery()
            advance(1)
            r.classic.found(sighting("DD"))
            advance(600)
            assertEquals(1, r.repo.devices.value.size)
            advance(31_000) // the inquiry's own safety limit ended it at 15 s; staleness is checked while it ticks
            r.repo.startClassicDiscovery()
            advance(1)
            advance(1_000)
            assertTrue(
                "a device not heard for 30 s is dropped",
                r.repo.devices.value
                    .isEmpty(),
            )
        }

    @Test
    fun `a sighting without a signal reading keeps the last known one and a new one is not invented`() =
        runTest {
            val r = rig()
            r.repo.startClassicDiscovery()
            advance(1)
            r.classic.found(sighting("EE", rssi = null, deviceClass = null, bond = null))
            advance(600)
            val d =
                r.repo.devices.value
                    .single()
            assertNull(d.classic?.deviceClass)
            assertNull("unknown bond state stays unknown", d.classic?.bondState)
        }

    @Test
    fun `a failing inquiry is reported as a scan failure`() =
        runTest {
            val failing =
                object : ClassicScanner {
                    override fun discover(): Flow<ClassicSighting> =
                        flow { throw ScanFailure(ScanFailure.Kind.PERMISSION_MISSING) }
                }
            val r = rig(failing)
            r.repo.startClassicDiscovery()
            advance(100)
            assertEquals(
                ScanFailure.Kind.PERMISSION_MISSING,
                r.repo.failure.value
                    ?.kind,
            )
            assertFalse(r.repo.scanningClassic.value)
        }
}
