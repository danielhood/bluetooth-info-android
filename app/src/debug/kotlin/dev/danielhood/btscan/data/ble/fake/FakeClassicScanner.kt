package dev.danielhood.btscan.data.ble.fake

import dev.danielhood.btscan.domain.model.ClassicSighting
import dev.danielhood.btscan.domain.port.ClassicScanner
import dev.danielhood.btscan.domain.port.Clock
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Fake classic inquiry: reports each scenario sighting at its time offset, then completes after
 * [FakeClassicSighting.INQUIRY_MS], like the real inquiry that Android ends by itself. Deterministic.
 */
class FakeClassicScanner(
    private val scenario: Scenario,
    private val clock: Clock,
) : ClassicScanner {
    override fun discover(): Flow<ClassicSighting> =
        flow {
            var elapsed = 0L
            scenario.classic.sortedBy { it.atMs }.forEach { fake ->
                delay(fake.atMs - elapsed)
                elapsed = fake.atMs
                emit(
                    ClassicSighting(
                        address = fake.address,
                        name = fake.name,
                        rssi = fake.rssi,
                        deviceClass = fake.deviceClass,
                        bondState = fake.bondState,
                        timestamp = clock.now(),
                    ),
                )
            }
            delay(maxOf(0L, FakeClassicSighting.INQUIRY_MS - elapsed))
        }
}
