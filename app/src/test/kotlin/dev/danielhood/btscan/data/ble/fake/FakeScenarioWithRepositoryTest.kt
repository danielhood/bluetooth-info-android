package dev.danielhood.btscan.data.ble.fake

import dev.danielhood.btscan.domain.decode.Confidence
import dev.danielhood.btscan.domain.decode.DecoderId
import dev.danielhood.btscan.domain.decode.FieldKey
import dev.danielhood.btscan.domain.decode.FieldValue
import dev.danielhood.btscan.domain.estimate.Estimates
import dev.danielhood.btscan.domain.model.AddressType
import dev.danielhood.btscan.domain.model.DataStatus
import dev.danielhood.btscan.domain.model.Phy
import dev.danielhood.btscan.domain.model.ScanFailure
import dev.danielhood.btscan.domain.port.Clock
import dev.danielhood.btscan.domain.repo.DefaultDeviceRepository
import dev.danielhood.btscan.testing.NoClassicScanner
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/** Repository driven by the real fake scanner on virtual time: acceptance criteria 1 and 5. */
@OptIn(ExperimentalCoroutinesApi::class)
class FakeScenarioWithRepositoryTest {
    private fun TestScope.repoFor(scenario: String): DefaultDeviceRepository {
        val clock =
            object : Clock {
                override fun now(): Instant = Instant.EPOCH.plusMillis(testScheduler.currentTime)
            }
        return DefaultDeviceRepository(
            FakeBleScanner(Scenarios.require(scenario), clock),
            NoClassicScanner,
            clock,
            backgroundScope,
        )
    }

    private fun TestScope.advance(ms: Long) {
        testScheduler.advanceTimeBy(ms)
        testScheduler.runCurrent()
    }

    @Test
    fun `default scenario lists five devices by two seconds ordered by signal and six after three`() =
        runTest {
            val repo = repoFor("default")
            repo.startScan()
            advance(2_000)
            assertEquals(5, repo.devices.value.size)
            assertEquals(
                repo.devices.value
                    .map { it.rssi }
                    .sortedDescending(),
                repo.devices.value.map { it.rssi },
            )
            advance(1_600)
            assertEquals(6, repo.devices.value.size)
        }

    @Test
    fun `the vanishing device is removed thirty seconds after it stops advertising`() =
        runTest {
            val repo = repoFor("default")
            repo.startScan()
            advance(60_000)
            assertEquals(6, repo.devices.value.size)
            advance(11_000)
            assertEquals(5, repo.devices.value.size)
            assertTrue(repo.devices.value.none { it.address.endsWith(":04") })
        }

    @Test
    fun `detail flow for a removed device emits null`() =
        runTest {
            val repo = repoFor("default")
            repo.startScan()
            advance(1_000)
            val address =
                repo.devices.value
                    .first { it.address.endsWith(":04") }
                    .address
            advance(80_000)
            val seen = mutableListOf<Any?>()
            backgroundScope.launch { repo.device(address).collect { seen += it } }
            advance(100)
            assertEquals(listOf<Any?>(null), seen)
        }

    @Test
    fun `beacons scenario decodes the ibeacon and accumulates alternating eddystone frames`() =
        runTest {
            val repo = repoFor("beacons")
            repo.startScan()
            advance(3_000)
            val byAddress = repo.devices.value.associateBy { it.address.takeLast(2) }
            assertEquals(listOf(DecoderId.IBEACON), byAddress.getValue("01").decoded.map { it.decoder })
            assertEquals(
                setOf(DecoderId.EDDYSTONE_UID, DecoderId.EDDYSTONE_URL, DecoderId.EDDYSTONE_TLM),
                byAddress
                    .getValue("02")
                    .decoded
                    .map { it.decoder }
                    .toSet(),
            )
            assertEquals(listOf(DecoderId.EDDYSTONE_EID), byAddress.getValue("03").decoded.map { it.decoder })
            assertEquals(listOf(DecoderId.ALTBEACON), byAddress.getValue("04").decoded.map { it.decoder })
        }

