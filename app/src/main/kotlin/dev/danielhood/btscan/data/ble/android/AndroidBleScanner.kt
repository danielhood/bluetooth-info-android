package dev.danielhood.btscan.data.ble.android

import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import dev.danielhood.btscan.domain.model.Bytes
import dev.danielhood.btscan.domain.model.RawAdvertisement
import dev.danielhood.btscan.domain.model.ScanFailure
import dev.danielhood.btscan.domain.port.BleScanner
import dev.danielhood.btscan.domain.port.Clock
import dev.danielhood.btscan.util.AppLog
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow

private const val BUFFER_CAPACITY = 512

/**
 * Real BLE scanner over `BluetoothLeScanner`. Passes each advertisement's raw bytes through untouched (parsing is
 * the domain's job) and never reads `BluetoothDevice.getName()`, which would need the Bluetooth "connect"
 * permission (deliberately never requested).
 * Platform failures, including a missing permission, surface as [ScanFailure].
 */
class AndroidBleScanner(
    private val context: Context,
    private val clock: Clock,
) : BleScanner {
    override fun scan(): Flow<RawAdvertisement> =
        callbackFlow {
            val manager = context.getSystemService(BluetoothManager::class.java)
            val adapter = manager?.adapter ?: throw ScanFailure(ScanFailure.Kind.FEATURE_UNSUPPORTED)
            // Null while Bluetooth is off; the UI normally prevents starting in that state.
            val scanner = adapter.bluetoothLeScanner ?: throw ScanFailure(ScanFailure.Kind.INTERNAL_ERROR)

            val callback =
                object : ScanCallback() {
                    override fun onScanResult(callbackType: Int, result: ScanResult) {
                        trySend(result.toRaw())
                    }

                    override fun onBatchScanResults(results: MutableList<ScanResult>) {
                        results.forEach { trySend(it.toRaw()) }
                    }

                    override fun onScanFailed(errorCode: Int) {
                        AppLog.i(AppLog.SCAN, "scan failed code=$errorCode")
                        close(scanFailureFor(errorCode))
                    }
                }
            val settings =
                ScanSettings
                    .Builder()
                    .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                    .setLegacy(false)
                    .build()
            try {
                scanner.startScan(null, settings, callback)
                AppLog.i(AppLog.SCAN, "scan started")
            } catch (e: SecurityException) {
                throw ScanFailure(ScanFailure.Kind.PERMISSION_MISSING, cause = e)
            }
            awaitClose {
                try {
                    scanner.stopScan(callback)
                } catch (e: SecurityException) {
                    AppLog.i(AppLog.SCAN, "stopScan without permission: ${e.message}")
                } catch (e: IllegalStateException) {
                    AppLog.i(AppLog.SCAN, "stopScan while adapter off: ${e.message}")
                }
                AppLog.i(AppLog.SCAN, "scan stopped")
            }
        }.buffer(BUFFER_CAPACITY)

    private fun ScanResult.toRaw() =
        RawAdvertisement(
            address = device.address,
            rssi = rssi,
            bytes = Bytes(scanRecord?.bytes ?: ByteArray(0)),
            isConnectable = isConnectable,
            timestamp = clock.now(),
            radio =
                radioInfoOf(
                    primaryPhy = primaryPhy,
                    secondaryPhy = secondaryPhy,
                    isLegacy = isLegacy,
                    advertisingSid = advertisingSid,
                    txPower = txPower,
                    periodicIntervalUnits = periodicAdvertisingInterval,
                    dataStatus = dataStatus,
                ),
        )
}
