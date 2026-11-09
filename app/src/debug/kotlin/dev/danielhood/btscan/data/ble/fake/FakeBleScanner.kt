package dev.danielhood.btscan.data.ble.fake

import dev.danielhood.btscan.domain.model.RawAdvertisement
import dev.danielhood.btscan.domain.model.ScanFailure
import dev.danielhood.btscan.domain.port.BleScanner
import dev.danielhood.btscan.domain.port.Clock
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.math.roundToInt
import kotlin.math.sin

private const val RSSI_DRIFT_DB = 4.0
private const val DRIFT_PERIOD_DIVISOR = 3000.0

/**
 * Plays back a [Scenario] as raw advertisements, emitting exactly what a real scanner would (payload bytes), so
 * parsing, aggregation and UI are exercised identically. Time is the coroutine clock (virtual in tests); RSSI
 * drifts on a deterministic sine wave, so there is no randomness.
 */
class FakeBleScanner(
    private val scenario: Scenario,
    private val clock: Clock,
) : BleScanner {
    override fun scan(): Flow<RawAdvertisement> =
        flow {
            var elapsedMs = 0L
            while (true) {
                scenario.failure?.let { if (elapsedMs >= it.afterMs) throw ScanFailure(it.kind) }
                scenario.devices.forEachIndexed { index, device ->
                    if (isAdvertisingAt(device, elapsedMs)) {
                        emit(
                            RawAdvertisement(
                                address = device.address,
                                rssi = rssiAt(device, index, elapsedMs),
                                bytes = device.advertisement,
                                isConnectable = device.isConnectable,
                                timestamp = clock.now(),
                            ),
                        )
                    }
                }
                delay(TICK_MS)
                elapsedMs += TICK_MS
            }
        }

    private fun isAdvertisingAt(device: FakeDevice, elapsedMs: Long): Boolean {
        val sinceFirstSlot = elapsedMs - device.appearAtMs - device.phaseMs
        val vanished = device.vanishAtMs?.let { elapsedMs >= it } ?: false
        return sinceFirstSlot >= 0 && sinceFirstSlot % device.intervalMs == 0L && !vanished
    }

    private fun rssiAt(device: FakeDevice, index: Int, elapsedMs: Long): Int {
        val wave = sin(elapsedMs / DRIFT_PERIOD_DIVISOR + index)
        return device.baseRssi + (RSSI_DRIFT_DB * wave).roundToInt()
    }

    companion object {
        /** Scenario time resolution. Intervals and phases must be multiples of this. */
        const val TICK_MS = 100L
    }
}
