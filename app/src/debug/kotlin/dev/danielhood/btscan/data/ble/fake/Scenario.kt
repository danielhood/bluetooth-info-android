package dev.danielhood.btscan.data.ble.fake

import dev.danielhood.btscan.domain.model.AdapterState
import dev.danielhood.btscan.domain.model.Bytes
import dev.danielhood.btscan.domain.model.RadioInfo
import dev.danielhood.btscan.domain.model.ScanFailure

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
)

/** Makes the scan throw [kind] once [afterMs] has elapsed (0 = on start). */
data class FakeFailure(
    val kind: ScanFailure.Kind,
    val afterMs: Long = 0,
)

data class Scenario(
    val name: String,
    val devices: List<FakeDevice>,
    val failure: FakeFailure? = null,
    val adapterState: AdapterState = AdapterState.ON,
)
