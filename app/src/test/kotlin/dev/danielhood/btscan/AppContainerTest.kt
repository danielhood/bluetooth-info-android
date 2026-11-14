package dev.danielhood.btscan

import dev.danielhood.btscan.RealPorts
import dev.danielhood.btscan.domain.model.ScanFailure
import dev.danielhood.btscan.domain.port.Clock
import dev.danielhood.btscan.testing.AlwaysOnAdapterMonitor
import dev.danielhood.btscan.testing.FakePermissionChecker
import dev.danielhood.btscan.testing.FakeSystemInfoSource
import dev.danielhood.btscan.testing.ManualScanner
import dev.danielhood.btscan.testing.NoClassicScanner
import dev.danielhood.btscan.testing.rawAd
import dev.danielhood.btscan.testing.testClock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

/** Wiring: the debug launch config selects which scanner the repository uses. */
@OptIn(ExperimentalCoroutinesApi::class)
class AppContainerTest {
    private val realScanner = ManualScanner()

    private fun TestScope.container(): AppContainer {
        val clock =
            object : Clock {
                override fun now(): Instant = Instant.EPOCH.plusMillis(testScheduler.currentTime)
            }
        return AppContainer(
            permissionChecker = FakePermissionChecker(),
            real = RealPorts(realScanner, AlwaysOnAdapterMonitor, FakeSystemInfoSource(), NoClassicScanner),
            scope = backgroundScope,
            clock = clock,
        )
    }

    private fun TestScope.advance(ms: Long) {
        testScheduler.advanceTimeBy(ms)
        testScheduler.runCurrent()
    }

    @Test
    fun `fake scanner config feeds the repository with the chosen scenario`() =
        runTest {
            val container = container()
            container.debugConfig = DebugConfig(scanner = "fake", scenario = "default")
            container.deviceRepository.startScan()
            advance(2_000)
            assertEquals(5, container.deviceRepository.devices.value.size)
        }

    @Test
    fun `empty scenario keeps scanning with no devices`() =
        runTest {
            val container = container()
            container.debugConfig = DebugConfig(scanner = "fake", scenario = "empty")
            container.deviceRepository.startScan()
            advance(2_000)
            assertEquals(0, container.deviceRepository.devices.value.size)
            assertEquals(true, container.deviceRepository.scanning.value)
        }

    @Test
    fun `without a debug config the injected real scanner is used`() =
        runTest {
            val container = container()
            container.deviceRepository.startScan()
            advance(100)
            realScanner.ads.emit(rawAd("REAL-1", name = "Real Device"))
            advance(600)
            assertEquals(
                listOf("REAL-1"),
                container.deviceRepository.devices.value
                    .map { it.address },
            )
        }

    @Test
    fun `a failing real scanner surfaces as a scan failure`() =
        runTest {
            val failing =
                object : dev.danielhood.btscan.domain.port.BleScanner {
                    override fun scan() =
                        kotlinx.coroutines.flow.flow<dev.danielhood.btscan.domain.model.RawAdvertisement> {
                            throw ScanFailure(ScanFailure.Kind.PERMISSION_MISSING)
                        }
                }
            val container =
                AppContainer(
                    permissionChecker = FakePermissionChecker(),
                    real = RealPorts(failing, AlwaysOnAdapterMonitor, FakeSystemInfoSource(), NoClassicScanner),
                    scope = backgroundScope,
                    clock = testClock(),
                )
            container.deviceRepository.startScan()
            advance(100)
            assertEquals(
                ScanFailure.Kind.PERMISSION_MISSING,
                container.deviceRepository.failure.value
                    ?.kind,
            )
        }
}
