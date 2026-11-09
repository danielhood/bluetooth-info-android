package dev.danielhood.btscan.domain.port

import dev.danielhood.btscan.domain.model.AdapterState
import dev.danielhood.btscan.domain.model.PermissionState
import dev.danielhood.btscan.domain.model.RawAdvertisement
import kotlinx.coroutines.flow.Flow
import java.time.Instant

interface BleScanner {
    /** Cold flow. Collecting starts a scan; cancelling the collector stops it. Failures are thrown as ScanFailure. */
    fun scan(): Flow<RawAdvertisement>
}

interface AdapterStateMonitor {
    val state: Flow<AdapterState>
}

interface PermissionChecker {
    /** Runtime permission names the scan needs on this Android version (for the UI to request). */
    val requiredPermissions: List<String>

    fun current(): PermissionState
}

/** Injected time source; never call the system clock directly from domain or ViewModel code. */
interface Clock {
    fun now(): Instant
}
