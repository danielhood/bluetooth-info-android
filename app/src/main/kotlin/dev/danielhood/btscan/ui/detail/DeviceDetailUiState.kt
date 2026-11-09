package dev.danielhood.btscan.ui.detail

import dev.danielhood.btscan.domain.model.BleDevice
import dev.danielhood.btscan.domain.parser.AdFlag
import dev.danielhood.btscan.domain.parser.CompanyIds
import dev.danielhood.btscan.domain.parser.ServiceUuids
import dev.danielhood.btscan.ui.list.signalLevel
import java.time.Duration
import java.time.Instant

data class ServiceUi(
    val uuid: String,
    val name: String?,
)

data class ManufacturerUi(
    val companyIdHex: String,
    val companyName: String?,
    val payloadHex: String,
)

data class ServiceDataUi(
    val uuid: String,
    val name: String?,
    val dataHex: String,
)

/** Display-ready device details; everything is already formatted except relative times (see [DeviceDetailUiState]). */
data class DeviceDetailUi(
    val address: String,
    val name: String?,
    val rssi: Int,
    val signalLevel: Int,
    val isConnectable: Boolean,
    val txPowerDbm: Int?,
    val advertisementCount: Int,
    val services: List<ServiceUi>,
    val manufacturers: List<ManufacturerUi>,
    val serviceData: List<ServiceDataUi>,
    val flagsHex: String?,
    val flags: List<AdFlag>,
    val rawHex: String,
)

sealed interface DeviceDetailUiState {
    /** The device was never known to the repository (for example after process restore). */
    data object NotFound : DeviceDetailUiState

    /**
     * [outOfRange] is true once the repository dropped the device while the screen stayed open; the last known
     * data stays visible. Ages are whole seconds, recomputed every second.
     */
    data class Loaded(
        val device: DeviceDetailUi,
        val outOfRange: Boolean,
        val firstSeenAgoSeconds: Long,
        val lastSeenAgoSeconds: Long,
    ) : DeviceDetailUiState
}

private const val HEX_RADIX = 16
private const val COMPANY_ID_WIDTH = 4
private const val FLAGS_WIDTH = 2

internal fun BleDevice.toDetailUi() =
    DeviceDetailUi(
        address = address,
        name = name,
        rssi = rssi,
        signalLevel = signalLevel(rssi),
        isConnectable = isConnectable,
        txPowerDbm = txPowerDbm,
        advertisementCount = advertisementCount,
        services = serviceUuids.map { ServiceUi(it.toString(), ServiceUuids.nameOf(it)) },
        manufacturers =
            manufacturerData.map {
                ManufacturerUi(
                    companyIdHex =
                        "0x" +
                            it.companyId
                                .toString(HEX_RADIX)
                                .uppercase()
                                .padStart(COMPANY_ID_WIDTH, '0'),
                    companyName = CompanyIds.nameOf(it.companyId),
                    payloadHex = it.data.toHex(" "),
                )
            },
        serviceData =
            serviceData.map {
                ServiceDataUi(
                    it.uuid.toString(),
                    ServiceUuids.nameOf(it.uuid),
                    it.data.toHex(" "),
                )
            },
        flagsHex = flags?.let { "0x" + it.toString(HEX_RADIX).uppercase().padStart(FLAGS_WIDTH, '0') },
        flags = flags?.let { AdFlag.decode(it) } ?: emptyList(),
        rawHex = rawBytes.toHex(" "),
    )

internal fun secondsBetween(from: Instant, to: Instant): Long = Duration.between(from, to).seconds.coerceAtLeast(0)
