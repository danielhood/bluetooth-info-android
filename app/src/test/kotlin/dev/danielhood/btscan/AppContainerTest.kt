package dev.danielhood.btscan

import dev.danielhood.btscan.domain.model.ScanFailure
import dev.danielhood.btscan.domain.port.Clock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

/** Wiring: the debug launch config selects which scanner the repository uses. */
@OptIn(ExperimentalCoroutinesApi::class)
class AppContainerTest {
    private fun TestScope.container(): AppContainer {
        val clock =
            object : Clock {
                override fun now(): Instant = Instant.EPOCH.plusMillis(testScheduler.currentTime)
            }
        return AppContainer(scope = backgroundScope, clock = clock)
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
    fun `without a debug config the real scanner slot is used, unavailable until M5`() =
        runTest {
            val container = container()
            container.deviceRepository.startScan()
            advance(100)
            assertEquals(
                ScanFailure.Kind.FEATURE_UNSUPPORTED,
                container.deviceRepository.failure.value
                    ?.kind,
            )
        }
}
