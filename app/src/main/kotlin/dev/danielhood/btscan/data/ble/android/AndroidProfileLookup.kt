package dev.danielhood.btscan.data.ble.android

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.ParcelUuid
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import dev.danielhood.btscan.domain.model.PermissionState
import dev.danielhood.btscan.domain.port.PermissionChecker
import dev.danielhood.btscan.domain.port.ProfileLookup
import dev.danielhood.btscan.domain.system.ProfileLookupResult
import dev.danielhood.btscan.util.AppLog
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

/**
 * Classic service lookup: `BluetoothDevice.fetchUuidsWithSdp()`, answered by the `ACTION_UUID` broadcast. This CONTACTS
 * the device (a short classic session), so it is the only file allowed to make the call (ArchitectureTest) and it is
 * reached only from the detail ViewModel's confirmed user action. It never pairs. The caller applies the timeout;
 * cancelling the coroutine unregisters the receiver (the platform query itself cannot be recalled).
 */
class AndroidProfileLookup(
    private val context: Context,
    private val permissions: PermissionChecker,
) : ProfileLookup {
    override suspend fun fetchProfiles(address: String): ProfileLookupResult {
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
        return when {
            permissions.connectState() !is PermissionState.Granted -> ProfileLookupResult.PermissionMissing
            adapter == null || !BluetoothAdapter.checkBluetoothAddress(address) -> ProfileLookupResult.NotStarted
            else -> query(adapter.getRemoteDevice(address))
        }
    }

    private suspend fun query(device: BluetoothDevice): ProfileLookupResult =
        suspendCancellableCoroutine { continuation ->
            val unregistered = AtomicBoolean(false)
            val receiver =
                object : BroadcastReceiver() {
                    override fun onReceive(context: Context, intent: Intent) {
                        val from =
                            IntentCompat.getParcelableExtra(
                                intent,
                                BluetoothDevice.EXTRA_DEVICE,
                                BluetoothDevice::class.java,
                            )
                        if (from?.address != device.address) return
                        val uuids =
                            IntentCompat
                                .getParcelableArrayExtra(intent, BluetoothDevice.EXTRA_UUID, ParcelUuid::class.java)
                                .orEmpty()
                                .filterIsInstance<ParcelUuid>()
                                .map { it.uuid }
                        stop(unregistered, this)
                        if (continuation.isActive) continuation.resume(ProfileLookupResult.Profiles(uuids))
                    }
                }
            register(receiver)
            continuation.invokeOnCancellation { stop(unregistered, receiver) }
            val started =
                try {
                    device.fetchUuidsWithSdp()
                } catch (_: SecurityException) {
                    stop(unregistered, receiver)
                    continuation.resume(ProfileLookupResult.PermissionMissing)
                    return@suspendCancellableCoroutine
                }
            AppLog.i(AppLog.SCAN, "profile lookup started=$started")
            if (!started) {
                stop(unregistered, receiver)
                continuation.resume(ProfileLookupResult.NotStarted)
            }
        }

    private fun stop(unregistered: AtomicBoolean, receiver: BroadcastReceiver? = null) {
        if (receiver != null && unregistered.compareAndSet(false, true)) context.unregisterReceiver(receiver)
    }

    private fun register(receiver: BroadcastReceiver) {
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(BluetoothDevice.ACTION_UUID),
            ContextCompat.RECEIVER_EXPORTED,
        )
    }
}