    @Test
    fun `sensors scenario decodes ruuvi and bthome and flags the encrypted frame`() =
        runTest {
            val repo = repoFor("sensors")
            repo.startScan()
            advance(2_000)
            val byAddress = repo.devices.value.associateBy { it.address.takeLast(2) }
            val ruuvi = byAddress.getValue("01").decoded.single()
            assertEquals(DecoderId.RUUVI_V5, ruuvi.decoder)
            assertEquals(
                24.3,
                (ruuvi.fields.first { it.key == FieldKey.TEMPERATURE_C }.value as FieldValue.Number).value,
                1e-9,
            )
            assertEquals(
                DecoderId.BTHOME,
                byAddress
                    .getValue("02")
                    .decoded
                    .single()
                    .decoder,
            )
            assertEquals(
                Confidence.SPECIFICATION,
                byAddress
                    .getValue("02")
                    .decoded
                    .single()
                    .confidence,
            )
            assertEquals(
                Confidence.ENCRYPTED,
                byAddress
                    .getValue("03")
                    .decoded
                    .single()
                    .confidence,
            )
        }

    @Test
    fun `vendors scenario produces hints with the right wording class and nothing for the plain device`() =
        runTest {
            val repo = repoFor("vendors")
            repo.startScan()
            advance(3_000)
            val byAddress = repo.devices.value.associateBy { it.address.takeLast(2) }
            assertEquals(9, byAddress.size)

            fun ids(n: String) = byAddress.getValue(n).decoded.map { it.decoder }
            assertEquals(listOf(DecoderId.APPLE_CONTINUITY, DecoderId.TRACKER_HINT), ids("01"))
            assertEquals(listOf(DecoderId.APPLE_CONTINUITY), ids("02"))
            assertEquals(listOf(DecoderId.APPLE_CONTINUITY), ids("03"))
            assertEquals(listOf(DecoderId.APPLE_CONTINUITY), ids("04"))
            assertEquals(listOf(DecoderId.FAST_PAIR), ids("05"))
            assertEquals(listOf(DecoderId.FAST_PAIR), ids("06"))
            assertEquals(listOf(DecoderId.TRACKER_HINT), ids("07"))
            assertEquals(listOf(DecoderId.TRACKER_HINT), ids("08"))
            assertEquals(emptyList<DecoderId>(), ids("09"))
            assertTrue(
                byAddress.values
                    .flatMap { it.decoded }
                    .filter {
                        it.decoder in
                            setOf(DecoderId.APPLE_CONTINUITY, DecoderId.TRACKER_HINT)
                    }.all { it.confidence == Confidence.UNOFFICIAL_HINT },
            )
        }

    @Test
    fun `radio scenario reports phy and set data, omits unavailable values and estimates intervals`() =
        runTest {
            val repo = repoFor("radio")
            repo.startScan()
            advance(6_000)
            val byAddress = repo.devices.value.associateBy { it.address.takeLast(2) }
            val extended = byAddress.getValue("01").radio!!
            assertEquals(Phy.LE_2M, extended.secondaryPhy)
            assertEquals(3, extended.advertisingSid)
            assertEquals(80, extended.periodicIntervalUnits)
            val coded = byAddress.getValue("02").radio!!
            assertNull(coded.txPowerDbm)
            assertEquals(DataStatus.TRUNCATED, byAddress.getValue("03").radio!!.dataStatus)
            assertEquals(true, byAddress.getValue("04").radio!!.isLegacy)
            assertEquals(AddressType.RANDOM, byAddress.getValue("01").addressType)
            assertEquals(AddressType.PUBLIC, byAddress.getValue("02").addressType)
            assertEquals(AddressType.ANONYMOUS, byAddress.getValue("03").addressType)
            assertEquals(500L, Estimates.advertisingIntervalMs(byAddress.getValue("01")))
            assertEquals(1000L, Estimates.advertisingIntervalMs(byAddress.getValue("04")))
            assertEquals(100L, Estimates.advertisingIntervalMs(byAddress.getValue("05")))
            assertNotNull(Estimates.distanceBand(byAddress.getValue("05")))
            assertNull(Estimates.distanceBand(byAddress.getValue("01")))
        }

    @Test
    fun `many scenario stays at two hundred devices while scanning`() =
        runTest {
            val repo = repoFor("many")
            repo.startScan()
            advance(20_000)
            assertEquals(200, repo.devices.value.size)
        }

    @Test
    fun `scan-fails scenario ends in a failed state with devices retained`() =
        runTest {
            val repo = repoFor("scan-fails")
            repo.startScan()
            advance(2_500)
            assertEquals(ScanFailure.Kind.INTERNAL_ERROR, repo.failure.value?.kind)
            assertEquals(false, repo.scanning.value)
            assertEquals(2, repo.devices.value.size)
        }
}
