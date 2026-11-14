package dev.danielhood.btscan.data.ble.android

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.provider.Settings
import dev.danielhood.btscan.domain.model.AdapterState
import dev.danielhood.btscan.domain.port.AdapterStateMonitor
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Reports whether BLE scanning can work: no hardware, adapter off, or system location off. Location must be on on every
 * Android version because the manifest does not assert `neverForLocation` (decision D-P2-8). Re-evaluated on every
 * Bluetooth state or location mode change broadcast.
 */
class AndroidAdapterStateMonitor(
    private val context: Context,
) : AdapterStateMonitor {
    override val state: Flow<AdapterState> =
        callbackFlow {
            trySend(current())
            val receiver =
                object : BroadcastReceiver() {
                    override fun onReceive(context: Context, intent: Intent) {
                        trySend(current())
                    }
                }
            val filter =
                IntentFilter().apply {
                    addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
                    addAction(LocationManager.MODE_CHANGED_ACTION)
                    addAction(LocationManager.PROVIDERS_CHANGED_ACTION)
                }
            registerSystemReceiver(receiver, filter)
            awaitClose { context.unregisterReceiver(receiver) }
        }.distinctUntilChanged()

    internal fun current(): AdapterState {
        val hasBle = context.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
        return when {
            !hasBle || adapter == null -> AdapterState.UNSUPPORTED
            adapter.state != BluetoothAdapter.STATE_ON -> AdapterState.OFF
            !isLocationEnabled() -> AdapterState.LOCATION_OFF
            else -> AdapterState.ON
        }
    }

    private fun isLocationEnabled(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            context.getSystemService(LocationManager::class.java).isLocationEnabled
        } else {
            @Suppress("DEPRECATION")
            Settings.Secure.getInt(context.contentResolver, Settings.Secure.LOCATION_MODE, 0) != 0
        }

    private fun registerSystemReceiver(receiver: BroadcastReceiver, filter: IntentFilter) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            context.registerReceiver(receiver, filter)
        }
    }
}
