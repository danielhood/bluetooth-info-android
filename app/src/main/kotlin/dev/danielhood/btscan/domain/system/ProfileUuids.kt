package dev.danielhood.btscan.domain.system

import java.util.UUID

private const val BASE_UUID_SUFFIX = "-0000-1000-8000-00805F9B34FB"
private const val HEX_WIDTH = 8

/** Curated names for classic Bluetooth profile and service-class UUIDs (Assigned Numbers). */
object ProfileUuids {
    private val names: Map<Int, String> =
        mapOf(
            0x1101 to "Serial Port",
            0x1105 to "OBEX Object Push",
            0x110A to "A2DP Audio Source",
            0x110B to "A2DP Audio Sink",
            0x110C to "AVRCP Target",
            0x110D to "A2DP Advanced Audio",
            0x110E to "AVRCP Remote Control",
            0x110F to "AVRCP Controller",
            0x1112 to "Headset Audio Gateway",
            0x1115 to "Personal Area Network User",
            0x1116 to "Network Access Point",
            0x111E to "Hands-Free",
            0x111F to "Hands-Free Audio Gateway",
            0x1124 to "Human Interface Device",
            0x112F to "Phone Book Access Server",
            0x1132 to "Message Access Server",
            0x1200 to "PnP Information",
        )

    private fun uuidOf(short: Int): UUID = UUID.fromString("%0${HEX_WIDTH}X$BASE_UUID_SUFFIX".format(short))

    private val byUuid: Map<UUID, String> = names.mapKeys { uuidOf(it.key) }

    /** The profile name for a Bluetooth-base UUID, or null. */
    fun nameOf(uuid: UUID): String? = byUuid[uuid]
}
