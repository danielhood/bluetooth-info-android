package dev.danielhood.btscan.domain.model

enum class Phy { LE_1M, LE_2M, LE_CODED }

enum class DataStatus { COMPLETE, TRUNCATED }

/**
 * Radio-level facts the platform reports with an advertisement. Values the platform marks as "not available"
 * (for example advertising set id 255 or TX power 127) are null here: the domain never sees sentinels.
 */
data class RadioInfo(
    val primaryPhy: Phy?,
    val secondaryPhy: Phy?,
    val isLegacy: Boolean,
    val advertisingSid: Int?,
    val txPowerDbm: Int?,
    /** Periodic advertising interval in units of 1.25 ms; null when there is no periodic advertising. */
    val periodicIntervalUnits: Int?,
    val dataStatus: DataStatus?,
)
