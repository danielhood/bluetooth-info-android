package dev.danielhood.btscan.ui.list

import dev.danielhood.btscan.data.ble.fake.FakeAdapterStateMonitor
import dev.danielhood.btscan.data.ble.fake.FakeBleScanner
import dev.danielhood.btscan.data.ble.fake.Scenarios
import dev.danielhood.btscan.domain.model.AdapterState
import dev.danielhood.btscan.domain.model.PermissionState
import dev.danielhood.btscan.domain.model.ScanFailure
import dev.danielhood.btscan.domain.port.BleScanner
import dev.danielhood.btscan.domain.repo.DefaultDeviceRepository
import dev.danielhood.btscan.testing.FakePermissionChecker
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DeviceListViewModelTest {
    @Before
    fun setUp() = Dispatchers.setMain(StandardTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private class Fixture(
        val vm: DeviceListViewModel,
        val repo: DefaultDeviceRepository,
        val permissions: FakePermissionChecker,
        val adapter: FakeAdapterStateMonitor,
    ) {
        val state get() = vm.uiState.value
    }

    private fun TestScope.fixture(
        scanner: BleScanner = ManualScanner(),
        permission: PermissionState = PermissionState.Granted,
        adapterState: AdapterState = AdapterState.ON,
    ): Fixture {
        val clock = testClock()
        val repo = DefaultDeviceRepository(scanner, clock, backgroundScope)
        val permissions = FakePermissionChecker(permission)
        val adapter = FakeAdapterStateMonitor(adapterState)
        val vm = DeviceListViewModel(repo, adapter, permissions, clock)
        advance(0)
        return Fixture(vm, repo, permissions, adapter)
    }

    @Test
    fun `initially idle with no devices and nothing scanned`() =
        runTest {
            val f = fixture()
            assertEquals(ListStatus.Idle, f.state.status)
            assertTrue(f.state.devices.isEmpty())
            assertFalse(f.state.hasScanned)
            assertTrue(f.state.canToggleScan)
        }

    @Test
    fun `missing permission shows permission needed and starting is ignored`() =
        runTest {
            val f = fixture(permission = PermissionState.Denied(canAskAgain = true))
            assertEquals(ListStatus.PermissionNeeded(permanentlyDenied = false), f.state.status)
            assertFalse(f.state.canToggleScan)
            f.vm.toggleScan()
            advance(100)
            assertFalse(f.repo.scanning.value)
        }

    @Test
    fun `a denied request the os will not repeat is permanently denied`() =
        runTest {
            val f = fixture(permission = PermissionState.Denied(canAskAgain = true))
            f.vm.onPermissionResult(granted = false, canAskAgain = false)
            advance(0)
            assertEquals(ListStatus.PermissionNeeded(permanentlyDenied = true), f.state.status)
        }

    @Test
    fun `a denied request that can be repeated is not permanent`() =
        runTest {
            val f = fixture(permission = PermissionState.Denied(canAskAgain = true))
            f.vm.onPermissionResult(granted = false, canAskAgain = true)
            advance(0)
            assertEquals(ListStatus.PermissionNeeded(permanentlyDenied = false), f.state.status)
        }

    @Test
    fun `granting the permission makes the screen ready but does not start a scan`() =
        runTest {
            val f = fixture(permission = PermissionState.Denied(canAskAgain = true))
            f.permissions.state = PermissionState.Granted
            f.vm.onPermissionResult(granted = true, canAskAgain = true)
            advance(500)
            assertEquals(ListStatus.Idle, f.state.status)
            assertFalse(f.repo.scanning.value)
        }

    @Test
    fun `permission granted in settings is picked up on resume and clears permanent denial`() =
        runTest {
            val f = fixture(permission = PermissionState.Denied(canAskAgain = true))
            f.vm.onPermissionResult(granted = false, canAskAgain = false)
            f.permissions.state = PermissionState.Granted
            f.vm.refreshEnvironment()
            advance(0)
            assertEquals(ListStatus.Idle, f.state.status)
            f.permissions.state = PermissionState.Denied(canAskAgain = true)
            f.vm.refreshEnvironment()
            advance(0)
            assertEquals(ListStatus.PermissionNeeded(permanentlyDenied = false), f.state.status)
        }

    @Test
    fun `adapter states map to blocking statuses`() =
        runTest {
            for ((adapter, expected) in listOf(
                AdapterState.OFF to ListStatus.BluetoothOff,
                AdapterState.LOCATION_OFF to ListStatus.LocationOff,
                AdapterState.UNSUPPORTED to ListStatus.Unsupported,
            )) {
                val f = fixture(adapterState = adapter)
                assertEquals(expected, f.state.status)
                assertTrue(f.state.isBlocked)
                assertFalse(f.state.canToggleScan)
            }
        }

    @Test
    fun `blocker priority is unsupported then permission then bluetooth off`() =
        runTest {
            val a = fixture(permission = PermissionState.Denied(true), adapterState = AdapterState.UNSUPPORTED)
            assertEquals(ListStatus.Unsupported, a.state.status)
            val b = fixture(permission = PermissionState.Denied(true), adapterState = AdapterState.OFF)
            assertEquals(ListStatus.PermissionNeeded(false), b.state.status)
        }

    @Test
    fun `turning bluetooth off mid scan stops the scan and shows bluetooth off`() =
        runTest {
            val f = fixture()
            f.vm.toggleScan()
            advance(100)
            assertEquals(ListStatus.Scanning, f.state.status)
            f.adapter.set(AdapterState.OFF)
            advance(0)
            assertEquals(ListStatus.BluetoothOff, f.state.status)
            assertFalse("scan must not keep running behind the message", f.repo.scanning.value)
        }

    @Test
    fun `bluetooth coming back after a mid scan loss is idle, not a phantom scan`() =
        runTest {
            val f = fixture()
            f.vm.toggleScan()
            advance(100)
            f.adapter.set(AdapterState.OFF)
            advance(0)
            f.adapter.set(AdapterState.ON)
            advance(0)
            assertEquals(ListStatus.Idle, f.state.status)
            advance(1_500)
            f.vm.toggleScan()
            advance(100)
            assertEquals(ListStatus.Scanning, f.state.status)
        }

    @Test
    fun `losing location on old android also stops the scan`() =
        runTest {
            val f = fixture()
            f.vm.toggleScan()
            advance(100)
            f.adapter.set(AdapterState.LOCATION_OFF)
            advance(0)
            assertEquals(ListStatus.LocationOff, f.state.status)
            assertFalse(f.repo.scanning.value)
        }

    @Test
    fun `start scans, lists devices strongest first and counts them`() =
        runTest {
            val scanner = ManualScanner()
            val f = fixture(scanner)
            f.vm.toggleScan()
            advance(100)
            assertEquals(ListStatus.Scanning, f.state.status)
            assertTrue(f.state.hasScanned)
            scanner.ads.emit(rawAd("B", rssi = -80, name = "Far"))
            scanner.ads.emit(rawAd("A", rssi = -45, name = "Near"))
            advance(500)
            assertEquals(listOf("A", "B"), f.state.devices.map { it.address })
            assertEquals(listOf(4, 2), f.state.devices.map { it.signalLevel })
            assertEquals(
                "Near",
                f.state.devices
                    .first()
                    .name,
            )
        }

    @Test
    fun `stop keeps the devices and returns to idle`() =
        runTest {
            val scanner = ManualScanner()
            val f = fixture(scanner)
            f.vm.toggleScan()
            advance(100)
            scanner.ads.emit(rawAd("A"))
            advance(1_500)
            f.vm.toggleScan()
            advance(0)
            assertEquals(ListStatus.Idle, f.state.status)
            assertEquals(1, f.state.devices.size)
        }

    @Test
    fun `rapid toggles within a second are ignored`() =
        runTest {
            val f = fixture()
            f.vm.toggleScan()
            advance(100)
            f.vm.toggleScan()
            advance(100)
            assertEquals("second tap ignored", ListStatus.Scanning, f.state.status)
            advance(1_000)
            f.vm.toggleScan()
            advance(0)
            assertEquals(ListStatus.Idle, f.state.status)
        }

    @Test
    fun `hide unnamed filters the list`() =
        runTest {
            val scanner = ManualScanner()
            val f = fixture(scanner)
            f.vm.toggleScan()
            advance(100)
            scanner.ads.emit(rawAd("A", name = "Named"))
            scanner.ads.emit(rawAd("B"))
            advance(500)
            assertEquals(2, f.state.devices.size)
            f.vm.setHideUnnamed(true)
            advance(0)
            assertEquals(listOf("A"), f.state.devices.map { it.address })
            assertTrue(f.state.hideUnnamed)
        }

    @Test
    fun `clear empties the list, resets the scanned flag and keeps scanning`() =
        runTest {
            val scanner = ManualScanner()
            val f = fixture(scanner)
            f.vm.toggleScan()
            advance(100)
            scanner.ads.emit(rawAd("A"))
            advance(500)
            f.vm.clear()
            advance(0)
            assertTrue(f.state.devices.isEmpty())
            assertFalse(f.state.hasScanned)
            assertEquals(ListStatus.Scanning, f.state.status)
        }

    @Test
    fun `backgrounding stops the scan but keeps devices`() =
        runTest {
            val scanner = ManualScanner()
            val f = fixture(scanner)
            f.vm.toggleScan()
            advance(100)
            scanner.ads.emit(rawAd("A"))
            advance(500)
            f.vm.onBackgrounded()
            advance(0)
            assertEquals(ListStatus.Idle, f.state.status)
            assertEquals(1, f.state.devices.size)
        }

    @Test
    fun `scan failure is shown and retry starts a new scan`() =
        runTest {
            val f = fixture(FakeBleScanner(Scenarios.require("scan-fails"), testClock()))
            f.vm.toggleScan()
            advance(2_500)
            assertEquals(ListStatus.Failed(ScanFailure.Kind.INTERNAL_ERROR), f.state.status)
            assertTrue("failed state offers retry", f.state.canToggleScan)
            f.vm.toggleScan()
            advance(100)
            assertEquals(ListStatus.Scanning, f.state.status)
        }

    @Test
    fun `signal levels follow the documented thresholds`() {
        assertEquals(4, signalLevel(-40))
        assertEquals(4, signalLevel(-55))
        assertEquals(3, signalLevel(-56))
        assertEquals(3, signalLevel(-67))
        assertEquals(2, signalLevel(-68))
        assertEquals(2, signalLevel(-80))
        assertEquals(1, signalLevel(-81))
        assertEquals(1, signalLevel(-100))
    }
}
