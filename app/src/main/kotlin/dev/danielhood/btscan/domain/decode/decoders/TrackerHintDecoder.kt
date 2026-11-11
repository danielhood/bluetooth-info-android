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
import dev.danielhood.btscan.domain.parser.ServiceUuids

private const val TILE_UUID16 = 0xFEED
private const val SMARTTAG_UUID16 = 0xFD5A
private const val SMARTTAG_UNREGISTERED_UUID16 = 0xFD59

/**
 * A single cautious hint that an advertisement resembles a location tracker family. Inputs, all from published
 * research (not vendor specifications): Apple message type 0x12 (furiousMAC/continuity), Tile service UUID 0xFEED
 * (arXiv 2510.00350) and Samsung SmartTag service UUIDs 0xFD5A and 0xFD59 (arXiv 2210.14702). The result carries a
 * family label only, is always an unofficial hint, and never says whose device it is or that it is following anyone:
 * other devices, including phones, can advertise the same identifiers.
 */
object TrackerHintDecoder : PayloadDecoder {
    override fun decode(ad: Advertisement): List<Decoded> {
        val families =
            buildList {
                if (APPLE_FIND_MY_TYPE in appleMessageTypes(ad)) add(LabelKey.TRACKER_FIND_MY)
                val uuids = (ad.serviceUuids + ad.serviceData.map { it.uuid }).mapNotNull(ServiceUuids::shortForm)
                if (TILE_UUID16 in uuids) add(LabelKey.TRACKER_TILE)
                if (SMARTTAG_UUID16 in uuids || SMARTTAG_UNREGISTERED_UUID16 in uuids) add(LabelKey.TRACKER_SMARTTAG)
            }
        return if (families.isEmpty()) {
            emptyList()
        } else {
            listOf(
                Decoded(
                    DecoderId.TRACKER_HINT,
                    Confidence.UNOFFICIAL_HINT,
                    families.map { DecodedField(FieldKey.TRACKER_FAMILY, FieldValue.Label(it)) },
                ),
            )
        }
    }
}
