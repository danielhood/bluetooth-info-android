package dev.danielhood.btscan

import android.content.Intent
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

    private companion object {
        const val EXTRA_SCANNER = "scanner"
        const val EXTRA_SCENARIO = "scenario"

        /** Release builds are checked (scripts/verify.sh) to NOT contain this string. */
        const val DEBUG_MARKER = "BTSCAN_DEBUG_ONLY_MARKER"
    }
}
