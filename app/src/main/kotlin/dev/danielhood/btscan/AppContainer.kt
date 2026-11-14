package dev.danielhood.btscan

import dev.danielhood.btscan.data.SystemClock
import dev.danielhood.btscan.domain.model.AdapterState
import dev.danielhood.btscan.domain.model.RawAdvertisement
import dev.danielhood.btscan.domain.port.AdapterStateMonitor
import dev.danielhood.btscan.domain.port.BleScanner
import dev.danielhood.btscan.domain.port.Clock
import dev.danielhood.btscan.domain.port.PermissionChecker
import dev.danielhood.btscan.domain.port.SystemDeviceInfoSource
import dev.danielhood.btscan.domain.repo.DefaultDeviceRepository
import dev.danielhood.btscan.domain.repo.DeviceRepository
import dev.danielhood.btscan.domain.system.SystemInfoResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow

/** Manual dependency injection root. The only place allowed to see every layer. */
class AppContainer(
    val permissionChecker: PermissionChecker,
    private val realScanner: BleScanner,
    private val realAdapterMonitor: AdapterStateMonitor,
    private val realSystemInfo: SystemDeviceInfoSource,
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
                    val chosen = debugOverrides.scannerFor(debugConfig, clock) ?: realScanner
                    emitAll(chosen.scan())
                }
        }

    /** Resolved at collection time for the same reason as [scanner]. */
    val adapterStateMonitor: AdapterStateMonitor =
        object : AdapterStateMonitor {
            override val state: Flow<AdapterState> =
                flow {
                    val chosen = debugOverrides.adapterMonitorFor(debugConfig) ?: realAdapterMonitor
                    emitAll(chosen.state)
                }
        }

    /** Resolved at lookup time for the same reason as [scanner]. */
    val systemDeviceInfoSource: SystemDeviceInfoSource =
        object : SystemDeviceInfoSource {
            override suspend fun lookup(address: String): SystemInfoResult =
                (debugOverrides.systemInfoSourceFor(debugConfig, permissionChecker) ?: realSystemInfo).lookup(address)
        }

    val deviceRepository: DeviceRepository = DefaultDeviceRepository(scanner, clock, scope)
}
