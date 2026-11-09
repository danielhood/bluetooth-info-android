package dev.danielhood.btscan.testing

import dev.danielhood.btscan.domain.model.ScanFailure
import dev.danielhood.btscan.ui.list.DeviceListUiState
import dev.danielhood.btscan.ui.list.DeviceRowUi
import dev.danielhood.btscan.ui.list.ListStatus

object SampleUi {
    val devices =
        listOf(
            DeviceRowUi("C0:FF:EE:00:00:01", "Thermo Sensor", -48, 4, true),
            DeviceRowUi("C0:FF:EE:00:00:06", "Late Arrival", -55, 4, true),
            DeviceRowUi("C0:FF:EE:00:00:02", "HR Strap", -60, 3, true),
            DeviceRowUi("C0:FF:EE:00:00:03", null, -72, 2, false),
            DeviceRowUi("C0:FF:EE:00:00:04", null, -80, 2, true),
            DeviceRowUi("C0:FF:EE:00:00:05", null, -85, 1, true),
        )

    fun state(
        status: ListStatus = ListStatus.Idle,
        devices: List<DeviceRowUi> = emptyList(),
        hideUnnamed: Boolean = false,
        hasScanned: Boolean = devices.isNotEmpty(),
    ) = DeviceListUiState(status, devices, hideUnnamed, hasScanned)

    val failed = state(ListStatus.Failed(ScanFailure.Kind.INTERNAL_ERROR), devices.take(2))
}
