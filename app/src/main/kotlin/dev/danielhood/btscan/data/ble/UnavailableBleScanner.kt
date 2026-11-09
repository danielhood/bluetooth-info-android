package dev.danielhood.btscan.data.ble

import dev.danielhood.btscan.domain.model.RawAdvertisement
import dev.danielhood.btscan.domain.model.ScanFailure
import dev.danielhood.btscan.domain.port.BleScanner
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/** Stand-in until the real Android scanner lands (roadmap M5): every scan fails with FEATURE_UNSUPPORTED. */
object UnavailableBleScanner : BleScanner {
    override fun scan(): Flow<RawAdvertisement> =
        flow {
            throw ScanFailure(ScanFailure.Kind.FEATURE_UNSUPPORTED)
        }
}
