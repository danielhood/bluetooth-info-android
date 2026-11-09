package dev.danielhood.btscan.domain.repo

import dev.danielhood.btscan.domain.model.Bytes
import dev.danielhood.btscan.domain.model.ManufacturerData
import dev.danielhood.btscan.domain.model.RawAdvertisement
import dev.danielhood.btscan.domain.model.ScanFailure
import dev.danielhood.btscan.domain.port.BleScanner
import dev.danielhood.btscan.domain.port.Clock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultDeviceRepositoryTest {
    private val flagsAndName = { name: String -> hex("02 01 06") + nameStructure(name) }

    private fun hex(s: String): ByteArray =
        s
            .replace(" ", "")
            .chunked(2)
            .map { it.toInt(16).toByte() }
            .toByteArray()

    private fun nameStructure(name: String): ByteArray {
        val n = name.toByteArray()
        return byteArrayOf((n.size + 1).toByte(), 0x09) + n
    }

    private class ManualScanner : BleScanner {
        val ads = MutableSharedFlow<RawAdvertisement>(extraBufferCapacity = 1024)
        var collectors = 0
        var active = 0

        override fun scan(): Flow<RawAdvertisement> =
            ads
                .onStart {
                    collectors++
                    active++
                }.onCompletion { active-- }
    }

    /** Advances virtual time and also runs tasks scheduled exactly at the new time. */
    private fun TestScope.advance(ms: Long) {
        testScheduler.advanceTimeBy(ms)
        testScheduler.runCurrent()
    }

    private fun TestScope.clock() =
        object : Clock {
            override fun now(): Instant = Instant.EPOCH.plusMillis(testScheduler.currentTime)
        }

    private fun TestScope.ad(
        address: String,
        rssi: Int = -60,
        bytes: ByteArray = ByteArray(0),
        connectable: Boolean = true,
    ) = RawAdvertisement(address, rssi, Bytes(bytes), connectable, Instant.EPOCH.plusMillis(testScheduler.currentTime))

    private fun TestScope.repo(scanner: BleScanner) = DefaultDeviceRepository(scanner, clock(), backgroundScope)

    @Test
    fun `aggregates advertisements per address keeping the latest rssi and counting them`() =
        runTest {
            val scanner = ManualScanner()
            val repo = repo(scanner)
            repo.startScan()
            runCurrent()
            scanner.ads.emit(ad("A", rssi = -70))
            advance(100)
            scanner.ads.emit(ad("A", rssi = -55))
            advance(500)
            val device = repo.devices.value.single()
            assertEquals(-55, device.rssi)
            assertEquals(2, device.advertisementCount)
            assertEquals(Instant.EPOCH, device.firstSeen)
            assertEquals(Instant.EPOCH.plusMillis(100), device.lastSeen)
        }

    @Test
    fun `merge keeps the last non-null name and unions uuids and replaces manufacturer data per company`() =
        runTest {
            val scanner = ManualScanner()
            val repo = repo(scanner)
            repo.startScan()
            runCurrent()
            // first packet: name + service 0x180D + manufacturer 0x004C
            scanner.ads.emit(ad("A", bytes = flagsAndName("Dev") + hex("03 03 0D 18") + hex("04 FF 4C 00 01")))
            // second packet (scan response style): no name, service 0x180F, manufacturer 0x004C updated
            scanner.ads.emit(ad("A", bytes = hex("03 03 0F 18") + hex("04 FF 4C 00 02")))
            advance(500)
            val d = repo.devices.value.single()
            assertEquals("Dev", d.name)
            assertEquals(2, d.serviceUuids.size)
            assertEquals(listOf(ManufacturerData(0x004C, Bytes(byteArrayOf(2)))), d.manufacturerData)
        }

    @Test
    fun `devices are sorted by rssi descending with address as tie-break`() =
        runTest {
            val scanner = ManualScanner()
            val repo = repo(scanner)
            repo.startScan()
            runCurrent()
            scanner.ads.emit(ad("C", rssi = -80))
            scanner.ads.emit(ad("B", rssi = -50))
            scanner.ads.emit(ad("A", rssi = -50))
            advance(500)
            assertEquals(listOf("A", "B", "C"), repo.devices.value.map { it.address })
        }

    @Test
    fun `snapshot is throttled to one rebuild per interval`() =
        runTest {
            val scanner = ManualScanner()
            val repo = repo(scanner)
            repo.startScan()
            runCurrent()
            val seen = mutableListOf<Int>()
            backgroundScope.launch { repo.devices.collect { seen += it.size } }
            runCurrent()
            scanner.ads.emit(ad("A"))
            advance(100)
            scanner.ads.emit(ad("B"))
            advance(100)
            assertEquals("nothing published before the interval elapses", listOf(0), seen)
            advance(300)
            assertEquals(listOf(0, 2), seen)
        }

    @Test
    fun `stale devices are removed after thirty seconds while scanning`() =
        runTest {
            val scanner = ManualScanner()
            val repo = repo(scanner)
            repo.startScan()
            runCurrent()
            scanner.ads.emit(ad("A"))
            advance(29_900)
            assertEquals(1, repo.devices.value.size)
            advance(600)
            assertTrue(repo.devices.value.isEmpty())
        }

    @Test
    fun `a device heard again is not removed`() =
        runTest {
            val scanner = ManualScanner()
            val repo = repo(scanner)
            repo.startScan()
            runCurrent()
            scanner.ads.emit(ad("A"))
            advance(20_000)
            scanner.ads.emit(ad("A"))
            advance(20_000)
            assertEquals(1, repo.devices.value.size)
        }

    @Test
    fun `devices are kept after stop and not pruned while stopped`() =
        runTest {
            val scanner = ManualScanner()
            val repo = repo(scanner)
            repo.startScan()
            runCurrent()
            scanner.ads.emit(ad("A"))
            advance(600)
            repo.stopScan()
            runCurrent()
            advance(120_000)
            assertEquals(listOf("A"), repo.devices.value.map { it.address })
            assertFalse(repo.scanning.value)
            assertEquals(0, scanner.active)
        }

    @Test
    fun `stopping publishes the latest state immediately`() =
        runTest {
            val scanner = ManualScanner()
            val repo = repo(scanner)
            repo.startScan()
            runCurrent()
            scanner.ads.emit(ad("A"))
            advance(100)
            repo.stopScan()
            runCurrent()
            assertEquals(1, repo.devices.value.size)
        }

    @Test
    fun `clear forgets devices but scanning continues`() =
        runTest {
            val scanner = ManualScanner()
            val repo = repo(scanner)
            repo.startScan()
            runCurrent()
            scanner.ads.emit(ad("A"))
            advance(600)
            repo.clear()
            assertTrue(repo.devices.value.isEmpty())
            assertTrue(repo.scanning.value)
            scanner.ads.emit(ad("B"))
            advance(600)
            assertEquals(listOf("B"), repo.devices.value.map { it.address })
        }

    @Test
    fun `starting twice collects the scanner only once`() =
        runTest {
            val scanner = ManualScanner()
            val repo = repo(scanner)
            repo.startScan()
            repo.startScan()
            runCurrent()
            assertEquals(1, scanner.collectors)
            assertTrue(repo.scanning.value)
        }

    @Test
    fun `restart after stop collects again and scanning ends up true`() =
        runTest {
            val scanner = ManualScanner()
            val repo = repo(scanner)
            repo.startScan()
            runCurrent()
            repo.stopScan()
            repo.startScan()
            runCurrent()
            assertEquals(2, scanner.collectors)
            assertEquals(1, scanner.active)
            assertTrue(repo.scanning.value)
        }

    @Test
    fun `scanner failure is exposed and stops scanning`() =
        runTest {
            val failing =
                object : BleScanner {
                    override fun scan(): Flow<RawAdvertisement> =
                        flow {
                            throw ScanFailure(ScanFailure.Kind.INTERNAL_ERROR, 3)
                        }
                }
            val repo = repo(failing)
            repo.startScan()
            runCurrent()
            assertFalse(repo.scanning.value)
            assertEquals(ScanFailure.Kind.INTERNAL_ERROR, repo.failure.value?.kind)
        }

    @Test
    fun `failure is cleared when a new scan starts`() =
        runTest {
            var fail = true
            val scanner =
                object : BleScanner {
                    override fun scan(): Flow<RawAdvertisement> =
                        flow {
                            if (fail) throw ScanFailure(ScanFailure.Kind.SCANNING_TOO_FREQUENTLY)
                            kotlinx.coroutines.awaitCancellation()
                        }
                }
            val repo = repo(scanner)
            repo.startScan()
            runCurrent()
            assertNotNull(repo.failure.value)
            fail = false
            repo.startScan()
            runCurrent()
            assertNull(repo.failure.value)
            assertTrue(repo.scanning.value)
        }

    @Test
    fun `device flow emits the device then null once it goes stale`() =
        runTest {
            val scanner = ManualScanner()
            val repo = repo(scanner)
            repo.startScan()
            runCurrent()
            val seen = mutableListOf<String?>()
            backgroundScope.launch { repo.device("A").collect { seen += it?.address } }
            runCurrent()
            scanner.ads.emit(ad("A"))
            advance(600)
            advance(30_000)
            assertEquals(listOf(null, "A", null), seen)
        }

    @Test
    fun `unparseable payloads still produce a device`() =
        runTest {
            val scanner = ManualScanner()
            val repo = repo(scanner)
            repo.startScan()
            runCurrent()
            scanner.ads.emit(ad("A", bytes = hex("0A 09 41 42")))
            advance(500)
            val d = repo.devices.value.single()
            assertNull(d.name)
        }
}
