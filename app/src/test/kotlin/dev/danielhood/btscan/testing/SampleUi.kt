package dev.danielhood.btscan.testing

import dev.danielhood.btscan.domain.decode.Confidence
import dev.danielhood.btscan.domain.decode.Decoded
import dev.danielhood.btscan.domain.decode.DecodedField
import dev.danielhood.btscan.domain.decode.DecoderId
import dev.danielhood.btscan.domain.decode.Decoders
import dev.danielhood.btscan.domain.decode.FieldKey
import dev.danielhood.btscan.domain.decode.FieldValue
import dev.danielhood.btscan.domain.decode.Unit
import dev.danielhood.btscan.domain.decode.decoders.RuuviV5Decoder
import dev.danielhood.btscan.domain.estimate.DistanceBand
import dev.danielhood.btscan.domain.model.Advertisement
import dev.danielhood.btscan.domain.model.Bytes
import dev.danielhood.btscan.domain.model.DataStatus
import dev.danielhood.btscan.domain.model.ManufacturerData
import dev.danielhood.btscan.domain.model.Phy
import dev.danielhood.btscan.domain.model.RadioInfo
import dev.danielhood.btscan.domain.model.ScanFailure
import dev.danielhood.btscan.domain.parser.AdvertisementParser
import dev.danielhood.btscan.domain.system.AddressType
import dev.danielhood.btscan.domain.system.BondState
import dev.danielhood.btscan.domain.system.DeviceClassInfo
import dev.danielhood.btscan.domain.system.DeviceType
import dev.danielhood.btscan.domain.system.SystemDeviceInfo
import dev.danielhood.btscan.ui.detail.SystemInfoState
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

    /** Rows as the beacons, sensors and vendors scenarios would show them. */
    val devicesWithKinds =
        listOf(
            DeviceRowUi("C0:FF:EE:00:00:01", "Ruuvi 884F", -48, 4, true, DecoderId.RUUVI_V5),
            DeviceRowUi("C0:FF:EE:00:00:02", null, -55, 4, false, DecoderId.IBEACON),
            DeviceRowUi("C0:FF:EE:00:00:03", null, -62, 3, false, DecoderId.EDDYSTONE_UID),
            DeviceRowUi("C0:FF:EE:00:00:04", null, -70, 2, false, DecoderId.TRACKER_HINT),
            DeviceRowUi("C0:FF:EE:00:00:05", "Plain Device", -78, 2, true),
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

    val beacon =
        thermo.copy(
            name = "Eddystone Beacon",
            decoded =
                listOf(
                    Decoded(
                        DecoderId.IBEACON,
                        Confidence.SPECIFICATION,
                        listOf(
                            DecodedField(FieldKey.BEACON_UUID, FieldValue.Text("E2C56DB5-DFFB-48D2-B060-D0F5A71096E0")),
                            DecodedField(FieldKey.BEACON_MAJOR, FieldValue.Number(1.0)),
                            DecodedField(FieldKey.BEACON_MINOR, FieldValue.Number(2.0)),
                            DecodedField(FieldKey.MEASURED_POWER_1M_DBM, FieldValue.Number(-59.0, Unit.DBM)),
                        ),
                    ),
                    Decoded(
                        DecoderId.EDDYSTONE_TLM,
                        Confidence.SPECIFICATION,
                        listOf(
                            DecodedField(FieldKey.BATTERY_MV, FieldValue.Number(3000.0, Unit.MILLIVOLT)),
                            DecodedField(FieldKey.TEMPERATURE_C, FieldValue.Number(24.5, Unit.CELSIUS)),
                        ),
                    ),
                    Decoded(
                        DecoderId.EDDYSTONE_EID,
                        Confidence.ENCRYPTED,
                        listOf(
                            DecodedField(FieldKey.EDDYSTONE_EID, FieldValue.Hex(Bytes(ByteArray(8) { it.toByte() }))),
                        ),
                    ),
                ),
        )

    /** Ruuvi format 5 documentation example plus an encrypted BTHome frame, decoded by the real decoders. */
    val sensor =
        thermo.copy(
            name = "Ruuvi 884F",
            decoded =
                RuuviV5Decoder.decode(
                    Advertisement(
                        manufacturerData =
                            listOf(
                                ManufacturerData(
                                    0x0499,
                                    Bytes(
                                        "0512FC5394C37C0004FFFC040CAC364200CDCBB8334C884F"
                                            .chunked(2)
                                            .map { it.toInt(16).toByte() }
                                            .toByteArray(),
                                    ),
                                ),
                            ),
                    ),
                ),
        )

    /** A Find My-style frame decoded by the real decoders: an Apple message label plus the tracker hint. */
    val vendor =
        thermo.copy(
            name = null,
            decoded =
                Decoders.decode(
                    Advertisement(
                        manufacturerData = listOf(ManufacturerData(0x004C, Bytes(byteArrayOf(0x12, 0x01, 0x00)))),
                    ),
                ),
        )

    /** Extended advertising with every radio field present plus both estimates. */
    val radio =
        thermo.copy(
            name = "Radio Demo",
            radio = RadioInfo(Phy.LE_1M, Phy.LE_2M, false, 3, 8, 80, DataStatus.COMPLETE),
            intervalEstimateMs = 500,
            distanceBand = DistanceBand.ABOUT_ONE_METRE,
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

    /** A paired dual-mode headset as the system describes it (all fields present). */
    val systemInfoHeadset =
        SystemInfoState.Loaded(
            SystemDeviceInfo(
                name = "Buds Pro",
                alias = "My buds",
                bondState = BondState.BONDED,
                deviceType = DeviceType.DUAL,
                deviceClass = DeviceClassInfo.of(0x0418),
                addressType = AddressType.PUBLIC,
                cachedUuids = listOf(0x110B, 0x111E, 0x180F).map { AdvertisementParser.uuidFromShort(it.toLong()) },
            ),
        )

    /** The system knows almost nothing: every missing value must read "Not available", never a guess. */
    val systemInfoSparse =
        SystemInfoState.Loaded(
            SystemDeviceInfo(
                name = null,
                alias = null,
                bondState = BondState.NONE,
                deviceType = null,
                deviceClass = null,
                addressType = null,
            ),
        )

    fun loaded(
        device: dev.danielhood.btscan.ui.detail.DeviceDetailUi = thermo,
        outOfRange: Boolean = false,
        systemInfo: SystemInfoState = SystemInfoState.NeedsPermission(permanentlyDenied = false),
    ) = dev.danielhood.btscan.ui.detail.DeviceDetailUiState.Loaded(
        device = device,
        outOfRange = outOfRange,
        firstSeenAgoSeconds = 75,
        lastSeenAgoSeconds = if (outOfRange) 41 else 1,
        systemInfo = systemInfo,
    )
}
