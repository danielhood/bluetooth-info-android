package dev.danielhood.btscan.ui.detail

import dev.danielhood.btscan.domain.decode.Decoded
import dev.danielhood.btscan.domain.estimate.DistanceBand
import dev.danielhood.btscan.domain.estimate.Estimates
import dev.danielhood.btscan.domain.model.AddressType
import dev.danielhood.btscan.domain.model.BleDevice
import dev.danielhood.btscan.domain.model.DeviceSource
import dev.danielhood.btscan.domain.model.RadioInfo
import dev.danielhood.btscan.domain.parser.AdFlag
import dev.danielhood.btscan.domain.parser.CompanyIds
import dev.danielhood.btscan.domain.parser.ServiceUuids
import dev.danielhood.btscan.domain.system.SystemDeviceInfo
import dev.danielhood.btscan.ui.list.signalLevel
import java.time.Duration
import java.time.Instant
import java.util.UUID

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
    val decoded: List<Decoded> = emptyList(),
    val radio: RadioInfo? = null,
    /** How the address was formed (from the scan result; Android 15 and later); null when unknown. */
    val addressType: AddressType? = null,
    /** Which radio found the device; the "Found by" line shows only when a classic inquiry was involved. */
    val source: DeviceSource = DeviceSource.BLE,
    /** Estimated average milliseconds between received packets; null until enough packets were seen. */
    val intervalEstimateMs: Long? = null,
    /** Rough distance band; null unless the device advertises a calibrated reference power. */
    val distanceBand: DistanceBand? = null,
)

/** The System information section (Tier 2a): reading what Android knows needs a permission asked for on demand. */
sealed interface SystemInfoState {
    /** [permanentlyDenied]: the OS will no longer show the prompt, so the only way is the app's settings. */
    data class NeedsPermission(
        val permanentlyDenied: Boolean,
    ) : SystemInfoState

    data object Loading : SystemInfoState

    data class Loaded(
        val info: SystemDeviceInfo,
    ) : SystemInfoState

    /** The system has nothing to say about this device. */
    data object Unavailable : SystemInfoState
}

/**
 * Querying a device for the profiles it supports (Tier 2c). This CONTACTS the device, so it never leaves [Idle]
 * without an explicit tap, and [Confirming] (a dialog naming the device) always comes first: nothing has contacted
 * it until the user confirms. There is no "remember my answer".
 */
sealed interface ProfileLookupState {
    data object Idle : ProfileLookupState

    /** The confirmation dialog is on screen. */
    data object Confirming : ProfileLookupState

    data object Running : ProfileLookupState

    data class Done(
        val uuids: List<UUID>,
    ) : ProfileLookupState

    data object TimedOut : ProfileLookupState

    /** The platform would not start the query. */
    data object NotStarted : ProfileLookupState
}

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
        val systemInfo: SystemInfoState = SystemInfoState.NeedsPermission(permanentlyDenied = false),
        val profiles: ProfileLookupState = ProfileLookupState.Idle,
        /** The lookup button is offered only for classic or dual-mode devices, once system information is readable. */
        val canLookUpProfiles: Boolean = false,
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
        decoded = decoded,
        radio = radio,
        addressType = addressType,
        source = source,
        intervalEstimateMs = Estimates.advertisingIntervalMs(this),
        distanceBand = Estimates.distanceBand(this),
    )

internal fun secondsBetween(from: Instant, to: Instant): Long = Duration.between(from, to).seconds.coerceAtLeast(0)
