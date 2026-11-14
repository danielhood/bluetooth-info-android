package dev.danielhood.btscan.data.ble.fake

import dev.danielhood.btscan.domain.model.DeviceSource
import dev.danielhood.btscan.domain.repo.DefaultDeviceRepository
import dev.danielhood.btscan.testing.ManualScanner
import dev.danielhood.btscan.testing.advance
import dev.danielhood.btscan.testing.testClock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FakeClassicScenarioTest {
    private fun TestScope.repo(): DefaultDeviceRepository {
        val scenario = Scenarios.require("classic-mixed")
        val clock = testClock()
        return DefaultDeviceRepository(
            FakeBleScanner(scenario, clock),
            FakeClassicScanner(scenario, clock),
            clock,
            backgroundScope,
        )
    }

    @Test
    fun `an inquiry finds the classic devices, merges the shared one, and ends by itself`() =
        runTest {
            val repo = repo()
            repo.startScan()
            advance(2_000)
            assertEquals(
                "BLE only so far",
                setOf(DeviceSource.BLE),
                repo.devices.value
                    .map { it.source }
                    .toSet(),
            )
            repo.startClassicDiscovery()
            advance(7_000)
            val byAddress = repo.devices.value.associateBy { it.address.takeLast(2) }
            assertEquals(DeviceSource.BOTH, byAddress.getValue("01").source)
            assertEquals(DeviceSource.BLE, byAddress.getValue("02").source)
            assertEquals(DeviceSource.CLASSIC, byAddress.getValue("0A").source)
            assertEquals("Car Audio", byAddress.getValue("0A").name)
            assertEquals(DeviceSource.CLASSIC, byAddress.getValue("0B").source)
            assertNull("the unnamed laptop stays unnamed", byAddress.getValue("0C").name)
            assertTrue(repo.scanningClassic.value)
            advance(6_000)
            assertFalse("the fake inquiry ends by itself at about 12 s", repo.scanningClassic.value)
            assertEquals(5, repo.devices.value.size)
        }

    @Test
    fun `the fake inquiry honours the real repository rule that both scans never overlap`() =
        runTest {
            val repo = repo()
            repo.startScan()
            advance(500)
            repo.startClassicDiscovery()
            advance(500)
            assertFalse(repo.scanning.value)
            assertTrue(repo.scanningClassic.value)
        }

    @Test
    fun `every scenario without classic sightings simply finds nothing`() =
        runTest {
            val scenario = Scenarios.require("default")
            val clock = testClock()
            val repo =
                DefaultDeviceRepository(ManualScanner(), FakeClassicScanner(scenario, clock), clock, backgroundScope)
            repo.startClassicDiscovery()
            advance(13_000)
            assertTrue(repo.devices.value.isEmpty())
            assertFalse(repo.scanningClassic.value)
        }
}
