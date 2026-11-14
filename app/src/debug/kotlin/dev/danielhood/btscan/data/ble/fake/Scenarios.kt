package dev.danielhood.btscan.data.ble.fake

import dev.danielhood.btscan.domain.model.AdapterState
import dev.danielhood.btscan.domain.model.AddressType
import dev.danielhood.btscan.domain.model.Bytes
import dev.danielhood.btscan.domain.model.DataStatus
import dev.danielhood.btscan.domain.model.Phy
import dev.danielhood.btscan.domain.model.RadioInfo
import dev.danielhood.btscan.domain.model.ScanFailure
import dev.danielhood.btscan.domain.parser.AdvertisementParser
import dev.danielhood.btscan.domain.system.BondState
import dev.danielhood.btscan.domain.system.DeviceClassInfo
import dev.danielhood.btscan.domain.system.DeviceType
import dev.danielhood.btscan.domain.system.SystemDeviceInfo

private const val MANY_COUNT = 200
private const val MANY_PHASES = 10
private const val MANY_INTERVAL_MS = 1000L
private const val MANY_BASE_RSSI = -95
private const val MANY_RSSI_SPREAD = 50
private const val SECOND_MS = 1000L
private const val HEX_RADIX = 16
private const val BYTE_BITS = 8
private const val ADDR_BYTE_MASK = 0xFF
private const val TICK_MS = FakeBleScanner.TICK_MS

/** Named, deterministic fake scan scenarios (docs/functional-spec.md "Fake scenarios"). */
object Scenarios {
    const val DEFAULT = "default"

    private fun hex2(v: Int) = v.toString(HEX_RADIX).padStart(2, '0').uppercase()

    private fun addr(n: Int): String {
        val high = hex2((n shr BYTE_BITS) and ADDR_BYTE_MASK)
        val low = hex2(n and ADDR_BYTE_MASK)
        return "C0:FF:EE:00:$high:$low"
    }

    private val default =
        Scenario(
            name = "default",
            devices =
                listOf(
                    FakeDevice(
                        address = addr(1),
                        advertisement =
                            advertisement {
                                flags(0x06)
                                completeName("Thermo Sensor")
                                uuids16(0x181A, 0x180F)
                                txPower(-4)
                            },
                        baseRssi = -48,
                    ),
                    FakeDevice(
                        address = addr(2),
                        advertisement =
                            advertisement {
                                flags(0x06)
                                completeName("HR Strap")
                                uuids16(0x180D)
                                manufacturer(0x0059, 0x01, 0x02, 0x03)
                            },
                        baseRssi = -60,
                        phaseMs = 100,
                    ),
                    FakeDevice(
                        address = addr(3),
                        advertisement =
                            advertisement {
                                flags(0x1A)
                                manufacturer(0x004C, 0x02, 0x15, 0xAA, 0xBB, 0xCC, 0xDD, 0x00, 0x01, 0x00, 0x02, 0xC5)
                            },
                        baseRssi = -72,
                        isConnectable = false,
                        phaseMs = 200,
                    ),
                    FakeDevice(
                        address = addr(4),
                        advertisement = advertisement { flags(0x06) },
                        baseRssi = -80,
                        vanishAtMs = 40 * SECOND_MS,
                        phaseMs = 300,
                    ),
                    FakeDevice(
                        address = addr(5),
                        advertisement = advertisement { manufacturer(0x0006, 0x01, 0x09, 0x20, 0x02) },
                        baseRssi = -85,
                        phaseMs = 400,
                    ),
                    FakeDevice(
                        address = addr(6),
                        advertisement =
                            advertisement {
                                flags(0x06)
                                completeName("Late Arrival")
                                txPower(0)
                            },
                        baseRssi = -55,
                        appearAtMs = 3 * SECOND_MS,
                    ),
                ),
        )

    private val empty = Scenario(name = "empty", devices = emptyList())

    private val many =
        Scenario(
            name = "many",
            devices =
                List(MANY_COUNT) { i ->
                    FakeDevice(
                        address = addr(100 + i),
                        advertisement =
                            advertisement {
                                flags(0x06)
                                if (i % 3 != 0) completeName("Device ${i + 1}")
                                manufacturer(0x00E0 + i % MANY_PHASES, i and ADDR_BYTE_MASK)
                            },
                        baseRssi = MANY_BASE_RSSI + (i * 7) % MANY_RSSI_SPREAD,
                        intervalMs = MANY_INTERVAL_MS,
                        phaseMs = (i % MANY_PHASES) * TICK_MS,
                    )
                },
        )

