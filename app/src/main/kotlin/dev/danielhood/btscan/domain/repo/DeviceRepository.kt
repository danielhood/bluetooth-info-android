package dev.danielhood.btscan.domain.repo

import dev.danielhood.btscan.domain.model.BleDevice
import dev.danielhood.btscan.domain.model.ScanFailure
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface DeviceRepository {
    /** Known devices sorted by RSSI descending (ties by address); rebuilt at most once per snapshot interval. */
    val devices: StateFlow<List<BleDevice>>

    val scanning: StateFlow<Boolean>

    /** True while a classic inquiry runs. Never true together with [scanning]: starting one stops the other. */
    val scanningClassic: StateFlow<Boolean>

    /** Set when the last scan ended with an error; cleared when a new scan starts. */
    val failure: StateFlow<ScanFailure?>

    /** Live view of one device; emits null when it is not (or no longer) known. */
    fun device(address: String): Flow<BleDevice?>

    /** Starts scanning if not already scanning. Previously found devices are kept. */
    fun startScan()

    /** Stops scanning. Found devices are kept so they can still be inspected. */
    fun stopScan()

    /**
     * Starts one classic inquiry (it ends by itself) and stops a BLE scan first. Call only from an explicit user
     * action.
     * Found devices merge into the same list by address.
     */
    fun startClassicDiscovery()

    fun stopClassicDiscovery()

    /** Forgets all devices. Scanning, if active, continues. */
    fun clear()
}
