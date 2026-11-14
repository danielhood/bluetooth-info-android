package dev.danielhood.btscan

import android.content.Intent
import dev.danielhood.btscan.domain.port.AdapterStateMonitor
import dev.danielhood.btscan.domain.port.BleScanner
import dev.danielhood.btscan.domain.port.ClassicScanner
import dev.danielhood.btscan.domain.port.Clock
import dev.danielhood.btscan.domain.port.PermissionChecker
import dev.danielhood.btscan.domain.port.SystemDeviceInfoSource

/** Release build: debug overrides are impossible. */
class DebugOverridesImpl : DebugOverrides {
    override fun configFrom(intent: Intent?): DebugConfig = DebugConfig.NONE

    override fun scannerFor(config: DebugConfig, clock: Clock): BleScanner? = null

    override fun adapterMonitorFor(config: DebugConfig): AdapterStateMonitor? = null

    override fun classicScannerFor(config: DebugConfig, clock: Clock): ClassicScanner? = null

    override fun systemInfoSourceFor(
        config: DebugConfig,
        permissions: PermissionChecker,
    ): SystemDeviceInfoSource? = null
}
