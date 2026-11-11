package dev.danielhood.btscan.domain.decode.decoders

import dev.danielhood.btscan.domain.decode.Confidence
import dev.danielhood.btscan.domain.decode.Decoded
import dev.danielhood.btscan.domain.decode.DecodedField
import dev.danielhood.btscan.domain.decode.DecoderId
import dev.danielhood.btscan.domain.decode.FieldKey
import dev.danielhood.btscan.domain.decode.FieldValue
import dev.danielhood.btscan.domain.decode.PayloadDecoder
import dev.danielhood.btscan.domain.model.Advertisement
import dev.danielhood.btscan.domain.model.Bytes
import dev.danielhood.btscan.domain.parser.ServiceUuids

private const val FAST_PAIR_UUID16 = 0xFE2C
private const val MODEL_ID_SIZE = 3

/**
 * Google Fast Pair provider advertisement (developers.google.com/nearby/fast-pair/specifications/service/provider):
 * service data for 16-bit UUID 0xFE2C. Discoverable (pairing) mode carries exactly the 3-byte model ID; the
 * not-discoverable mode carries account data (a filter), which is not interpreted. The model ID is shown as a
 * number only: the app never maps it to a product, brand or owner.
 */
object FastPairDecoder : PayloadDecoder {
    override fun decode(ad: Advertisement): List<Decoded> =
        ad.serviceData
            .filter { ServiceUuids.shortForm(it.uuid) == FAST_PAIR_UUID16 }
            .mapNotNull { decodeFrame(it.data) }

    private fun decodeFrame(data: Bytes): Decoded? =
        when {
            data.size == MODEL_ID_SIZE -> {
                Decoded(
                    DecoderId.FAST_PAIR,
                    Confidence.SPECIFICATION,
                    listOf(
                        DecodedField(FieldKey.FAST_PAIR_DISCOVERABLE, FieldValue.Bool(true)),
                        DecodedField(FieldKey.FAST_PAIR_MODEL_ID, FieldValue.Hex(data)),
                    ),
                )
            }

            data.size > MODEL_ID_SIZE -> {
                Decoded(
                    DecoderId.FAST_PAIR,
                    Confidence.SPECIFICATION,
                    listOf(DecodedField(FieldKey.FAST_PAIR_DISCOVERABLE, FieldValue.Bool(false))),
                )
            }

            else -> {
                null
            }
        }
}
