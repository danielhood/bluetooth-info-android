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

object SampleDetail {
    val thermo =
        dev.danielhood.btscan.ui.detail.DeviceDetailUi(
            address = "C0:FF:EE:00:00:01",
            name = "Thermo Sensor",
            rssi = -48,
            signalLevel = 4,
            isConnectable = true,
            txPowerDbm = -4,
            advertisementCount = 42,
            services =
                listOf(
                    dev.danielhood.btscan.ui.detail
                        .ServiceUi("0000181a-0000-1000-8000-00805f9b34fb", "Environmental Sensing"),
                    dev.danielhood.btscan.ui.detail
                        .ServiceUi("0000180f-0000-1000-8000-00805f9b34fb", "Battery"),
                ),
            manufacturers =
                listOf(
                    dev.danielhood.btscan.ui.detail
                        .ManufacturerUi("0x0059", "Nordic Semiconductor ASA", "01 02 03"),
                ),
            serviceData =
                listOf(
                    dev.danielhood.btscan.ui.detail
                        .ServiceDataUi("0000180f-0000-1000-8000-00805f9b34fb", "Battery", "64"),
                ),
            flagsHex = "0x06",
            flags =
                listOf(
                    dev.danielhood.btscan.domain.parser.AdFlag.LE_GENERAL_DISCOVERABLE,
                    dev.danielhood.btscan.domain.parser.AdFlag.BR_EDR_NOT_SUPPORTED,
                ),
            rawHex = "02 01 06 0E 09 54 68 65 72 6D 6F 20 53 65 6E 73 6F 72",
        )

    val sparse =
        dev.danielhood.btscan.ui.detail.DeviceDetailUi(
            address = "C0:FF:EE:00:00:05",
            name = null,
            rssi = -88,
            signalLevel = 1,
            isConnectable = false,
            txPowerDbm = null,
            advertisementCount = 3,
            services = emptyList(),
            manufacturers = emptyList(),
            serviceData = emptyList(),
            flagsHex = null,
            flags = emptyList(),
            rawHex = "",
        )

    fun loaded(device: dev.danielhood.btscan.ui.detail.DeviceDetailUi = thermo, outOfRange: Boolean = false) =
        dev.danielhood.btscan.ui.detail.DeviceDetailUiState.Loaded(
            device = device,
            outOfRange = outOfRange,
            firstSeenAgoSeconds = 75,
            lastSeenAgoSeconds = if (outOfRange) 41 else 1,
        )
}
