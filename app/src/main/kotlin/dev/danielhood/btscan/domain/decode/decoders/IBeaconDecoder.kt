package dev.danielhood.btscan.domain.decode.decoders

import dev.danielhood.btscan.domain.decode.Confidence
import dev.danielhood.btscan.domain.decode.Decoded
import dev.danielhood.btscan.domain.decode.DecodedField
import dev.danielhood.btscan.domain.decode.DecoderId
import dev.danielhood.btscan.domain.decode.FieldKey
import dev.danielhood.btscan.domain.decode.FieldValue
import dev.danielhood.btscan.domain.decode.PayloadDecoder
import dev.danielhood.btscan.domain.decode.Unit
import dev.danielhood.btscan.domain.model.Advertisement
import dev.danielhood.btscan.domain.model.Bytes

private const val APPLE_COMPANY_ID = 0x004C
private const val IBEACON_TYPE = 0x02
private const val IBEACON_LENGTH = 0x15
private const val IBEACON_PAYLOAD_SIZE = 23
private const val UUID_OFFSET = 2
private const val MAJOR_OFFSET = 18
private const val MINOR_OFFSET = 20
private const val POWER_OFFSET = 22
private const val BYTE_BITS = 8
private const val SHORT_BITS = 16

/**
 * iBeacon: manufacturer data, company 0x004C, payload `02 15 <uuid:16> <major:2 BE> <minor:2 BE> <power:int8>`
 * (23 bytes). The layout is widely documented (for example the AltBeacon and Wikipedia descriptions); Apple's
 * own specification is licensed, so confidence is still [Confidence.SPECIFICATION] for the documented fields only.
 */
object IBeaconDecoder : PayloadDecoder {
    override fun decode(ad: Advertisement): List<Decoded> =
        ad.manufacturerData
            .filter { it.companyId == APPLE_COMPANY_ID }
            .mapNotNull { decodePayload(it.data) }

    private fun decodePayload(data: Bytes): Decoded? {
        val valid =
            data.size == IBEACON_PAYLOAD_SIZE && data[0] == IBEACON_TYPE && data[1] == IBEACON_LENGTH
        return if (valid) {
            Decoded(
                DecoderId.IBEACON,
                Confidence.SPECIFICATION,
                listOf(
                    DecodedField(
                        FieldKey.BEACON_UUID,
                        FieldValue.Text(formatUuid(data.slice(UUID_OFFSET, MAJOR_OFFSET))),
                    ),
                    DecodedField(FieldKey.BEACON_MAJOR, number(bigEndian16(data, MAJOR_OFFSET))),
                    DecodedField(FieldKey.BEACON_MINOR, number(bigEndian16(data, MINOR_OFFSET))),
                    DecodedField(
                        FieldKey.MEASURED_POWER_1M_DBM,
                        FieldValue.Number(data[POWER_OFFSET].toByte().toDouble(), Unit.DBM),
                    ),
                ),
            )
        } else {
            null
        }
    }

    private fun number(v: Int) = FieldValue.Number(v.toDouble())
}

internal fun bigEndian16(data: Bytes, offset: Int): Int = (data[offset] shl BYTE_BITS) or data[offset + 1]

internal fun bigEndian32(data: Bytes, offset: Int): Long =
    (bigEndian16(data, offset).toLong() shl SHORT_BITS) or bigEndian16(data, offset + 2).toLong()

private val UUID_GROUPS = listOf(4, 2, 2, 2, 6) // bytes per group of 8-4-4-4-12

/** Formats 16 bytes as the canonical 8-4-4-4-12 upper-case UUID text. */
internal fun formatUuid(bytes: Bytes): String {
    var index = 0
    return UUID_GROUPS.joinToString("-") { size ->
        bytes.slice(index, index + size).toHex().also { index += size }
    }
}
