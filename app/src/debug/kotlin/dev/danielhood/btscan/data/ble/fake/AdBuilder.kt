package dev.danielhood.btscan.data.ble.fake

import dev.danielhood.btscan.domain.model.Bytes
import java.io.ByteArrayOutputStream
import java.util.UUID

private const val AD_FLAGS = 0x01
private const val AD_UUID16_COMPLETE = 0x03
private const val AD_UUID128_COMPLETE = 0x07
private const val AD_NAME_SHORT = 0x08
private const val AD_NAME_COMPLETE = 0x09
private const val AD_TX_POWER = 0x0A
private const val AD_SERVICE_DATA_16 = 0x16
private const val AD_MANUFACTURER = 0xFF
private const val BYTE_BITS = 8
private const val UUID_HALF_SIZE = 8

/** Builds raw advertisement payloads for fakes and tests: `advertisement { flags(6); completeName("X") }`. */
class AdBuilder {
    private val out = ByteArrayOutputStream()

    fun flags(value: Int) = structure(AD_FLAGS, byteArrayOf(value.toByte()))

    fun completeName(name: String) = structure(AD_NAME_COMPLETE, name.toByteArray())

    fun shortName(name: String) = structure(AD_NAME_SHORT, name.toByteArray())

    fun txPower(dbm: Int) = structure(AD_TX_POWER, byteArrayOf(dbm.toByte()))

    fun uuids16(vararg values: Int) =
        structure(AD_UUID16_COMPLETE, values.flatMap { littleEndian(it.toLong(), 2).toList() }.toByteArray())

    fun uuid128(uuid: UUID) {
        val le =
            littleEndian(uuid.leastSignificantBits, UUID_HALF_SIZE) +
                littleEndian(uuid.mostSignificantBits, UUID_HALF_SIZE)
        structure(AD_UUID128_COMPLETE, le)
    }

    fun serviceData16(uuid16: Int, vararg data: Int) =
        structure(AD_SERVICE_DATA_16, littleEndian(uuid16.toLong(), 2) + data.map { it.toByte() }.toByteArray())

    fun manufacturer(companyId: Int, vararg data: Int) =
        structure(AD_MANUFACTURER, littleEndian(companyId.toLong(), 2) + data.map { it.toByte() }.toByteArray())

    fun serviceData16(uuid16: Int, data: Bytes) =
        structure(AD_SERVICE_DATA_16, littleEndian(uuid16.toLong(), 2) + data.toByteArray())

    fun manufacturer(companyId: Int, data: Bytes) =
        structure(AD_MANUFACTURER, littleEndian(companyId.toLong(), 2) + data.toByteArray())

    /** Appends bytes verbatim, for building malformed payloads. */
    fun raw(vararg bytes: Int) {
        bytes.forEach { out.write(it) }
    }

    fun build() = Bytes(out.toByteArray())

    private fun structure(type: Int, data: ByteArray) {
        out.write(data.size + 1)
        out.write(type)
        out.write(data)
    }

    private fun littleEndian(value: Long, size: Int) =
        ByteArray(size) { (value shr (BYTE_BITS * it)).toByte() }
}

fun advertisement(block: AdBuilder.() -> Unit): Bytes = AdBuilder().apply(block).build()

private const val HEX_RADIX = 16
private const val HEX_PAIR = 2

/** Parses hex text (spaces ignored) into bytes; used to write payloads exactly as the specifications print them. */
fun hexBytes(text: String): Bytes {
    val clean = text.replace(" ", "")
    return Bytes(
        ByteArray(clean.length / HEX_PAIR) {
            clean.substring(it * HEX_PAIR, it * HEX_PAIR + HEX_PAIR).toInt(HEX_RADIX).toByte()
        },
    )
}
