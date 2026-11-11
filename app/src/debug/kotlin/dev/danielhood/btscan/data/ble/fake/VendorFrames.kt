package dev.danielhood.btscan.data.ble.fake

import dev.danielhood.btscan.domain.model.Bytes

private const val APPLE = 0x004C
private const val FAST_PAIR = 0xFE2C
private const val TILE = 0xFEED
private const val SMARTTAG = 0xFD5A
private const val FLAGS_GENERAL = 0x06
private const val HEX_WIDTH = 2
private const val HEX_RADIX = 16
private const val FIND_MY_LENGTH = 0x19
private const val AIRDROP_LENGTH = 0x12
private const val PROXIMITY_PAIRING_LENGTH = 0x19
private const val OTHER_MESSAGE_LENGTH = 5
private const val SMARTTAG_PAYLOAD_SIZE = 20

/**
 * Vendor-hint payloads for the `vendors` scenario. All payload bytes are made-up filler (a counting pattern): only
 * the type bytes, lengths and service UUIDs are real, because those are all the decoders look at.
 */
object VendorFrames {
    private fun filler(
        count: Int,
    ) = (0 until count).joinToString(" ") { it.toString(HEX_RADIX).padStart(HEX_WIDTH, '0') }

    private fun apple(type: String, length: Int): Bytes =
        advertisement {
            flags(FLAGS_GENERAL)
            manufacturer(
                APPLE,
                hexBytes("$type ${length.toString(HEX_RADIX).padStart(HEX_WIDTH, '0')} ${filler(length)}"),
            )
        }

    /** Type 0x12, length 0x19. */
    fun appleFindMy(): Bytes = apple("12", FIND_MY_LENGTH)

    /** Type 0x05, length 0x12. */
    fun appleAirDrop(): Bytes = apple("05", AIRDROP_LENGTH)

    /** Type 0x07, length 0x19. */
    fun appleProximityPairing(): Bytes = apple("07", PROXIMITY_PAIRING_LENGTH)

    /** Type 0x10 is not named by this app: shows as "other message" with its code. */
    fun appleOtherMessage(): Bytes = apple("10", OTHER_MESSAGE_LENGTH)

    /** Discoverable mode: exactly the 3-byte model ID (made-up value 2A3B4C). */
    fun fastPairDiscoverable(): Bytes =
        advertisement {
            flags(FLAGS_GENERAL)
            uuids16(FAST_PAIR)
            serviceData16(FAST_PAIR, hexBytes("2A3B4C"))
        }

    /** Not-discoverable mode: account data longer than 3 bytes (filler). */
    fun fastPairNotDiscoverable(): Bytes =
        advertisement {
            flags(FLAGS_GENERAL)
            serviceData16(FAST_PAIR, hexBytes("00 11 22 33 44 55 66 77"))
        }

    fun tileStyle(): Bytes =
        advertisement {
            flags(FLAGS_GENERAL)
            uuids16(TILE)
            serviceData16(TILE, hexBytes("00 01 02 03 04 05 06 07"))
        }

    fun smartTagStyle(): Bytes =
        advertisement {
            flags(FLAGS_GENERAL)
            serviceData16(SMARTTAG, hexBytes(filler(SMARTTAG_PAYLOAD_SIZE)))
        }
}
