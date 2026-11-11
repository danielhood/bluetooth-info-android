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
import dev.danielhood.btscan.domain.parser.ServiceUuids

private const val EDDYSTONE_UUID16 = 0xFEAA
private const val FRAME_UID = 0x00
private const val FRAME_URL = 0x10
private const val FRAME_TLM = 0x20
private const val FRAME_EID = 0x30
private const val UID_MIN_SIZE = 18
private const val UID_NAMESPACE_END = 12
private const val UID_INSTANCE_END = 18
private const val URL_MIN_SIZE = 4
private const val URL_ENCODED_OFFSET = 3
private const val TLM_SIZE = 14
private const val TLM_VERSION_PLAIN = 0x00
private const val TLM_VERSION_ENCRYPTED = 0x01
private const val TLM_UNSUPPORTED_TEMPERATURE = 0x8000
private const val TLM_UNSUPPORTED_VOLTAGE = 0
private const val EID_SIZE = 10
private const val TLM_BATTERY_OFFSET = 2
private const val TLM_TEMPERATURE_OFFSET = 4
private const val TLM_COUNT_OFFSET = 6
private const val TLM_UPTIME_OFFSET = 10
private const val FIXED_POINT_DIVISOR = 256.0
private const val DECISECOND = 10.0
private const val SIGN_BIT_16 = 0x10000
private const val SIGN_LIMIT_16 = 0x8000
private const val PRINTABLE_FIRST = 0x21
private const val PRINTABLE_LAST = 0x7E
private const val EXPANSION_LAST = 0x0D

/** Scheme prefix byte -> text, per the Eddystone-URL specification. */
private val URL_SCHEMES = listOf("http://www.", "https://www.", "http://", "https://")

/** Expansion code byte (0x00..0x0D) -> text, per the Eddystone-URL specification. */
private val URL_EXPANSIONS =
    listOf(
        ".com/",
        ".org/",
        ".edu/",
        ".net/",
        ".info/",
        ".biz/",
        ".gov/",
        ".com",
        ".org",
        ".edu",
        ".net",
        ".info",
        ".biz",
        ".gov",
    )

/**
 * Eddystone (github.com/google/eddystone): service data for 16-bit UUID 0xFEAA, first byte is the frame type.
 * Decodes UID, URL, unencrypted TLM and EID (EID is opaque and shown as such; encrypted TLM is flagged only).
 */
object EddystoneDecoder : PayloadDecoder {
    override fun decode(ad: Advertisement): List<Decoded> =
        ad.serviceData
            .filter { ServiceUuids.shortForm(it.uuid) == EDDYSTONE_UUID16 }
            .mapNotNull { decodeFrame(it.data) }

    private fun decodeFrame(frame: Bytes): Decoded? =
        if (frame.size == 0) {
            null
        } else {
            when (frame[0]) {
                FRAME_UID -> uid(frame)
                FRAME_URL -> url(frame)
                FRAME_TLM -> tlm(frame)
                FRAME_EID -> eid(frame)
                else -> null
            }
        }

    private fun uid(frame: Bytes): Decoded? =
        if (frame.size < UID_MIN_SIZE) {
            null
        } else {
            Decoded(
                DecoderId.EDDYSTONE_UID,
                Confidence.SPECIFICATION,
                listOf(
                    DecodedField(FieldKey.TX_POWER_0M_DBM, dbm(frame[1])),
                    DecodedField(FieldKey.EDDYSTONE_NAMESPACE, FieldValue.Hex(frame.slice(2, UID_NAMESPACE_END))),
                    DecodedField(
                        FieldKey.EDDYSTONE_INSTANCE,
                        FieldValue.Hex(frame.slice(UID_NAMESPACE_END, UID_INSTANCE_END)),
                    ),
                ),
            )
        }

    private fun url(frame: Bytes): Decoded? {
        val decoded = if (frame.size < URL_MIN_SIZE) null else expandUrl(frame)
        return decoded?.let {
            Decoded(
                DecoderId.EDDYSTONE_URL,
                Confidence.SPECIFICATION,
                listOf(
                    DecodedField(FieldKey.TX_POWER_0M_DBM, dbm(frame[1])),
                    DecodedField(FieldKey.EDDYSTONE_URL, FieldValue.Text(it)),
                ),
            )
        }
    }

    /** Null when the scheme byte, an expansion code or a character is outside the specification. */
    private fun expandUrl(frame: Bytes): String? {
        val scheme = URL_SCHEMES.getOrNull(frame[2])
        val parts = (URL_ENCODED_OFFSET until frame.size).map { urlPart(frame[it]) }
        return if (scheme == null || parts.any { it == null }) null else scheme + parts.joinToString("")
    }

    private fun urlPart(b: Int): String? =
        when {
            b <= EXPANSION_LAST -> URL_EXPANSIONS[b]
            b in PRINTABLE_FIRST..PRINTABLE_LAST -> b.toChar().toString()
            else -> null
        }

    private fun tlm(frame: Bytes): Decoded? =
        when {
            frame.size < 2 -> null
            frame[1] == TLM_VERSION_ENCRYPTED -> Decoded(DecoderId.EDDYSTONE_TLM, Confidence.ENCRYPTED, emptyList())
            frame[1] != TLM_VERSION_PLAIN || frame.size != TLM_SIZE -> null
            else -> Decoded(DecoderId.EDDYSTONE_TLM, Confidence.SPECIFICATION, tlmFields(frame))
        }

    private fun tlmFields(frame: Bytes): List<DecodedField> {
        val voltage = bigEndian16(frame, TLM_BATTERY_OFFSET)
        val rawTemperature = bigEndian16(frame, TLM_TEMPERATURE_OFFSET)
        return buildList {
            if (voltage != TLM_UNSUPPORTED_VOLTAGE) {
                add(DecodedField(FieldKey.BATTERY_MV, FieldValue.Number(voltage.toDouble(), Unit.MILLIVOLT)))
            }
            if (rawTemperature != TLM_UNSUPPORTED_TEMPERATURE) {
                val signed = if (rawTemperature >= SIGN_LIMIT_16) rawTemperature - SIGN_BIT_16 else rawTemperature
                add(DecodedField(FieldKey.TEMPERATURE_C, FieldValue.Number(signed / FIXED_POINT_DIVISOR, Unit.CELSIUS)))
            }
            add(
                DecodedField(
                    FieldKey.ADVERTISEMENT_COUNT,
                    FieldValue.Number(bigEndian32(frame, TLM_COUNT_OFFSET).toDouble()),
                ),
            )
            add(
                DecodedField(
                    FieldKey.UPTIME_SECONDS,
                    FieldValue.Number(bigEndian32(frame, TLM_UPTIME_OFFSET) / DECISECOND, Unit.SECONDS),
                ),
            )
        }
    }

    private fun eid(frame: Bytes): Decoded? =
        if (frame.size != EID_SIZE) {
            null
        } else {
            Decoded(
                DecoderId.EDDYSTONE_EID,
                Confidence.ENCRYPTED,
                listOf(
                    DecodedField(FieldKey.TX_POWER_0M_DBM, dbm(frame[1])),
                    DecodedField(FieldKey.EDDYSTONE_EID, FieldValue.Hex(frame.slice(2, EID_SIZE))),
                ),
            )
        }

    private fun dbm(unsigned: Int) = FieldValue.Number(unsigned.toByte().toDouble(), Unit.DBM)
}
