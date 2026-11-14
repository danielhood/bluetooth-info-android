package dev.danielhood.btscan.domain.system

import java.util.UUID

/** Pairing state as Android reports it. Display only: the app never creates or changes a bond. */
enum class BondState { NONE, BONDING, BONDED }

enum class DeviceType { CLASSIC, LE, DUAL, UNKNOWN }

/**
 * What Android itself knows about a device, read without contacting it. Every field is null when the system has no
 * value: nothing is guessed or filled in. (The address type is deliberately not here: asked of a device made from just
 * an address, Android answers "public" whatever the truth, so it is read from the scan result instead.)
 */
data class SystemDeviceInfo(
    /** The name the system holds; may differ from the advertised name. */
    val name: String?,
    /** A name the user gave the device in system settings. */
    val alias: String?,
    val bondState: BondState,
    val deviceType: DeviceType?,
    val deviceClass: DeviceClassInfo?,
    /** Service UUIDs Android last learned; may be stale or empty. */
    val cachedUuids: List<UUID> = emptyList(),
)

/** Outcome of asking the system about a device. */
sealed interface SystemInfoResult {
    data class Available(
        val info: SystemDeviceInfo,
    ) : SystemInfoResult

    /** The runtime permission needed to read this is not (or no longer) granted. */
    data object PermissionMissing : SystemInfoResult

    /** The system cannot provide anything for this address (unknown device, adapter off, malformed address). */
    data object Unavailable : SystemInfoResult
}
