package dev.danielhood.btscan

import android.content.Intent

/** Release build: debug overrides are impossible. */
class DebugOverridesImpl : DebugOverrides {
    override fun configFrom(intent: Intent?): DebugConfig = DebugConfig.NONE
}
