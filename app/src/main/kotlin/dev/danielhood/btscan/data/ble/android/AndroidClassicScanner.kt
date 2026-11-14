package dev.danielhood.btscan.data.ble.android

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import androidx.core.content.IntentCompat
import dev.danielhood.btscan.domain.model.ClassicSighting
import dev.danielhood.btscan.domain.model.ScanFailure
import dev.danielhood.btscan.domain.port.ClassicScanner
import dev.danielhood.btscan.domain.port.Clock
import dev.danielhood.btscan.domain.system.BondState
import dev.danielhood.btscan.util.AppLog
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow

private const val BUFFER_CAPACITY = 128

/**
 * Classic Bluetooth inquiry over `BluetoothAdapter.startDiscovery()`: one inquiry that Android ends by itself after
 * about 12 seconds (the flow then completes). Cancelling the collector cancels the inquiry. This is the only file
 * allowed to start or cancel discovery (ArchitectureTest), and it is only ever started from a user action: it can
 * briefly disturb audio on a connected headset. Never pairs and never connects.
 */
class AndroidClassicScanner(
    private val context: Context,
    private val clock: Clock,
) : ClassicScanner {
    override fun discover(): Flow<ClassicSighting> =
        callbackFlow {
            val adapter =
                context.getSystemService(BluetoothManager::class.java)?.adapter
                    ?: throw ScanFailure(ScanFailure.Kind.FEATURE_UNSUPPORTED)
            val receiver =
                object : BroadcastReceiver() {
                    override fun onReceive(context: Context, intent: Intent) {
                        when (intent.action) {
                            BluetoothDevice.ACTION_FOUND -> sightingFrom(intent)?.let { trySend(it) }
                            BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> close()
                        }
                    }
                }
            val filter =
                IntentFilter().apply {
                    addAction(BluetoothDevice.ACTION_FOUND)
                    addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
                }
            registerSystemReceiver(receiver, filter)
            val started =
                try {
                    adapter.startDiscovery()
                } catch (e: SecurityException) {
                    context.unregisterReceiver(receiver)
                    throw ScanFailure(ScanFailure.Kind.PERMISSION_MISSING, cause = e)
                }
            if (!started) {
                context.unregisterReceiver(receiver)
                throw ScanFailure(ScanFailure.Kind.INTERNAL_ERROR)
            }
            AppLog.i(AppLog.SCAN, "classic discovery started")
            awaitClose {
                try {
                    adapter.cancelDiscovery()
                } catch (e: SecurityException) {
                    AppLog.i(AppLog.SCAN, "cancel classic discovery without permission: ${e.message}")
                }
                context.unregisterReceiver(receiver)
                AppLog.i(AppLog.SCAN, "classic discovery stopped")
            }
        }.buffer(BUFFER_CAPACITY)

    private fun sightingFrom(intent: Intent): ClassicSighting? {
        val device = IntentCompat.getParcelableExtra(intent, BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        return device?.let {
            val rssi = intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, Short.MIN_VALUE)
            val rawClass =
                IntentCompat
                    .getParcelableExtra(
                        intent,
                        BluetoothDevice.EXTRA_CLASS,
                        BluetoothClass::class.java,
                    )?.deviceClass
            ClassicSighting(
                address = it.address,
                name = intent.getStringExtra(BluetoothDevice.EXTRA_NAME),
                rssi = rssi.takeUnless { value -> value == Short.MIN_VALUE }?.toInt(),
                deviceClass = rawClass?.let(::deviceClassOf),
                bondState = bondStateOrNull(it),
                timestamp = clock.now(),
            )
        }
    }

    /** Bond state needs the connect permission; without it the value is unknown, not "not paired". */
    private fun bondStateOrNull(device: BluetoothDevice): BondState? =
        try {
            when (device.bondState) {
                BluetoothDevice.BOND_BONDED -> BondState.BONDED
                BluetoothDevice.BOND_BONDING -> BondState.BONDING
                else -> BondState.NONE
            }
        } catch (_: SecurityException) {
            null
        }

    private fun registerSystemReceiver(receiver: BroadcastReceiver, filter: IntentFilter) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            context.registerReceiver(receiver, filter)
        }
    }
}
