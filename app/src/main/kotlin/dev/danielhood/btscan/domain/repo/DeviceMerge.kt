package dev.danielhood.btscan.domain.repo

import dev.danielhood.btscan.domain.decode.Decoded
import dev.danielhood.btscan.domain.model.Advertisement
import dev.danielhood.btscan.domain.model.BleDevice
import dev.danielhood.btscan.domain.model.Bytes
import dev.danielhood.btscan.domain.model.ClassicInfo
import dev.danielhood.btscan.domain.model.ClassicSighting
import dev.danielhood.btscan.domain.model.DeviceSource
import dev.danielhood.btscan.domain.model.RawAdvertisement

/**
 * Folds a new advertisement into an existing device. Latest wins for signal and timing; name, TX power and
 * flags keep the last non-null value; UUIDs are unioned; service/manufacturer data is replaced per key,
 * because advertising packets and scan responses carry different fields. Decoded results are replaced per
 * decoder id, so alternating frames (for example Eddystone UID, URL and TLM) accumulate side by side.
 */
internal fun BleDevice.merge(raw: RawAdvertisement, adv: Advertisement, decoded: List<Decoded>): BleDevice =
    copy(
        name = adv.localName ?: name,
        rssi = raw.rssi,
        txPowerDbm = adv.txPowerDbm ?: txPowerDbm,
        isConnectable = raw.isConnectable,
        flags = adv.flags ?: flags,
        serviceUuids = (serviceUuids + adv.serviceUuids).distinct(),
        serviceData = mergeByKey(serviceData, adv.serviceData) { it.uuid },
        manufacturerData = mergeByKey(manufacturerData, adv.manufacturerData) { it.companyId },
        rawBytes = raw.bytes,
        lastSeen = raw.timestamp,
        advertisementCount = advertisementCount + 1,
        decoded = mergeByKey(this.decoded, decoded) { it.decoder },
        radio = raw.radio ?: radio,
        addressType = raw.addressType ?: addressType,
        source = if (source == DeviceSource.CLASSIC) DeviceSource.BOTH else source,
    )

private fun <T, K> mergeByKey(old: List<T>, new: List<T>, key: (T) -> K): List<T> {
    val merged = LinkedHashMap<K, T>()
    old.forEach { merged[key(it)] = it }
    new.forEach { merged[key(it)] = it }
    return merged.values.toList()
}

private const val UNKNOWN_RSSI_DBM = -127

/** Folds a classic sighting into an existing device; a BLE-only device becomes BOTH and keeps its advertisement. */
internal fun BleDevice.mergeClassic(sighting: ClassicSighting): BleDevice =
    copy(
        name = name ?: sighting.name,
        rssi = sighting.rssi ?: rssi,
        lastSeen = sighting.timestamp,
        source = if (source == DeviceSource.BLE) DeviceSource.BOTH else source,
        classic = ClassicInfo(sighting.deviceClass ?: classic?.deviceClass, sighting.bondState ?: classic?.bondState),
    )

/** A device only a classic inquiry has found: no advertisement bytes, not known to be connectable. */
internal fun ClassicSighting.toNewDevice(): BleDevice =
    BleDevice(
        address = address,
        name = name,
        rssi = rssi ?: UNKNOWN_RSSI_DBM,
        txPowerDbm = null,
        isConnectable = false,
        flags = null,
        serviceUuids = emptyList(),
        serviceData = emptyList(),
        manufacturerData = emptyList(),
        rawBytes = Bytes.EMPTY,
        firstSeen = timestamp,
        lastSeen = timestamp,
        advertisementCount = 0,
        source = DeviceSource.CLASSIC,
        classic = ClassicInfo(deviceClass, bondState),
    )
