package dev.danielhood.btscan.data.ble.android

import android.bluetooth.BluetoothDevice
import dev.danielhood.btscan.domain.model.AddressType

/**
 * Maps the platform's `BluetoothDevice.getAddressType()` value. Only meaningful for the device object that came with a
 * scan result: for one made from an address alone Android answers "public" whatever the truth (observed on the
 * Pixel), so that value is never used.
 */
internal fun addressTypeOf(value: Int): AddressType =
    when (value) {
        BluetoothDevice.ADDRESS_TYPE_PUBLIC -> AddressType.PUBLIC
        BluetoothDevice.ADDRESS_TYPE_RANDOM -> AddressType.RANDOM
        BluetoothDevice.ADDRESS_TYPE_ANONYMOUS -> AddressType.ANONYMOUS
        else -> AddressType.UNKNOWN
    }
