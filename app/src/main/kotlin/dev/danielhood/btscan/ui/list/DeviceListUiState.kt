package dev.danielhood.btscan.ui.list

import dev.danielhood.btscan.domain.decode.DecoderId
import dev.danielhood.btscan.domain.decode.primaryKind
import dev.danielhood.btscan.domain.model.AdapterState
import dev.danielhood.btscan.domain.model.BleDevice
import dev.danielhood.btscan.domain.model.ClassicInfo
import dev.danielhood.btscan.domain.model.DeviceSource
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

/** Which devices the list shows by how they were found. */
enum class SourceFilter { ALL, BLE, CLASSIC }

/** A one-line explanation shown under the controls after the app stopped one scan for the other. */
enum class ListNotice { BLE_STOPPED_FOR_CLASSIC, CLASSIC_STOPPED_FOR_BLE }

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
    val source: DeviceSource = DeviceSource.BLE,
    /** What a classic inquiry reported (class, pairing state); null for BLE-only devices. */
    val classic: ClassicInfo? = null,
)

data class DeviceListUiState(
    val status: ListStatus,
    val devices: List<DeviceRowUi>,
    val hideUnnamed: Boolean,
    /** True once the user has started a scan since the last Clear; picks the empty-state wording. */
    val hasScanned: Boolean,
    /** A classic inquiry is running (about 12 s, ends by itself). */
    val classicScanning: Boolean = false,
    /** The first-use notice for classic scanning is on screen; nothing starts until the user confirms it. */
    val classicNoticePending: Boolean = false,
    val notice: ListNotice? = null,
    val sourceFilter: SourceFilter = SourceFilter.ALL,
) {
    val isScanning: Boolean get() = status == ListStatus.Scanning

    /** The classic scan button is usable under the same conditions as the BLE one (permission, adapter, location). */
    val canStartClassic: Boolean get() = canToggleScan

    /** Rows carry a transport badge once any device came from a classic inquiry (otherwise everything is BLE). */
    val showSourceBadges: Boolean get() = devices.any { it.source != DeviceSource.BLE }

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
        source = source,
        classic = classic,
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
