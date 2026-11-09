package dev.danielhood.btscan

import android.content.Intent
import dev.danielhood.btscan.data.ble.fake.FakeAdapterStateMonitor
import dev.danielhood.btscan.data.ble.fake.FakeBleScanner
import dev.danielhood.btscan.data.ble.fake.Scenarios
import dev.danielhood.btscan.domain.port.AdapterStateMonitor
import dev.danielhood.btscan.domain.port.BleScanner
import dev.danielhood.btscan.domain.port.Clock
import dev.danielhood.btscan.util.AppLog

/** Debug build: honours `--es scanner` / `--es scenario` launch extras. Must not exist in release. */
class DebugOverridesImpl : DebugOverrides {
    override fun configFrom(intent: Intent?): DebugConfig {
        val config =
            DebugConfig(
                scanner = intent?.getStringExtra(EXTRA_SCANNER),
                scenario = intent?.getStringExtra(EXTRA_SCENARIO),
            )
        AppLog.d(AppLog.APP, "$DEBUG_MARKER config=$config")
        return config
    }

    override fun scannerFor(config: DebugConfig, clock: Clock): BleScanner? =
        when (config.scanner) {
            null, SCANNER_REAL -> null
            SCANNER_FAKE -> FakeBleScanner(Scenarios.require(config.scenario ?: Scenarios.DEFAULT), clock)
            else -> throw IllegalArgumentException("Unknown scanner '${config.scanner}'. Use 'fake' or 'real'.")
        }

    override fun adapterMonitorFor(config: DebugConfig): AdapterStateMonitor? =
        when (config.scanner) {
            null, SCANNER_REAL -> {
                null
            }

            SCANNER_FAKE -> {
                val scenario = Scenarios.require(config.scenario ?: Scenarios.DEFAULT)
                FakeAdapterStateMonitor(scenario.adapterState)
            }

            else -> {
                throw IllegalArgumentException("Unknown scanner '${config.scanner}'. Use 'fake' or 'real'.")
            }
        }

    private companion object {
        const val EXTRA_SCANNER = "scanner"
        const val EXTRA_SCENARIO = "scenario"
        const val SCANNER_FAKE = "fake"
        const val SCANNER_REAL = "real"

        /** Release builds are checked (scripts/verify.sh) to NOT contain this string. */
        const val DEBUG_MARKER = "BTSCAN_DEBUG_ONLY_MARKER"
    }
}
