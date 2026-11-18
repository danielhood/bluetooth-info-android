package dev.danielhood.btscan.domain.port

import dev.danielhood.btscan.domain.model.AdapterState
import dev.danielhood.btscan.domain.model.ClassicSighting
import dev.danielhood.btscan.domain.model.PermissionState
import dev.danielhood.btscan.domain.model.RawAdvertisement
import dev.danielhood.btscan.domain.system.ProfileLookupResult
import dev.danielhood.btscan.domain.system.SystemInfoResult
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

    /**
     * Runtime permissions needed to read what the system knows about a device (Tier 2a); empty where none is needed
     * (Android 11 and below). Independent of scanning: the list screen never requires them.
     */
    val connectPermissions: List<String>

    fun connectState(): PermissionState
}

/**
 * Asks one device which profiles it supports (a classic service lookup). Unlike everything else in the app this
 * CONTACTS the device: a short classic session. It is started only from an explicit tap on that device's detail screen
 * after a confirmation naming it (see DeviceDetailViewModel); the caller applies the timeout. Never pairs.
 */
interface ProfileLookup {
    suspend fun fetchProfiles(address: String): ProfileLookupResult
}

/** Reads what the Android system already knows about a device. Never contacts the device and never pairs. */
interface SystemDeviceInfoSource {
    suspend fun lookup(address: String): SystemInfoResult
}

/**
 * Classic Bluetooth discovery (an inquiry). Cold flow: collecting starts one inquiry that ends by itself after about
 * 12 seconds; cancelling the collector stops it. Failures are thrown as ScanFailure. It is started only from an
 * explicit user action (see DeviceListViewModel): it can briefly disturb audio on a connected headset.
 */
interface ClassicScanner {
    fun discover(): Flow<ClassicSighting>
}

/** Injected time source; never call the system clock directly from domain or ViewModel code. */
interface Clock {
    fun now(): Instant
}
