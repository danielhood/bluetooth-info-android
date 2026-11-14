package dev.danielhood.btscan.domain.model

enum class AdapterState {
    /** No Bluetooth LE hardware. */
    UNSUPPORTED,
    OFF,
    ON,

    /** Adapter on, but system location is off: Android hides scan results without it (see decision D-P2-8). */
    LOCATION_OFF,
}

sealed interface PermissionState {
    data object Granted : PermissionState

    /** [canAskAgain] is false when the OS will no longer show the prompt (user must use Settings). */
    data class Denied(
        val canAskAgain: Boolean,
    ) : PermissionState
}

/** A scan could not start or stopped abnormally. [platformCode] is the platform's error code when there is one. */
class ScanFailure(
    val kind: Kind,
    val platformCode: Int? = null,
    cause: Throwable? = null,
) : Exception("Scan failed: $kind${platformCode?.let { " (code $it)" } ?: ""}", cause) {
    enum class Kind {
        ALREADY_STARTED,
        REGISTRATION_FAILED,
        FEATURE_UNSUPPORTED,
        INTERNAL_ERROR,
        OUT_OF_HARDWARE_RESOURCES,
        SCANNING_TOO_FREQUENTLY,
        PERMISSION_MISSING,
        UNKNOWN,
    }
}
