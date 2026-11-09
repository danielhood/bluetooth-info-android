package dev.danielhood.btscan.domain.parser

import java.util.UUID

/** Curated subset of Bluetooth SIG assigned 16-bit service UUIDs plus a few well-known vendor services. */
object ServiceUuids {
    private val shortNames: Map<Int, String> =
        mapOf(
            0x1800 to "Generic Access",
            0x1801 to "Generic Attribute",
            0x1802 to "Immediate Alert",
            0x1803 to "Link Loss",
            0x1804 to "Tx Power",
            0x1808 to "Glucose",
            0x1809 to "Health Thermometer",
            0x180A to "Device Information",
            0x180D to "Heart Rate",
            0x180F to "Battery",
            0x1810 to "Blood Pressure",
            0x1812 to "Human Interface Device",
            0x1814 to "Running Speed and Cadence",
            0x1816 to "Cycling Speed and Cadence",
            0x1818 to "Cycling Power",
            0x1819 to "Location and Navigation",
            0x181A to "Environmental Sensing",
            0x181D to "Weight Scale",
        )

    private val vendorNames: Map<UUID, String> =
        mapOf(
            UUID.fromString("6E400001-B5A3-F393-E0A9-E50E24DCCA9E") to "Nordic UART Service",
        )

    private const val BASE_MSB_LOW = 0x1000L
    private const val BASE_LSB = 0x800000805F9B34FBuL
    private const val SHORT_SHIFT = 32
    private const val SHORT_MASK = 0xFFFFFFFFL
    private const val MAX_SHORT16 = 0xFFFFL

    /** The 16-bit form of a Bluetooth-base UUID, or null if [uuid] is not one. */
    fun shortForm(uuid: UUID): Int? {
        val isBase =
            (uuid.mostSignificantBits and SHORT_MASK) == BASE_MSB_LOW &&
                uuid.leastSignificantBits == BASE_LSB.toLong()
        val value = uuid.mostSignificantBits ushr SHORT_SHIFT
        return if (isBase && value <= MAX_SHORT16) value.toInt() else null
    }

    fun nameOf(uuid: UUID): String? = shortForm(uuid)?.let { shortNames[it] } ?: vendorNames[uuid]
}
