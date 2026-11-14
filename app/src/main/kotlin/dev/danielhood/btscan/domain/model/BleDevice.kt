package dev.danielhood.btscan.domain.model

import dev.danielhood.btscan.domain.decode.Decoded
import java.time.Instant
import java.util.UUID

/** A device aggregated over all advertisements seen from one address. This is what the UI consumes. */
data class BleDevice(
    val address: String,
    val name: String?,
    val rssi: Int,
    val txPowerDbm: Int?,
    val isConnectable: Boolean,
    val flags: Int?,
    val serviceUuids: List<UUID>,
    val serviceData: List<ServiceData>,
    val manufacturerData: List<ManufacturerData>,
    val rawBytes: Bytes,
    val firstSeen: Instant,
    val lastSeen: Instant,
    val advertisementCount: Int,
    /** Decoder results, one per decoder id, latest value wins (frames of one format accumulate). */
    val decoded: List<Decoded> = emptyList(),
    val radio: RadioInfo? = null,
    val addressType: AddressType? = null,
    /** BLE by default; CLASSIC when only a classic inquiry found it, BOTH when each did. */
    val source: DeviceSource = DeviceSource.BLE,
    val classic: ClassicInfo? = null,
)
