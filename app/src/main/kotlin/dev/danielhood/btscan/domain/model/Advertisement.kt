package dev.danielhood.btscan.domain.model

import java.time.Instant
import java.util.UUID

/** One received advertisement as delivered by a scanner, before parsing. */
data class RawAdvertisement(
    /** Device address; may be a rotating private address. */
    val address: String,
    /** Signal strength in dBm. */
    val rssi: Int,
    /** Complete advertisement payload (advertising data plus scan response when the platform merges them). */
    val bytes: Bytes,
    val isConnectable: Boolean,
    val timestamp: Instant,
    /** Radio metadata; null when the scanner does not provide it (for example most fakes). */
    val radio: RadioInfo? = null,
)

data class ManufacturerData(
    val companyId: Int,
    val data: Bytes,
)

data class ServiceData(
    val uuid: UUID,
    val data: Bytes,
)

/** Decoded content of an advertisement payload (see AdvertisementParser). */
data class Advertisement(
    val localName: String? = null,
    val txPowerDbm: Int? = null,
    val flags: Int? = null,
    val serviceUuids: List<UUID> = emptyList(),
    val serviceData: List<ServiceData> = emptyList(),
    val manufacturerData: List<ManufacturerData> = emptyList(),
)
