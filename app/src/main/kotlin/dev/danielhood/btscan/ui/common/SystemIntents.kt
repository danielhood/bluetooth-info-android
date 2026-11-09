package dev.danielhood.btscan.ui.common

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings

/** Hand-offs to system settings screens. Each returns quietly if the device has no matching screen. */
object SystemIntents {
    fun openAppSettings(context: Context) =
        start(
            context,
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
        )

    fun openBluetoothSettings(context: Context) = start(context, Intent(Settings.ACTION_BLUETOOTH_SETTINGS))

    fun openLocationSettings(context: Context) = start(context, Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))

    private fun start(context: Context, intent: Intent) {
        runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
}
