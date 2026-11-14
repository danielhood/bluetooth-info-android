package dev.danielhood.btscan.domain.model

import dev.danielhood.btscan.domain.system.BondState
import dev.danielhood.btscan.domain.system.DeviceClassInfo
import java.time.Instant

/**
 * One device reported by a classic Bluetooth inquiry. Classic discovery carries no advertisement bytes; everything
 * here is optional except the address because the platform reports what it has. [bondState] is null when it could not
 * be read (it needs the connect permission), never assumed.
 */
data class ClassicSighting(
    val address: String,
    val name: String?,
    val rssi: Int?,
    val deviceClass: DeviceClassInfo?,
    val bondState: BondState?,
    val timestamp: Instant,
)

/** What a classic inquiry told us about a device, kept on the merged [BleDevice]. */
data class ClassicInfo(
    val deviceClass: DeviceClassInfo?,
    val bondState: BondState?,
)