    /** Devices with minimal or malformed payloads, for parser and "not advertised" UI paths. */
    private val sparseFields =
        Scenario(
            name = "sparse-fields",
            devices =
                listOf(
                    FakeDevice(
                        address = addr(1),
                        advertisement = advertisement { completeName("Name Only") },
                        baseRssi = -50,
                    ),
                    FakeDevice(
                        address = addr(2),
                        advertisement = advertisement { manufacturer(0x0059, 0xDE, 0xAD) },
                        baseRssi = -62,
                        phaseMs = 100,
                    ),
                    // Structure claims length 10 but only 3 bytes follow.
                    FakeDevice(
                        address = addr(3),
                        advertisement = advertisement { raw(0x0A, 0x09, 0x41, 0x42) },
                        baseRssi = -70,
                        phaseMs = 200,
                    ),
                    // Flags, then a zero length that ends parsing before the name.
                    FakeDevice(
                        address = addr(4),
                        advertisement =
                            advertisement {
                                flags(0x06)
                                raw(0x00, 0x09, 0x41)
                            },
                        baseRssi = -75,
                        phaseMs = 300,
                    ),
                    FakeDevice(
                        address = addr(5),
                        advertisement = advertisement { },
                        baseRssi = -90,
                        phaseMs = 400,
                    ),
                ),
        )

    /**
     * Beacon formats with exact bytes (see the decoder tests for the field values they must produce):
     * an iBeacon, one Eddystone device rotating UID/URL/TLM frames, a rotating Eddystone-EID and an AltBeacon.
     */
    private val beacons =
        Scenario(
            name = "beacons",
            devices =
                listOf(
                    FakeDevice(
                        address = addr(1),
                        advertisement = BeaconFrames.ibeacon(),
                        baseRssi = -55,
                        isConnectable = false,
                    ),
                    FakeDevice(
                        address = addr(2),
                        advertisement = BeaconFrames.eddystoneUid(),
                        alternatePayloads = listOf(BeaconFrames.eddystoneUrl(), BeaconFrames.eddystoneTlm()),
                        baseRssi = -65,
                        isConnectable = false,
                        phaseMs = 100,
                    ),
                    FakeDevice(
                        address = addr(3),
                        advertisement = BeaconFrames.eddystoneEid(),
                        baseRssi = -75,
                        isConnectable = false,
                        phaseMs = 200,
                    ),
                    FakeDevice(
                        address = addr(4),
                        advertisement = BeaconFrames.altBeacon(),
                        baseRssi = -80,
                        isConnectable = false,
                        phaseMs = 300,
                    ),
                ),
        )

    /** Sensor formats with exact bytes (see SensorFrames): Ruuvi format 5, BTHome plain and BTHome encrypted. */
    private val sensors =
        Scenario(
            name = "sensors",
            devices =
                listOf(
                    FakeDevice(address = addr(1), advertisement = SensorFrames.ruuvi(), baseRssi = -52),
                    FakeDevice(
                        address = addr(2),
                        advertisement = SensorFrames.bthome(),
                        baseRssi = -62,
                        isConnectable = false,
                        phaseMs = 100,
                    ),
                    FakeDevice(
                        address = addr(3),
                        advertisement = SensorFrames.bthomeEncrypted(),
                        baseRssi = -72,
                        isConnectable = false,
                        phaseMs = 200,
                    ),
                ),
        )

    /** Vendor hints (see VendorFrames): Apple message types, Fast Pair both modes, tracker-style identifiers. */
    private val vendors =
        Scenario(
            name = "vendors",
            devices =
                listOf(
                    vendor(1, VendorFrames.appleFindMy(), -50),
                    vendor(2, VendorFrames.appleAirDrop(), -56),
                    vendor(3, VendorFrames.appleProximityPairing(), -62),
                    vendor(4, VendorFrames.appleOtherMessage(), -68),
                    vendor(5, VendorFrames.fastPairDiscoverable(), -72),
                    vendor(6, VendorFrames.fastPairNotDiscoverable(), -76),
                    vendor(7, VendorFrames.tileStyle(), -80),
                    vendor(8, VendorFrames.smartTagStyle(), -84),
                    vendor(9, advertisement { flags(0x06) }, -88),
                ),
        )

    private fun vendor(n: Int, payload: Bytes, rssi: Int) =
        FakeDevice(address = addr(n), advertisement = payload, baseRssi = rssi, phaseMs = (n % 5) * 100L)

