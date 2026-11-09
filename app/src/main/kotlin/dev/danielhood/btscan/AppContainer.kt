package dev.danielhood.btscan

import dev.danielhood.btscan.data.SystemClock
import dev.danielhood.btscan.data.ble.UnavailableBleScanner
import dev.danielhood.btscan.domain.model.RawAdvertisement
import dev.danielhood.btscan.domain.port.BleScanner
import dev.danielhood.btscan.domain.port.Clock
import dev.danielhood.btscan.domain.repo.DefaultDeviceRepository
import dev.danielhood.btscan.domain.repo.DeviceRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow

/** Manual dependency injection root. The only place allowed to see every layer. */
class AppContainer(
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    val clock: Clock = SystemClock,
) {
    val debugOverrides: DebugOverrides = DebugOverridesImpl()

    /** Set by MainActivity from the launch intent; always [DebugConfig.NONE] in release builds. */
    @Volatile
    var debugConfig: DebugConfig = DebugConfig.NONE

    /** Resolves the scanner at scan time so the launch config set after construction is honoured. */
    private val scanner =
        object : BleScanner {
            override fun scan(): Flow<RawAdvertisement> =
                flow {
                    val chosen = debugOverrides.scannerFor(debugConfig, clock) ?: UnavailableBleScanner
                    emitAll(chosen.scan())
                }
        }

    val deviceRepository: DeviceRepository = DefaultDeviceRepository(scanner, clock, scope)
}
