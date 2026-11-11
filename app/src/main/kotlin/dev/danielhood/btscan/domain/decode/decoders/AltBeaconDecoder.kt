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

private const val BEACON_CODE_HIGH = 0xBE
private const val BEACON_CODE_LOW = 0xAC
private const val ALTBEACON_PAYLOAD_SIZE = 24
private const val ID_OFFSET = 2
private const val ID_UUID_END = 18
private const val MAJOR_OFFSET = 18
private const val MINOR_OFFSET = 20
private const val REFERENCE_RSSI_OFFSET = 22

/**
 * AltBeacon (github.com/AltBeacon/spec): manufacturer data of any company, payload
 * `BE AC <id:20> <reference rssi:int8> <manufacturer reserved:1>` (24 bytes). The 20-byte id is shown the usual way
 * as a 16-byte UUID plus two 16-bit values; the spec itself only calls it a 20-byte identifier.
 */
object AltBeaconDecoder : PayloadDecoder {
    override fun decode(ad: Advertisement): List<Decoded> = ad.manufacturerData.mapNotNull { decodePayload(it.data) }

    private fun decodePayload(data: Bytes): Decoded? {
        val valid =
            data.size == ALTBEACON_PAYLOAD_SIZE && data[0] == BEACON_CODE_HIGH && data[1] == BEACON_CODE_LOW
        return if (valid) {
            Decoded(
                DecoderId.ALTBEACON,
                Confidence.SPECIFICATION,
                listOf(
                    DecodedField(
                        FieldKey.BEACON_UUID,
                        FieldValue.Text(formatUuid(data.slice(ID_OFFSET, ID_UUID_END))),
                    ),
                    DecodedField(FieldKey.BEACON_MAJOR, FieldValue.Number(bigEndian16(data, MAJOR_OFFSET).toDouble())),
                    DecodedField(FieldKey.BEACON_MINOR, FieldValue.Number(bigEndian16(data, MINOR_OFFSET).toDouble())),
                    DecodedField(
                        FieldKey.MEASURED_POWER_1M_DBM,
                        FieldValue.Number(data[REFERENCE_RSSI_OFFSET].toByte().toDouble(), Unit.DBM),
                    ),
                ),
            )
        } else {
            null
        }
    }
}
