package dev.danielhood.btscan.data.ble.android

import android.bluetooth.le.ScanCallback
import dev.danielhood.btscan.domain.model.ScanFailure

/** Maps `ScanCallback.onScanFailed` error codes to the domain's [ScanFailure]. */
internal fun scanFailureFor(errorCode: Int): ScanFailure {
    val kind =
        when (errorCode) {
            ScanCallback.SCAN_FAILED_ALREADY_STARTED -> ScanFailure.Kind.ALREADY_STARTED
            ScanCallback.SCAN_FAILED_APPLICATION_REGISTRATION_FAILED -> ScanFailure.Kind.REGISTRATION_FAILED
            ScanCallback.SCAN_FAILED_INTERNAL_ERROR -> ScanFailure.Kind.INTERNAL_ERROR
            ScanCallback.SCAN_FAILED_FEATURE_UNSUPPORTED -> ScanFailure.Kind.FEATURE_UNSUPPORTED
            ScanCallback.SCAN_FAILED_OUT_OF_HARDWARE_RESOURCES -> ScanFailure.Kind.OUT_OF_HARDWARE_RESOURCES
            ScanCallback.SCAN_FAILED_SCANNING_TOO_FREQUENTLY -> ScanFailure.Kind.SCANNING_TOO_FREQUENTLY
            else -> ScanFailure.Kind.UNKNOWN
        }
    return ScanFailure(kind, platformCode = errorCode)
}
