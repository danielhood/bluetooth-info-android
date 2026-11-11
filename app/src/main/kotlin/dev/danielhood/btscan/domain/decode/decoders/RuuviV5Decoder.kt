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

private const val RUUVI_COMPANY_ID = 0x0499
private const val FORMAT_5 = 0x05
private const val PAYLOAD_SIZE = 24
private const val TEMPERATURE_OFFSET = 1
private const val HUMIDITY_OFFSET = 3
private const val PRESSURE_OFFSET = 5
private const val ACCEL_X_OFFSET = 7
private const val ACCEL_Y_OFFSET = 9
private const val ACCEL_Z_OFFSET = 11
private const val POWER_OFFSET = 13
private const val MOVEMENT_OFFSET = 15
private const val SEQUENCE_OFFSET = 16
private const val MAC_OFFSET = 18
private const val MAC_END = 24

private const val INVALID_SIGNED_16 = 0x8000
private const val INVALID_UNSIGNED_16 = 0xFFFF
private const val INVALID_MOVEMENT = 0xFF
private const val INVALID_BATTERY = 0x7FF
private const val INVALID_TX_POWER = 0x1F
private const val TX_POWER_BITS = 5
private const val TX_POWER_MASK = 0x1F
private const val BATTERY_BASE_MV = 1600
private const val TX_POWER_STEP_DBM = 2
private const val TX_POWER_BASE_DBM = -40
private const val PRESSURE_BASE_PA = 50000
private const val TEMPERATURE_DIVISOR = 200.0 // 0.005 degrees C per unit
private const val HUMIDITY_DIVISOR = 400.0 // 0.0025 percent per unit
private const val PA_PER_HPA = 100.0
private const val MILLI_G_PER_G = 1000.0

/**
 * Ruuvi data format 5 (RAWv2), docs.ruuvi.com/communication/bluetooth-advertisements/data-format-5-rawv2:
 * manufacturer data, company 0x0499, 24 bytes, big-endian. Each field has a documented "invalid" value; invalid
 * fields are omitted rather than shown.
 */
object RuuviV5Decoder : PayloadDecoder {
    override fun decode(ad: Advertisement): List<Decoded> =
        ad.manufacturerData
            .filter { it.companyId == RUUVI_COMPANY_ID }
            .mapNotNull { decodePayload(it.data) }

    private fun decodePayload(data: Bytes): Decoded? =
        if (data.size == PAYLOAD_SIZE && data[0] == FORMAT_5) {
            Decoded(DecoderId.RUUVI_V5, Confidence.SPECIFICATION, fields(data))
        } else {
            null
        }

    private fun fields(data: Bytes): List<DecodedField> =
        buildList {
            signed(
                data,
                TEMPERATURE_OFFSET,
            )?.let { add(num(FieldKey.TEMPERATURE_C, it / TEMPERATURE_DIVISOR, Unit.CELSIUS)) }
            unsigned(
                data,
                HUMIDITY_OFFSET,
            )?.let { add(num(FieldKey.HUMIDITY_PERCENT, it / HUMIDITY_DIVISOR, Unit.PERCENT)) }
            unsigned(data, PRESSURE_OFFSET)?.let {
                add(num(FieldKey.PRESSURE_HPA, (it + PRESSURE_BASE_PA) / PA_PER_HPA, Unit.HECTOPASCAL))
            }
            signed(data, ACCEL_X_OFFSET)?.let { add(num(FieldKey.ACCEL_X_G, it / MILLI_G_PER_G, Unit.G_FORCE)) }
            signed(data, ACCEL_Y_OFFSET)?.let { add(num(FieldKey.ACCEL_Y_G, it / MILLI_G_PER_G, Unit.G_FORCE)) }
            signed(data, ACCEL_Z_OFFSET)?.let { add(num(FieldKey.ACCEL_Z_G, it / MILLI_G_PER_G, Unit.G_FORCE)) }
            addAll(powerFields(bigEndian16(data, POWER_OFFSET)))
            if (data[MOVEMENT_OFFSET] != INVALID_MOVEMENT) {
                add(num(FieldKey.MOVEMENT_COUNTER, data[MOVEMENT_OFFSET].toDouble()))
            }
            unsigned(data, SEQUENCE_OFFSET)?.let { add(num(FieldKey.SEQUENCE_NUMBER, it.toDouble())) }
            add(DecodedField(FieldKey.DEVICE_MAC, FieldValue.Hex(data.slice(MAC_OFFSET, MAC_END))))
        }

    /** Battery (11 bits, mV above 1600) and TX power (5 bits, steps of 2 dBm from -40) share one 16-bit word. */
    private fun powerFields(word: Int): List<DecodedField> =
        buildList {
            val battery = word ushr TX_POWER_BITS
            val tx = word and TX_POWER_MASK
            if (battery != INVALID_BATTERY) {
                add(num(FieldKey.BATTERY_MV, (battery + BATTERY_BASE_MV).toDouble(), Unit.MILLIVOLT))
            }
            if (tx != INVALID_TX_POWER) {
                add(num(FieldKey.TX_POWER_DBM, (tx * TX_POWER_STEP_DBM + TX_POWER_BASE_DBM).toDouble(), Unit.DBM))
            }
        }

    private fun signed(data: Bytes, offset: Int): Int? =
        bigEndian16(data, offset).takeUnless { it == INVALID_SIGNED_16 }?.let { signed16(it) }

    private fun unsigned(data: Bytes, offset: Int): Int? =
        bigEndian16(data, offset).takeUnless { it == INVALID_UNSIGNED_16 }

    private fun num(key: FieldKey, value: Double, unit: Unit = Unit.NONE) =
        DecodedField(key, FieldValue.Number(value, unit))
}
