package dev.danielhood.btscan.domain.decode.decoders

import dev.danielhood.btscan.domain.decode.Confidence
import dev.danielhood.btscan.domain.decode.Decoded
import dev.danielhood.btscan.domain.decode.DecodedField
import dev.danielhood.btscan.domain.decode.DecoderId
import dev.danielhood.btscan.domain.decode.FieldKey
import dev.danielhood.btscan.domain.decode.FieldValue
import dev.danielhood.btscan.domain.decode.LabelKey
import dev.danielhood.btscan.domain.decode.PayloadDecoder
import dev.danielhood.btscan.domain.model.Advertisement
import dev.danielhood.btscan.domain.model.Bytes

internal const val APPLE_COMPANY = 0x004C
private const val TYPE_IBEACON = 0x02
private const val TYPE_AIRDROP = 0x05
private const val TYPE_PROXIMITY_PAIRING = 0x07
internal const val APPLE_FIND_MY_TYPE = 0x12
private const val MAX_MESSAGES = 4
private const val HEADER_SIZE = 2

/** The type bytes of the messages in an Apple manufacturer payload, in order. Only well-formed chains count. */
internal fun appleMessageTypes(ad: Advertisement): List<Int> =
    ad.manufacturerData.filter { it.companyId == APPLE_COMPANY }.flatMap { messageTypes(it.data) }

/** Type, length, payload, repeated; stops at the first message that does not fit. Payloads are never read. */
private fun messageTypes(data: Bytes): List<Int> {
    val types = mutableListOf<Int>()
    var offset = 0
    while (offset + HEADER_SIZE <= data.size && types.size < MAX_MESSAGES) {
        val next = offset + HEADER_SIZE + data[offset + 1]
        if (next > data.size) break
        types += data[offset]
        offset = next
    }
    return if (types.firstOrNull() == TYPE_IBEACON) emptyList() else types
}

/**
 * Apple Continuity message labels. This format is known only from community reverse engineering
 * (github.com/furiousMAC/continuity), so every result is an **unofficial hint** and only the message type is named:
 * no payload is read and nothing is said about a model, an owner or what the device is doing. Only type bytes
 * confirmed in those notes get a name (AirDrop 0x05, Proximity Pairing 0x07, Find My 0x12); every other type is
 * shown as "other message" with its code. Type 0x02 is the iBeacon format and left to its own decoder.
 */
object AppleContinuityDecoder : PayloadDecoder {
    override fun decode(ad: Advertisement): List<Decoded> {
        val types = appleMessageTypes(ad)
        return if (types.isEmpty()) {
            emptyList()
        } else {
            listOf(Decoded(DecoderId.APPLE_CONTINUITY, Confidence.UNOFFICIAL_HINT, types.flatMap(::fieldsFor)))
        }
    }

    private fun fieldsFor(type: Int): List<DecodedField> {
        val label =
            when (type) {
                TYPE_AIRDROP -> LabelKey.APPLE_AIRDROP
                TYPE_PROXIMITY_PAIRING -> LabelKey.APPLE_PROXIMITY_PAIRING
                APPLE_FIND_MY_TYPE -> LabelKey.APPLE_FIND_MY
                else -> LabelKey.APPLE_OTHER_MESSAGE
            }
        val message = DecodedField(FieldKey.APPLE_MESSAGE, FieldValue.Label(label))
        return if (label == LabelKey.APPLE_OTHER_MESSAGE) {
            listOf(message, DecodedField(FieldKey.APPLE_TYPE_CODE, FieldValue.Hex(Bytes(byteArrayOf(type.toByte())))))
        } else {
            listOf(message)
        }
    }
}
