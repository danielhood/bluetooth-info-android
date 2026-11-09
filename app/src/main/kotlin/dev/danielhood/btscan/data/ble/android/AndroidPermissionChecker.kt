package dev.danielhood.btscan.data.ble.android

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import dev.danielhood.btscan.domain.model.PermissionState
import dev.danielhood.btscan.domain.port.PermissionChecker

/**
 * Reports the runtime permission a BLE scan needs: BLUETOOTH_SCAN on Android 12+, otherwise fine location
 * (the legacy requirement). Whether the OS will still show a prompt is only knowable from an Activity, so
 * [current] reports `canAskAgain = true`; the UI refines that after a denied request.
 */
class AndroidPermissionChecker(
    private val context: Context,
) : PermissionChecker {
    override val requiredPermissions: List<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            listOf(Manifest.permission.BLUETOOTH_SCAN)
        } else {
            listOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }

    override fun current(): PermissionState {
        val allGranted =
            requiredPermissions.all {
                context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED
            }
        return if (allGranted) PermissionState.Granted else PermissionState.Denied(canAskAgain = true)
    }
}
