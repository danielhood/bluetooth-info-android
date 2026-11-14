package dev.danielhood.btscan.ui.list

import dev.danielhood.btscan.domain.decode.DecoderId
import dev.danielhood.btscan.domain.decode.primaryKind
import dev.danielhood.btscan.domain.model.AdapterState
import dev.danielhood.btscan.domain.model.BleDevice
import dev.danielhood.btscan.domain.model.PermissionState
import dev.danielhood.btscan.domain.model.ScanFailure

/** What the list screen should show, fully determined by permission, adapter and scan state. */
sealed interface ListStatus {
    /** Ready; not scanning. */
    data object Idle : ListStatus

    data object Scanning : ListStatus

    data class PermissionNeeded(
        val permanentlyDenied: Boolean,
    ) : ListStatus

    data object BluetoothOff : ListStatus

    /** Scanning needs system location switched on (all Android versions; decision D-P2-8). */
    data object LocationOff : ListStatus

    /** No BLE hardware. */
    data object Unsupported : ListStatus

    data class Failed(
        val kind: ScanFailure.Kind,
    ) : ListStatus
}

/** One row of the list; display-ready. */
data class DeviceRowUi(
    val address: String,
    val name: String?,
    val rssi: Int,
    /** 0..4 bars. */
    val signalLevel: Int,
    val isConnectable: Boolean,
    /** What a decoder recognised in the advertisement, shown as a small chip; null when nothing matched. */
    val kind: DecoderId? = null,
)

data class DeviceListUiState(
    val status: ListStatus,
    val devices: List<DeviceRowUi>,
    val hideUnnamed: Boolean,
    /** True once the user has started a scan since the last Clear; picks the empty-state wording. */
    val hasScanned: Boolean,
) {
    val isScanning: Boolean get() = status == ListStatus.Scanning

    /** The Start/Stop button is usable (Stop while scanning, Start when ready or retrying after a failure). */
    val canToggleScan: Boolean get() =
        status == ListStatus.Scanning || status == ListStatus.Idle ||
            status is ListStatus.Failed

    /** Environment problems that replace the list area with an explanation. */
    val isBlocked: Boolean
        get() =
            status is ListStatus.PermissionNeeded || status == ListStatus.BluetoothOff ||
                status == ListStatus.LocationOff || status == ListStatus.Unsupported
}

private const val LEVEL_4_MIN_RSSI = -55
private const val LEVEL_3_MIN_RSSI = -67
private const val LEVEL_2_MIN_RSSI = -80
private const val LEVEL_4 = 4
private const val LEVEL_3 = 3
private const val LEVEL_2 = 2
private const val LEVEL_1 = 1

/** Maps RSSI (dBm) to 1..4 bars. */
fun signalLevel(rssi: Int): Int =
    when {
        rssi >= LEVEL_4_MIN_RSSI -> LEVEL_4
        rssi >= LEVEL_3_MIN_RSSI -> LEVEL_3
        rssi >= LEVEL_2_MIN_RSSI -> LEVEL_2
        else -> LEVEL_1
    }

internal fun BleDevice.toRowUi() =
    DeviceRowUi(
        address = address,
        name = name,
        rssi = rssi,
        signalLevel = signalLevel(rssi),
        isConnectable = isConnectable,
        kind = decoded.primaryKind(),
    )

/** Pure derivation of the screen status. Order matters: the first applicable blocker wins. */
@Suppress("LongParameterList")
internal fun deriveStatus(
    adapter: AdapterState,
    permission: PermissionState,
    permanentlyDenied: Boolean,
    scanning: Boolean,
    failure: ScanFailure?,
): ListStatus =
    when {
        adapter == AdapterState.UNSUPPORTED -> ListStatus.Unsupported
        permission is PermissionState.Denied -> ListStatus.PermissionNeeded(permanentlyDenied)
        adapter == AdapterState.OFF -> ListStatus.BluetoothOff
        adapter == AdapterState.LOCATION_OFF -> ListStatus.LocationOff
        scanning -> ListStatus.Scanning
        failure != null -> ListStatus.Failed(failure.kind)
        else -> ListStatus.Idle
    }
