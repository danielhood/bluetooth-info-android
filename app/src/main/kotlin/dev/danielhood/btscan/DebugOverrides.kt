package dev.danielhood.btscan

import android.content.Intent

/**
 * Debug-only launch configuration, normally empty. In debug builds it is read from intent extras, e.g.
 * `adb shell am start -n dev.danielhood.btscan/.MainActivity --es scanner fake --es scenario default`.
 * Release builds always return [DebugConfig.NONE] (see src/release).
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
}
