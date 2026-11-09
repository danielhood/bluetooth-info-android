package dev.danielhood.btscan.ui.detail

import dev.danielhood.btscan.data.ble.fake.FakeBleScanner
import dev.danielhood.btscan.data.ble.fake.Scenarios
import dev.danielhood.btscan.domain.repo.DefaultDeviceRepository
import dev.danielhood.btscan.domain.repo.DeviceRepository
import dev.danielhood.btscan.testing.ManualScanner
import dev.danielhood.btscan.testing.advance
import dev.danielhood.btscan.testing.rawAd
import dev.danielhood.btscan.testing.testClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
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

class DeviceDetailViewModelTest {
    @Before
    fun setUp() = Dispatchers.setMain(StandardTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    /** Creates the ViewModel and observes its state (the age ticker only runs while observed). */
    private fun TestScope.vm(address: String, repo: DeviceRepository) =
        DeviceDetailViewModel(address, repo, testClock()).also {
            backgroundScope.launch { it.uiState.collect {} }
            advance(0)
        }

    private fun TestScope.defaultScenarioRepo() =
        DefaultDeviceRepository(
            FakeBleScanner(Scenarios.require("default"), testClock()),
            testClock(),
            backgroundScope,
        )

    private fun DeviceDetailViewModel.loaded() = uiState.value as DeviceDetailUiState.Loaded

    @Test
    fun `unknown address is not found`() =
        runTest {
            val repo = DefaultDeviceRepository(ManualScanner(), testClock(), backgroundScope)
            assertEquals(DeviceDetailUiState.NotFound, vm("nope", repo).uiState.value)
        }

    @Test
    fun `known device is loaded with its formatted fields`() =
        runTest {
            val repo = defaultScenarioRepo()
            repo.startScan()
            advance(1_000)
            val address =
                repo.devices.value
                    .first { it.name == "Thermo Sensor" }
                    .address
            val loaded = vm(address, repo).loaded()
            assertFalse(loaded.outOfRange)
            assertEquals("Thermo Sensor", loaded.device.name)
            assertEquals(-4, loaded.device.txPowerDbm)
            assertEquals(listOf("Environmental Sensing", "Battery"), loaded.device.services.map { it.name })
            assertEquals("0x06", loaded.device.flagsHex)
            assertTrue(loaded.device.rawHex.startsWith("02 01 06"))
        }

    @Test
    fun `manufacturer data shows company id, name and payload`() =
        runTest {
            val repo = defaultScenarioRepo()
            repo.startScan()
            advance(1_000)
            val address =
                repo.devices.value
                    .first { it.name == "HR Strap" }
                    .address
            val m =
                vm(address, repo)
                    .loaded()
                    .device.manufacturers
                    .single()
            assertEquals("0x0059", m.companyIdHex)
            assertEquals("Nordic Semiconductor ASA", m.companyName)
            assertEquals("01 02 03", m.payloadHex)
        }

    @Test
    fun `rssi updates live`() =
        runTest {
            val scanner = ManualScanner()
            val repo = DefaultDeviceRepository(scanner, testClock(), backgroundScope)
            repo.startScan()
            advance(0)
            scanner.ads.emit(rawAd("A", rssi = -70))
            advance(500)
            val vm = vm("A", repo)
            assertEquals(-70, vm.loaded().device.rssi)
            scanner.ads.emit(rawAd("A", rssi = -50))
            advance(500)
            assertEquals(-50, vm.loaded().device.rssi)
            assertEquals(2, vm.loaded().device.advertisementCount)
        }

    @Test
    fun `last seen age counts up every second`() =
        runTest {
            val scanner = ManualScanner()
            val repo = DefaultDeviceRepository(scanner, testClock(), backgroundScope)
            repo.startScan()
            advance(0)
            scanner.ads.emit(rawAd("A"))
            advance(500)
            val vm = vm("A", repo)
            advance(3_000)
            assertEquals(3, vm.loaded().lastSeenAgoSeconds)
            assertEquals(3, vm.loaded().firstSeenAgoSeconds)
        }

    @Test
    fun `device that goes out of range keeps its data and flags out of range (acceptance 5)`() =
        runTest {
            val repo = defaultScenarioRepo()
            repo.startScan()
            advance(1_000)
            val address =
                repo.devices.value
                    .first { it.address.endsWith(":04") }
                    .address
            val vm = vm(address, repo)
            assertFalse(vm.loaded().outOfRange)
            advance(75_000)
            val loaded = vm.loaded()
            assertTrue(loaded.outOfRange)
            assertEquals(address, loaded.device.address)
            assertTrue("last seen age: ${loaded.lastSeenAgoSeconds}", loaded.lastSeenAgoSeconds >= 30)
        }

    @Test
    fun `device that returns after dropping is no longer out of range`() =
        runTest {
            val scanner = ManualScanner()
            val repo = DefaultDeviceRepository(scanner, testClock(), backgroundScope)
            repo.startScan()
            advance(0)
            scanner.ads.emit(rawAd("A"))
            advance(500)
            val vm = vm("A", repo)
            advance(31_000)
            assertTrue(vm.loaded().outOfRange)
            scanner.ads.emit(rawAd("A", rssi = -42))
            advance(600)
            assertFalse(vm.loaded().outOfRange)
            assertEquals(-42, vm.loaded().device.rssi)
        }
}
