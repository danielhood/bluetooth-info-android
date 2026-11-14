package dev.danielhood.btscan

import android.content.Intent
import dev.danielhood.btscan.domain.port.AdapterStateMonitor
import dev.danielhood.btscan.domain.port.BleScanner
import dev.danielhood.btscan.domain.port.Clock
import dev.danielhood.btscan.domain.port.PermissionChecker
import dev.danielhood.btscan.domain.port.SystemDeviceInfoSource

/**
 * Debug-only launch configuration, normally empty. In debug builds it is read from intent extras, e.g.
 * `adb shell am start -n dev.danielhood.btscan/.MainActivity --es scanner fake --es scenario default`.
 * Release builds always return [DebugConfig.NONE] and never substitute ports (see src/release).
 */
data class DebugConfig(
    val scanner: String? = null,
    val scenario: String? = null,
) {
    /** Human-readable summary shown in the UI and logs; null when no override is active. */
    val label: String?
        get() =
            if (scanner == null &&
                scenario == null
            ) {
                null
            } else {
                "scanner=${scanner ?: "real"} scenario=${scenario ?: "-"}"
            }

    companion object {
        val NONE = DebugConfig()
    }
}

interface DebugOverrides {
    fun configFrom(intent: Intent?): DebugConfig

    /** A replacement scanner for [config], or null to use the real one. Always null in release builds. */
    fun scannerFor(config: DebugConfig, clock: Clock): BleScanner?

    /** A replacement adapter monitor for [config], or null to use the real one. Always null in release builds. */
    fun adapterMonitorFor(config: DebugConfig): AdapterStateMonitor?

    /** A replacement system-info source for [config], or null to use the real one. Always null in release builds. */
    fun systemInfoSourceFor(config: DebugConfig, permissions: PermissionChecker): SystemDeviceInfoSource?
}
