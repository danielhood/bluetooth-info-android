package dev.danielhood.btscan.data.ble.android

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import dev.danielhood.btscan.domain.model.PermissionState
import dev.danielhood.btscan.domain.port.PermissionChecker

/**
 * Reports the runtime permissions a BLE scan needs. The manifest does not assert `neverForLocation` (decision D-P2-8),
 * so on every Android version scan results need precise location; on Android 12+ they also need BLUETOOTH_SCAN.
 *
 * Android 12+ ignores a request for fine location unless coarse location is requested in the same call, so
 * [requiredPermissions] (what the UI requests) includes coarse, while [current] only demands what scanning needs:
 * a user who answers "approximate" has not granted enough and is asked again with an explanation.
 * Whether the OS will still show a prompt is only knowable from an Activity, so [current] reports
 * `canAskAgain = true`; the UI refines that after a denied request.
 */
class AndroidPermissionChecker(
    private val context: Context,
) : PermissionChecker {
    private val needed: List<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.ACCESS_FINE_LOCATION)
        } else {
            listOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }

    override val requiredPermissions: List<String> =
        if (Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.S
        ) {
            needed + Manifest.permission.ACCESS_COARSE_LOCATION
        } else {
            needed
        }

    /**
     * Reading system device info needs BLUETOOTH_CONNECT on Android 12+; the legacy BLUETOOTH permission covers it
     * before.
     */
    override val connectPermissions: List<String> =
        if (Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.S
        ) {
            listOf(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            emptyList()
        }

    override fun connectState(): PermissionState {
        val allGranted = connectPermissions.all { context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }
        return if (allGranted) PermissionState.Granted else PermissionState.Denied(canAskAgain = true)
    }

    override fun current(): PermissionState {
        val allGranted = needed.all { context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }
        return if (allGranted) PermissionState.Granted else PermissionState.Denied(canAskAgain = true)
    }
}
