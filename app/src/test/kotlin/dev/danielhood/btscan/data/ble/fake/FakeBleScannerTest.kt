package dev.danielhood.btscan.data.ble.fake

import dev.danielhood.btscan.domain.model.RawAdvertisement
import dev.danielhood.btscan.domain.model.ScanFailure
import dev.danielhood.btscan.domain.parser.AdvertisementParser
import dev.danielhood.btscan.domain.port.Clock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class FakeBleScannerTest {
    private fun TestScope.clock() =
        object : Clock {
            override fun now(): Instant = Instant.EPOCH.plusMillis(testScheduler.currentTime)
        }

    private fun TestScope.collect(
        scenario: String,
        untilMs: Long,
        failures: MutableList<ScanFailure> = mutableListOf(),
    ): List<RawAdvertisement> {
        val out = mutableListOf<RawAdvertisement>()
        backgroundScope.launch {
            FakeBleScanner(Scenarios.require(scenario), clock())
                .scan()
                .catch { if (it is ScanFailure) failures += it else throw it }
                .collect { out += it }
        }
        testScheduler.advanceTimeBy(untilMs)
        testScheduler.runCurrent()
        return out.toList()
    }

    @Test
    fun `every named scenario from the spec exists`() {
        val expected =
            setOf(
                "default",
                "empty",
                "many",
                "sparse-fields",
                "beacons",
                "sensors",
                "vendors",
                "radio",
                "scan-fails",
                "too-frequent",
                "bluetooth-off",
                "unsupported",
            )
        assertEquals(expected, Scenarios.all.keys)
    }

    @Test
    fun `unknown scenario fails loudly and lists the valid names`() {
        try {
            Scenarios.require("nope")
            fail("expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("default"))
        }
    }

    @Test
    fun `default scenario shows five devices by 2s and the sixth arrives at 3s`() =
        runTest {
            val at2 = collect("default", 2_000).map { it.address }.distinct()
            assertEquals(5, at2.size)
        }

    @Test
    fun `default scenario late device appears at three seconds`() =
        runTest {
            val ads = collect("default", 3_600)
            assertEquals(6, ads.map { it.address }.distinct().size)
            val late = ads.filter { it.address.endsWith(":06") }
            assertEquals(3_000, late.first().timestamp.toEpochMilli())
        }

    @Test
    fun `default scenario device four vanishes at forty seconds`() =
        runTest {
            val ads = collect("default", 45_000).filter { it.address.endsWith(":04") }
            assertTrue(ads.isNotEmpty())
            assertTrue(ads.last().timestamp.toEpochMilli() < 40_000)
        }

    @Test
    fun `output is deterministic`() =
        runTest {
            val first = collect("default", 5_000)
            val second = TestScope().collect("default", 5_000)
            assertEquals(first.map { it.address to it.rssi }, second.map { it.address to it.rssi })
        }

    @Test
    fun `empty scenario emits nothing`() =
        runTest {
            assertTrue(collect("empty", 10_000).isEmpty())
        }

    @Test
    fun `many scenario has 200 distinct devices after one second`() =
        runTest {
            assertEquals(200, collect("many", 1_000).map { it.address }.distinct().size)
        }

    @Test
    fun `scan-fails scenario throws internal error after two seconds`() =
        runTest {
            val failures = mutableListOf<ScanFailure>()
            backgroundScope.launch {
                FakeBleScanner(Scenarios.require("scan-fails"), clock())
                    .scan()
                    .catch { if (it is ScanFailure) failures += it else throw it }
                    .collect { }
            }
            testScheduler.advanceTimeBy(1_900)
            testScheduler.runCurrent()
            assertTrue(failures.isEmpty())
            testScheduler.advanceTimeBy(300)
            testScheduler.runCurrent()
            assertEquals(ScanFailure.Kind.INTERNAL_ERROR, failures.single().kind)
        }

    @Test
    fun `too-frequent scenario throws immediately`() =
        runTest {
            val failures = mutableListOf<ScanFailure>()
            collect("too-frequent", 0, failures)
            assertEquals(ScanFailure.Kind.SCANNING_TOO_FREQUENTLY, failures.single().kind)
        }

    @Test
    fun `every scenario payload parses without throwing`() =
        runTest {
            Scenarios.all.keys.filter { it !in setOf("scan-fails", "too-frequent") }.forEach { name ->
                collect(name, 2_000).forEach { AdvertisementParser.parse(it.bytes) }
            }
        }

    @Test
    fun `default scenario carries the advertised fields the detail screen needs`() =
        runTest {
            val devices =
                collect("default", 1_000)
                    .associateBy {
                        it.address
                    }.mapValues { AdvertisementParser.parse(it.value.bytes) }
            val thermo = devices.values.first { it.localName == "Thermo Sensor" }
            assertEquals(2, thermo.serviceUuids.size)
            assertEquals(-4, thermo.txPowerDbm)
            assertNotNull(devices.values.first { it.manufacturerData.isNotEmpty() })
        }

    @Test
    fun `adapter state monitors reflect the scenario`() {
        assertEquals(
            dev.danielhood.btscan.domain.model.AdapterState.OFF,
            Scenarios.require("bluetooth-off").adapterState,
        )
        assertEquals(
            dev.danielhood.btscan.domain.model.AdapterState.UNSUPPORTED,
            Scenarios.require("unsupported").adapterState,
        )
    }
}
