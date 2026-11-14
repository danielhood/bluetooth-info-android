package dev.danielhood.btscan

import dev.danielhood.btscan.data.SystemClock
import dev.danielhood.btscan.domain.model.AdapterState
import dev.danielhood.btscan.domain.model.ClassicSighting
import dev.danielhood.btscan.domain.model.RawAdvertisement
import dev.danielhood.btscan.domain.port.AdapterStateMonitor
import dev.danielhood.btscan.domain.port.BleScanner
import dev.danielhood.btscan.domain.port.ClassicScanner
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

/** The real (platform) implementations of the ports; the debug seam may replace each at use time. */
class RealPorts(
    val scanner: BleScanner,
    val adapterMonitor: AdapterStateMonitor,
    val systemInfo: SystemDeviceInfoSource,
    val classicScanner: ClassicScanner,
)

/** Manual dependency injection root. The only place allowed to see every layer. */
class AppContainer(
    val permissionChecker: PermissionChecker,
    private val real: RealPorts,
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
                    val chosen = debugOverrides.scannerFor(debugConfig, clock) ?: real.scanner
                    emitAll(chosen.scan())
                }
        }

    /** Resolved at collection time for the same reason as [scanner]. */
    val adapterStateMonitor: AdapterStateMonitor =
        object : AdapterStateMonitor {
            override val state: Flow<AdapterState> =
                flow {
                    val chosen = debugOverrides.adapterMonitorFor(debugConfig) ?: real.adapterMonitor
                    emitAll(chosen.state)
                }
        }

    /** Resolved at lookup time for the same reason as [scanner]. */
    val systemDeviceInfoSource: SystemDeviceInfoSource =
        object : SystemDeviceInfoSource {
            override suspend fun lookup(address: String): SystemInfoResult =
                (debugOverrides.systemInfoSourceFor(debugConfig, permissionChecker) ?: real.systemInfo).lookup(address)
        }

    /** Resolves the classic scanner at inquiry time, like [scanner]. */
    private val classicScanner =
        object : ClassicScanner {
            override fun discover(): Flow<ClassicSighting> =
                flow {
                    val chosen = debugOverrides.classicScannerFor(debugConfig, clock) ?: real.classicScanner
                    emitAll(chosen.discover())
                }
        }

    val deviceRepository: DeviceRepository = DefaultDeviceRepository(scanner, classicScanner, clock, scope)
}
