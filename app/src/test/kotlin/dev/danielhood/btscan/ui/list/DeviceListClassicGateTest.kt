package dev.danielhood.btscan.ui.list

import dev.danielhood.btscan.data.ble.fake.FakeAdapterStateMonitor
import dev.danielhood.btscan.domain.model.AdapterState
import dev.danielhood.btscan.domain.model.ClassicSighting
import dev.danielhood.btscan.domain.model.DeviceSource
import dev.danielhood.btscan.domain.model.PermissionState
import dev.danielhood.btscan.domain.repo.DefaultDeviceRepository
import dev.danielhood.btscan.domain.system.BondState
import dev.danielhood.btscan.testing.FakePermissionChecker
import dev.danielhood.btscan.testing.ManualClassicScanner
import dev.danielhood.btscan.testing.ManualScanner
import dev.danielhood.btscan.testing.advance
import dev.danielhood.btscan.testing.rawAd
import dev.danielhood.btscan.testing.testClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant

/**
 * The UI gate for classic Bluetooth: an inquiry can disturb audio, so it only ever starts from an explicit tap on the
 * classic button, confirmed in a notice once per process. Every other path is shown here to leave it stopped.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DeviceListClassicGateTest {
    @Before
    fun setUp() = Dispatchers.setMain(StandardTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private class Rig(
        val vm: DeviceListViewModel,
        val repo: DefaultDeviceRepository,
        val ble: ManualScanner,
        val classic: ManualClassicScanner,
        val permissions: FakePermissionChecker,
        val adapter: FakeAdapterStateMonitor,
    ) {
        val state get() = vm.uiState.value
    }

    private fun TestScope.rig(permission: PermissionState = PermissionState.Granted): Rig {
        val clock = testClock()
        val ble = ManualScanner()
        val classic = ManualClassicScanner()
        val repo = DefaultDeviceRepository(ble, classic, clock, backgroundScope)
        val permissions = FakePermissionChecker(permission)
        val adapter = FakeAdapterStateMonitor(AdapterState.ON)
        val vm = DeviceListViewModel(repo, adapter, permissions, clock)
        advance(0)
        return Rig(vm, repo, ble, classic, permissions, adapter)
    }

    private fun TestScope.tapClassic(r: Rig) {
        r.vm.onClassicScanClicked()
        advance(1_100) // past the toggle debounce
    }

    private fun TestScope.sighting(address: String, name: String?) =
        ClassicSighting(address, name, -60, null, BondState.NONE, Instant.EPOCH.plusMillis(testScheduler.currentTime))

    @Test
    fun `the first tap asks and starts nothing`() =
        runTest {
            val r = rig()
            tapClassic(r)
            assertTrue("the notice is shown", r.state.classicNoticePending)
            assertEquals("no inquiry was started", 0, r.classic.starts)
            assertFalse(r.state.classicScanning)
        }

    @Test
    fun `cancelling the notice starts nothing and the next tap asks again`() =
        runTest {
            val r = rig()
            tapClassic(r)
            r.vm.dismissClassicNotice()
            advance(1_100)
            assertFalse(r.state.classicNoticePending)
            assertEquals(0, r.classic.starts)
            tapClassic(r)
            assertTrue("still gated: asked again", r.state.classicNoticePending)
            assertEquals(0, r.classic.starts)
        }

    @Test
    fun `confirming the notice starts the inquiry the user asked for`() =
        runTest {
            val r = rig()
            tapClassic(r)
            r.vm.confirmClassicNotice()
            advance(1)
            assertEquals(1, r.classic.starts)
            assertTrue(r.state.classicScanning)
            assertFalse(r.state.classicNoticePending)
        }

    @Test
    fun `after confirming once the next tap starts straight away`() =
        runTest {
            val r = rig()
            tapClassic(r)
            r.vm.confirmClassicNotice()
            advance(1)
            r.classic.finish()
            advance(1_100)
            tapClassic(r)
            assertFalse("no second notice in this process", r.state.classicNoticePending)
            assertEquals(2, r.classic.starts)
        }

    @Test
    fun `no other path starts an inquiry`() =
        runTest {
            val r = rig(permission = PermissionState.Denied(canAskAgain = true))
            r.permissions.state = PermissionState.Granted
            r.vm.onPermissionResult(granted = true, canAskAgain = true) // a permission grant
            r.vm.refreshEnvironment() // a resume
            r.vm.toggleScan() // starting the BLE scan
            advance(1_100)
            r.vm.toggleScan() // stopping it
            advance(1_100)
            r.vm.setSourceFilter(SourceFilter.CLASSIC) // choosing the Classic filter
            r.adapter.set(AdapterState.OFF)
            advance(100)
            r.adapter.set(AdapterState.ON) // Bluetooth coming back
            advance(100)
            r.vm.onBackgrounded()
            advance(100)
            r.vm.clear()
            advance(100)
            assertEquals("not one inquiry across every other path", 0, r.classic.starts)
        }

    @Test
    fun `the classic button does nothing while scanning is blocked`() =
        runTest {
            val r = rig(permission = PermissionState.Denied(canAskAgain = true))
            assertFalse(r.state.canStartClassic)
            tapClassic(r)
            assertFalse("no notice either", r.state.classicNoticePending)
            assertEquals(0, r.classic.starts)
        }

    @Test
    fun `a second tap while an inquiry runs stops it`() =
        runTest {
            val r = rig()
            tapClassic(r)
            r.vm.confirmClassicNotice()
            advance(1_100)
            tapClassic(r)
            assertFalse(r.state.classicScanning)
            assertEquals(0, r.classic.active)
        }

    @Test
    fun `an inquiry that ends by itself clears the scanning state`() =
        runTest {
            val r = rig()
            tapClassic(r)
            r.vm.confirmClassicNotice()
            advance(1)
            r.classic.finish()
            advance(600)
            assertFalse(r.state.classicScanning)
        }

    @Test
    fun `starting classic while the BLE scan runs stops it and says so`() =
        runTest {
            val r = rig()
            r.vm.toggleScan()
            advance(1_100)
            assertTrue(r.state.isScanning)
            tapClassic(r)
            r.vm.confirmClassicNotice()
            advance(1)
            assertFalse("BLE stopped", r.state.isScanning)
            assertTrue(r.state.classicScanning)
            assertEquals(ListNotice.BLE_STOPPED_FOR_CLASSIC, r.state.notice)
        }

    @Test
    fun `starting the BLE scan while classic runs stops it and says so`() =
        runTest {
            val r = rig()
            tapClassic(r)
            r.vm.confirmClassicNotice()
            advance(1_100)
            r.vm.toggleScan()
            advance(1_100)
            assertTrue(r.state.isScanning)
            assertFalse("classic stopped", r.state.classicScanning)
            assertEquals(ListNotice.CLASSIC_STOPPED_FOR_BLE, r.state.notice)
        }

    @Test
    fun `leaving the app or turning bluetooth off stops the inquiry`() =
        runTest {
            val r = rig()
            tapClassic(r)
            r.vm.confirmClassicNotice()
            advance(1)
            r.vm.onBackgrounded()
            advance(100)
            assertFalse(r.state.classicScanning)
            assertEquals(0, r.classic.active)
            r.vm.onClassicScanClicked()
            advance(1_100)
            assertTrue(r.state.classicScanning)
            r.adapter.set(AdapterState.OFF)
            advance(100)
            assertFalse(r.state.classicScanning)
            assertEquals(0, r.classic.active)
        }

    @Test
    fun `a rapid double tap runs one inquiry`() =
        runTest {
            val r = rig()
            tapClassic(r)
            r.vm.confirmClassicNotice()
            advance(1)
            r.classic.finish()
            advance(1_100)
            r.vm.onClassicScanClicked()
            r.vm.onClassicScanClicked()
            advance(10)
            assertEquals(2, r.classic.starts)
        }

    @Test
    fun `the source filter shows ble, classic or all`() =
        runTest {
            val r = rig()
            r.vm.toggleScan()
            advance(1_100)
            r.ble.ads.emit(rawAd("BLE:ONLY", name = "Sensor"))
            r.ble.ads.emit(rawAd("BOTH", name = "Buds"))
            advance(600)
            tapClassic(r)
            r.vm.confirmClassicNotice()
            advance(1)
            r.classic.found(sighting("BOTH", "Buds"))
            r.classic.found(sighting("CLASSIC:ONLY", "Car"))
            advance(600)

            fun shown() =
                r.state.devices
                    .map { it.address to it.source }
                    .toSet()
            assertEquals(3, shown().size)
            assertTrue(r.state.showSourceBadges)
            r.vm.setSourceFilter(SourceFilter.BLE)
            advance(10)
            assertEquals(setOf("BLE:ONLY" to DeviceSource.BLE, "BOTH" to DeviceSource.BOTH), shown())
            r.vm.setSourceFilter(SourceFilter.CLASSIC)
            advance(10)
            assertEquals(setOf("BOTH" to DeviceSource.BOTH, "CLASSIC:ONLY" to DeviceSource.CLASSIC), shown())
            r.vm.setSourceFilter(SourceFilter.ALL)
            advance(10)
            assertEquals(3, shown().size)
        }

    @Test
    fun `badges appear only once a classic device exists and the notice clears with the list`() =
        runTest {
            val r = rig()
            r.vm.toggleScan()
            advance(1_100)
            r.ble.ads.emit(rawAd("X", name = "Sensor"))
            advance(600)
            assertFalse("everything is BLE, so no badges", r.state.showSourceBadges)
            assertNull(r.state.notice)
        }
}
