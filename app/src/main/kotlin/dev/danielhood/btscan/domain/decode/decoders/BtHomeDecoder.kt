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

private const val BTHOME_UUID16 = 0xFCD2
private const val ENCRYPTED_BIT = 0x01
private const val VERSION_SHIFT = 5
private const val VERSION_2 = 2

/** One supported object: id, data size, signedness, divisor (value = raw / divisor), key and unit. */
private class ObjectSpec(
    val size: Int,
    val signed: Boolean,
    val divisor: Double,
    val key: FieldKey,
    val unit: Unit,
)

private const val HUNDREDTH = 100.0
private const val THOUSANDTH = 1000.0
private const val TENTH = 10.0
private const val ONE = 1.0

/** Object ids and layouts from bthome.io/format (v2). All multi-byte values are little-endian. */
private val OBJECTS: Map<Int, ObjectSpec> =
    mapOf(
        0x00 to ObjectSpec(1, false, ONE, FieldKey.PACKET_ID, Unit.NONE),
        0x01 to ObjectSpec(1, false, ONE, FieldKey.BATTERY_PERCENT, Unit.PERCENT),
        0x02 to ObjectSpec(2, true, HUNDREDTH, FieldKey.TEMPERATURE_C, Unit.CELSIUS),
        0x03 to ObjectSpec(2, false, HUNDREDTH, FieldKey.HUMIDITY_PERCENT, Unit.PERCENT),
        0x04 to ObjectSpec(3, false, HUNDREDTH, FieldKey.PRESSURE_HPA, Unit.HECTOPASCAL),
        0x05 to ObjectSpec(3, false, HUNDREDTH, FieldKey.ILLUMINANCE_LUX, Unit.LUX),
        0x0C to ObjectSpec(2, false, THOUSANDTH, FieldKey.VOLTAGE_V, Unit.VOLT),
        0x0D to ObjectSpec(2, false, ONE, FieldKey.PM25, Unit.MICROGRAM_PER_CUBIC_METRE),
        0x0E to ObjectSpec(2, false, ONE, FieldKey.PM10, Unit.MICROGRAM_PER_CUBIC_METRE),
        0x12 to ObjectSpec(2, false, ONE, FieldKey.CO2_PPM, Unit.PPM),
        0x13 to ObjectSpec(2, false, ONE, FieldKey.TVOC, Unit.MICROGRAM_PER_CUBIC_METRE),
        0x2E to ObjectSpec(1, false, ONE, FieldKey.HUMIDITY_PERCENT, Unit.PERCENT),
        0x45 to ObjectSpec(2, true, TENTH, FieldKey.TEMPERATURE_C, Unit.CELSIUS),
    )

/**
 * BTHome v2 (bthome.io/format): service data for 16-bit UUID 0xFCD2. First byte is device information (bit 0
 * encrypted, bits 5-7 version = 2), then a sequence of `<object id> <little-endian data>`. Parsing stops at the first
 * object it does not know (its length is unknowable) or that is cut short; what was read before is kept.
 * Encrypted frames are reported as encrypted with no values (decrypting needs a key the app never has).
 */
object BtHomeDecoder : PayloadDecoder {
    override fun decode(ad: Advertisement): List<Decoded> =
        ad.serviceData
            .filter { ServiceUuids.shortForm(it.uuid) == BTHOME_UUID16 }
            .mapNotNull { decodeFrame(it.data) }

    private fun decodeFrame(frame: Bytes): Decoded? {
        val info = if (frame.size == 0) null else frame[0]
        return when {
            info == null || info ushr VERSION_SHIFT != VERSION_2 -> {
                null
            }

            info and ENCRYPTED_BIT != 0 -> {
                Decoded(DecoderId.BTHOME, Confidence.ENCRYPTED, emptyList())
            }

            else -> {
                parseObjects(
                    frame,
                ).takeIf { it.isNotEmpty() }?.let { Decoded(DecoderId.BTHOME, Confidence.SPECIFICATION, it) }
            }
        }
    }

    private fun parseObjects(frame: Bytes): List<DecodedField> {
        val fields = mutableListOf<DecodedField>()
        var offset = 1
        while (offset < frame.size) {
            val spec = OBJECTS[frame[offset]]
            if (spec == null || offset + 1 + spec.size > frame.size) break
            fields += DecodedField(spec.key, FieldValue.Number(value(frame, offset + 1, spec), spec.unit))
            offset += 1 + spec.size
        }
        return fields
    }

    private fun value(frame: Bytes, offset: Int, spec: ObjectSpec): Double {
        val raw = littleEndian(frame, offset, spec.size)
        val signedRaw = if (spec.signed) signed16(raw.toInt()).toLong() else raw
        return signedRaw / spec.divisor
    }
}