    /**
     * Radio metadata and interval estimates: extended advertising on 2M with a set id and periodic interval,
     * a coded-PHY device whose radio reports no TX power, a truncated extended report, a slow legacy device and
     * a fast calibrated iBeacon (distance band). Packet intervals are 500, 500, 500, 1000 and 100 ms.
     * Signal levels are 12 dB apart so rows keep their order despite drift.
     */
    private val radio =
        Scenario(
            name = "radio",
            devices =
                listOf(
                    FakeDevice(
                        address = addr(1),
                        advertisement = advertisement { flags(0x06) },
                        baseRssi = -45,
                        addressType = AddressType.RANDOM,
                        radio = RadioInfo(Phy.LE_1M, Phy.LE_2M, false, 3, 8, 80, DataStatus.COMPLETE),
                    ),
                    FakeDevice(
                        address = addr(2),
                        advertisement = advertisement { flags(0x06) },
                        baseRssi = -57,
                        phaseMs = 100,
                        addressType = AddressType.PUBLIC,
                        radio = RadioInfo(Phy.LE_CODED, Phy.LE_CODED, false, 1, null, null, DataStatus.COMPLETE),
                    ),
                    FakeDevice(
                        address = addr(3),
                        advertisement = advertisement { flags(0x06) },
                        baseRssi = -81,
                        phaseMs = 200,
                        addressType = AddressType.ANONYMOUS,
                        radio = RadioInfo(Phy.LE_1M, Phy.LE_1M, false, null, 0, null, DataStatus.TRUNCATED),
                    ),
                    FakeDevice(
                        address = addr(4),
                        advertisement = advertisement { flags(0x06) },
                        baseRssi = -93,
                        intervalMs = 1000,
                        phaseMs = 300,
                        addressType = AddressType.RANDOM,
                        radio = RadioInfo(Phy.LE_1M, null, true, null, null, null, null),
                    ),
                    FakeDevice(
                        address = addr(5),
                        advertisement = BeaconFrames.ibeacon(),
                        baseRssi = -69,
                        intervalMs = 100,
                        isConnectable = false,
                        addressType = AddressType.PUBLIC,
                        radio = RadioInfo(Phy.LE_1M, null, true, null, null, null, null),
                    ),
                ),
        )

    /**
     * Tier 2a: devices for which the fake system knows different amounts, to show every state of the System
     * information section: a paired dual-mode headset with an alias, an LE sensor, a device being paired, and one the
     * system knows nothing about.
     */
    private val systemInfo =
        Scenario(
            name = "system-info",
            devices =
                listOf(
                    FakeDevice(
                        address = addr(1),
                        advertisement =
                            advertisement {
                                flags(0x06)
                                completeName("Buds Pro")
                            },
                        baseRssi = -48,
                    ),
                    FakeDevice(
                        address = addr(2),
                        advertisement =
                            advertisement {
                                flags(0x06)
                                completeName("Thermo Sensor")
                            },
                        baseRssi = -60,
                        phaseMs = 100,
                    ),
                    FakeDevice(
                        address = addr(3),
                        advertisement = advertisement { flags(0x06) },
                        baseRssi = -72,
                        phaseMs = 200,
                    ),
                    FakeDevice(
                        address = addr(4),
                        advertisement = advertisement { flags(0x06) },
                        baseRssi = -84,
                        phaseMs = 300,
                    ),
                ),
            systemInfo =
                mapOf(
                    addr(1) to
                        SystemDeviceInfo(
                            name = "Buds Pro",
                            alias = "My buds",
                            bondState = BondState.BONDED,
                            deviceType = DeviceType.DUAL,
                            deviceClass = DeviceClassInfo.of(0x0418),
                            cachedUuids =
                                listOf(0x110B, 0x111E, 0x110E).map { AdvertisementParser.uuidFromShort(it.toLong()) },
                        ),
                    addr(2) to
                        SystemDeviceInfo(
                            name = "Thermo (system)",
                            alias = null,
                            bondState = BondState.NONE,
                            deviceType = DeviceType.LE,
                            deviceClass = null,
                        ),
                    addr(3) to
                        SystemDeviceInfo(
                            name = null,
                            alias = null,
                            bondState = BondState.BONDING,
                            deviceType = DeviceType.CLASSIC,
                            deviceClass = DeviceClassInfo.of(0x05C0),
                        ),
                ),
        )

    private val scanFails =
        Scenario(
            name = "scan-fails",
            devices = default.devices.take(2),
            failure = FakeFailure(ScanFailure.Kind.INTERNAL_ERROR, afterMs = 2 * SECOND_MS),
        )

    private val tooFrequent =
        Scenario(
            name = "too-frequent",
            devices = emptyList(),
            failure = FakeFailure(ScanFailure.Kind.SCANNING_TOO_FREQUENTLY),
        )

    private val bluetoothOff =
        Scenario(
            name = "bluetooth-off",
            devices = emptyList(),
            adapterState = AdapterState.OFF,
        )

    private val unsupported =
        Scenario(
            name = "unsupported",
            devices = emptyList(),
            adapterState = AdapterState.UNSUPPORTED,
        )

    val all: Map<String, Scenario> =
        listOf(
            default,
            empty,
            many,
            sparseFields,
            beacons,
            sensors,
            vendors,
            radio,
            systemInfo,
            scanFails,
            tooFrequent,
            bluetoothOff,
            unsupported,
        ).associateBy { it.name }

    /** Looks a scenario up by name; fails loudly (listing valid names) so a typo cannot silently pass. */
    fun require(name: String): Scenario =
        requireNotNull(all[name]) {
            "Unknown fake scenario '$name'. Known: ${all.keys.joinToString()}"
        }
}
