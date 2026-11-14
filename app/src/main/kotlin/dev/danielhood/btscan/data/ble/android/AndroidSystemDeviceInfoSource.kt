package dev.danielhood.btscan.data.ble.android

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.os.Build
import dev.danielhood.btscan.domain.model.PermissionState
import dev.danielhood.btscan.domain.port.PermissionChecker
import dev.danielhood.btscan.domain.port.SystemDeviceInfoSource
import dev.danielhood.btscan.domain.system.AddressType
import dev.danielhood.btscan.domain.system.BondState
import dev.danielhood.btscan.domain.system.DeviceClassInfo
import dev.danielhood.btscan.domain.system.DeviceType
import dev.danielhood.btscan.domain.system.SystemDeviceInfo
import dev.danielhood.btscan.domain.system.SystemInfoResult
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * Reads what Android already knows about a device through [BluetoothAdapter.getRemoteDevice] and the plain getters
 * (name, alias, bond state, type, class, address type, cached UUIDs). These return values the system cached and send
 * nothing to the device. It never starts pairing, queries services over the air, or connects (guarded by
 * ArchitectureTest). Each read is guarded by the permission check and a `SecurityException` catch.
 */
class AndroidSystemDeviceInfoSource(
    private val context: Context,
    private val permissions: PermissionChecker,
    private val dispatcher: CoroutineDispatcher,
) : SystemDeviceInfoSource {
    override suspend fun lookup(address: String): SystemInfoResult =
        withContext(dispatcher) {
            if (permissions.connectState() !is PermissionState.Granted) {
                SystemInfoResult.PermissionMissing
            } else {
                read(address)
            }
        }

    private fun read(address: String): SystemInfoResult {
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
        return if (adapter == null || !BluetoothAdapter.checkBluetoothAddress(address)) {
            SystemInfoResult.Unavailable
        } else {
            adapter.getRemoteDevice(address).readInfo()
        }
    }

    /** All reads happen inside one try block: losing the permission between the check and a read is not a crash. */
    private fun BluetoothDevice.readInfo(): SystemInfoResult =
        try {
            SystemInfoResult.Available(
                SystemDeviceInfo(
                    name = name,
                    alias = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) alias else null,
                    bondState = bondStateOf(bondState),
                    deviceType = deviceTypeOf(type),
                    deviceClass = bluetoothClass?.let { DeviceClassInfo.of(it.deviceClass) },
                    addressType =
                        if (Build.VERSION.SDK_INT >=
                            Build.VERSION_CODES.VANILLA_ICE_CREAM
                        ) {
                            addressTypeOf(addressType)
                        } else {
                            null
                        },
                    cachedUuids = uuids.orEmpty().map { it.uuid },
                ),
            )
        } catch (_: SecurityException) {
            SystemInfoResult.PermissionMissing
        }

    private fun bondStateOf(value: Int): BondState =
        when (value) {
            BluetoothDevice.BOND_BONDED -> BondState.BONDED
            BluetoothDevice.BOND_BONDING -> BondState.BONDING
            else -> BondState.NONE
        }

    private fun deviceTypeOf(value: Int): DeviceType =
        when (value) {
            BluetoothDevice.DEVICE_TYPE_CLASSIC -> DeviceType.CLASSIC
            BluetoothDevice.DEVICE_TYPE_LE -> DeviceType.LE
            BluetoothDevice.DEVICE_TYPE_DUAL -> DeviceType.DUAL
            else -> DeviceType.UNKNOWN
        }

    private fun addressTypeOf(value: Int): AddressType =
        when (value) {
            BluetoothDevice.ADDRESS_TYPE_PUBLIC -> AddressType.PUBLIC
            BluetoothDevice.ADDRESS_TYPE_RANDOM -> AddressType.RANDOM
            BluetoothDevice.ADDRESS_TYPE_ANONYMOUS -> AddressType.ANONYMOUS
            else -> AddressType.UNKNOWN
        }
}
