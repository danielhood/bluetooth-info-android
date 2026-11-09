package dev.danielhood.btscan

import android.content.Intent
import dev.danielhood.btscan.domain.port.BleScanner
import dev.danielhood.btscan.domain.port.Clock

/** Release build: debug overrides are impossible. */
class DebugOverridesImpl : DebugOverrides {
    override fun configFrom(intent: Intent?): DebugConfig = DebugConfig.NONE

    override fun scannerFor(config: DebugConfig, clock: Clock): BleScanner? = null
}
