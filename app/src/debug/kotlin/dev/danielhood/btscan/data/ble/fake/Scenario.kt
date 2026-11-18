package dev.danielhood.btscan.data.ble.fake

import dev.danielhood.btscan.domain.model.AdapterState
import dev.danielhood.btscan.domain.model.AddressType
import dev.danielhood.btscan.domain.model.Bytes
import dev.danielhood.btscan.domain.model.RadioInfo
import dev.danielhood.btscan.domain.model.ScanFailure
import dev.danielhood.btscan.domain.system.BondState
import dev.danielhood.btscan.domain.system.DeviceClassInfo
import dev.danielhood.btscan.domain.system.SystemDeviceInfo

/** A simulated device. All times are milliseconds since the scan started. Output is fully deterministic. */
data class FakeDevice(
    val address: String,
    val advertisement: Bytes,
    val baseRssi: Int,
    val isConnectable: Boolean = true,
    val appearAtMs: Long = 0,
    val vanishAtMs: Long? = null,
    val intervalMs: Long = 500,
    /** Offset of this device's advertising slot within its interval; spreads load. Multiple of the tick. */
    val phaseMs: Long = 0,
    /** Further payloads sent in rotation after [advertisement] (for example alternating Eddystone frames). */
    val alternatePayloads: List<Bytes> = emptyList(),
    /** Radio metadata reported with each advertisement; null like most fakes (and legacy-only scanners). */
    val radio: RadioInfo? = null,
    /** Address type the fake scan result reports; null like a phone older than Android 15. */
    val addressType: AddressType? = null,
)

/** Makes the scan throw [kind] once [afterMs] has elapsed (0 = on start). */
data class FakeFailure(
    val kind: ScanFailure.Kind,
    val afterMs: Long = 0,
)

private const val DEFAULT_CLASSIC_RSSI = -60

/** A device a fake classic inquiry reports [atMs] after it starts. */
data class FakeClassicSighting(
    val address: String,
    val name: String? = null,
    val rssi: Int? = DEFAULT_CLASSIC_RSSI,
    val deviceClass: DeviceClassInfo? = null,
    val bondState: BondState? = BondState.NONE,
    val atMs: Long = 0,
) {
    companion object {
        /** The fake inquiry ends by itself this long after it starts, like the real one (about 12 s). */
        const val INQUIRY_MS = 12_000L
    }
}

data class Scenario(
    val name: String,
    val devices: List<FakeDevice>,
    val failure: FakeFailure? = null,
    val adapterState: AdapterState = AdapterState.ON,
    /** What the fake system knows about each address (Tier 2a); addresses not listed are unknown to it. */
    val systemInfo: Map<String, SystemDeviceInfo> = emptyMap(),
    /** What a classic inquiry finds (Tier 2b); empty means the inquiry finds nothing. */
    val classic: List<FakeClassicSighting> = emptyList(),
    /** How devices answer a profile lookup (Tier 2c), by address. */
    val profiles: Map<String, FakeProfileBehaviour> = emptyMap(),
)
