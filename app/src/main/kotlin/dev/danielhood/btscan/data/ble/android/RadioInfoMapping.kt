package dev.danielhood.btscan.data.ble.android

import dev.danielhood.btscan.domain.model.DataStatus
import dev.danielhood.btscan.domain.model.Phy
import dev.danielhood.btscan.domain.model.RadioInfo

// Values of the android.bluetooth.le.ScanResult constants; kept as literals so the mapping is a pure function.
private const val PHY_LE_1M = 1
private const val PHY_LE_2M = 2
private const val PHY_LE_CODED = 3
private const val SID_NOT_PRESENT = 255
private const val TX_POWER_NOT_PRESENT = 127
private const val PERIODIC_INTERVAL_NOT_PRESENT = 0
private const val DATA_COMPLETE = 0
private const val DATA_TRUNCATED = 2

/** Maps platform scan-result radio fields to [RadioInfo], turning "not available" sentinels into null. */
internal fun radioInfoOf(
    primaryPhy: Int,
    secondaryPhy: Int,
    isLegacy: Boolean,
    advertisingSid: Int,
    txPower: Int,
    periodicIntervalUnits: Int,
    dataStatus: Int,
) = RadioInfo(
    primaryPhy = phyOf(primaryPhy),
    secondaryPhy = phyOf(secondaryPhy),
    isLegacy = isLegacy,
    advertisingSid = advertisingSid.takeUnless { it == SID_NOT_PRESENT },
    txPowerDbm = txPower.takeUnless { it == TX_POWER_NOT_PRESENT },
    periodicIntervalUnits = periodicIntervalUnits.takeUnless { it == PERIODIC_INTERVAL_NOT_PRESENT },
    dataStatus =
        when (dataStatus) {
            DATA_COMPLETE -> DataStatus.COMPLETE
            DATA_TRUNCATED -> DataStatus.TRUNCATED
            else -> null
        },
)

private fun phyOf(value: Int): Phy? =
    when (value) {
        PHY_LE_1M -> Phy.LE_1M
        PHY_LE_2M -> Phy.LE_2M
        PHY_LE_CODED -> Phy.LE_CODED
        else -> null
    }
