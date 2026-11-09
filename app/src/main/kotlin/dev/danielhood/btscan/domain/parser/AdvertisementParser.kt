package dev.danielhood.btscan.domain.parser

import dev.danielhood.btscan.domain.model.Advertisement
import dev.danielhood.btscan.domain.model.Bytes
import dev.danielhood.btscan.domain.model.ManufacturerData
import dev.danielhood.btscan.domain.model.ServiceData
import java.util.UUID

private const val AD_FLAGS = 0x01
private const val AD_UUID16_INCOMPLETE = 0x02
private const val AD_UUID16_COMPLETE = 0x03
private const val AD_UUID32_INCOMPLETE = 0x04
private const val AD_UUID32_COMPLETE = 0x05
private const val AD_UUID128_INCOMPLETE = 0x06
private const val AD_UUID128_COMPLETE = 0x07
private const val AD_NAME_SHORT = 0x08
private const val AD_NAME_COMPLETE = 0x09
private const val AD_TX_POWER = 0x0A
private const val AD_SERVICE_DATA_16 = 0x16
private const val AD_SERVICE_DATA_32 = 0x20
private const val AD_SERVICE_DATA_128 = 0x21
private const val AD_MANUFACTURER = 0xFF

private const val UUID16_SIZE = 2
private const val UUID32_SIZE = 4
private const val UUID128_SIZE = 16
private const val COMPANY_ID_SIZE = 2
private const val BYTE_BITS = 8
private const val UUID_HALF_SIZE = 8
private const val UUID_BASE_MSB_LOW = 0x1000L
private const val UUID_BASE_LSB = 0x800000805F9B34FBuL
private const val UUID_SHORT_SHIFT = 32

/**
 * Decodes Bluetooth advertisement payloads (a sequence of length/type/data structures, multi-byte
 * values little-endian). Lenient by design: a zero length ends parsing, a structure that overruns the payload
 * is dropped, unknown types are ignored, and malformed structures never throw.
 */
object AdvertisementParser {
    fun parse(payload: Bytes): Advertisement {
        val acc = Accumulator()
        var i = 0
        while (i < payload.size) {
            val length = payload[i]
            val end = i + 1 + length
            if (length == 0 || end > payload.size) break
            acc.accept(type = payload[i + 1], data = payload.slice(i + 2, end))
            i = end
        }
        return acc.build()
    }

    /** Expands a 16- or 32-bit Bluetooth short UUID using the Bluetooth Base UUID. */
    fun uuidFromShort(value: Long): UUID =
        UUID((value shl UUID_SHORT_SHIFT) or UUID_BASE_MSB_LOW, UUID_BASE_LSB.toLong())

    private class Accumulator {
        var flags: Int? = null
        var txPower: Int? = null
        var shortName: String? = null
        var completeName: String? = null
        val uuids = LinkedHashSet<UUID>()
        val serviceData = LinkedHashMap<UUID, Bytes>()
        val manufacturer = LinkedHashMap<Int, Bytes>()

        fun accept(type: Int, data: Bytes) {
            when (type) {
                AD_FLAGS -> if (data.size >= 1) flags = data[0]
                AD_TX_POWER -> if (data.size >= 1) txPower = data[0].toByte().toInt()
                AD_NAME_SHORT -> shortName = decodeName(data)
                AD_NAME_COMPLETE -> completeName = decodeName(data)
                AD_UUID16_INCOMPLETE, AD_UUID16_COMPLETE -> addUuids(data, UUID16_SIZE)
                AD_UUID32_INCOMPLETE, AD_UUID32_COMPLETE -> addUuids(data, UUID32_SIZE)
                AD_UUID128_INCOMPLETE, AD_UUID128_COMPLETE -> addUuids(data, UUID128_SIZE)
                AD_SERVICE_DATA_16 -> addServiceData(data, UUID16_SIZE)
                AD_SERVICE_DATA_32 -> addServiceData(data, UUID32_SIZE)
                AD_SERVICE_DATA_128 -> addServiceData(data, UUID128_SIZE)
                AD_MANUFACTURER -> addManufacturer(data)
            }
        }

        private fun decodeName(data: Bytes): String = String(data.toByteArray(), Charsets.UTF_8)

        private fun addUuids(data: Bytes, size: Int) {
            var offset = 0
            while (offset + size <= data.size) {
                uuids += readUuid(data, offset, size)
                offset += size
            }
        }

        private fun addServiceData(data: Bytes, uuidSize: Int) {
            if (data.size < uuidSize) return
            serviceData[readUuid(data, 0, uuidSize)] = data.slice(uuidSize, data.size)
        }

        private fun addManufacturer(data: Bytes) {
            if (data.size < COMPANY_ID_SIZE) return
            manufacturer[data[0] or (data[1] shl BYTE_BITS)] = data.slice(COMPANY_ID_SIZE, data.size)
        }

        fun build() =
            Advertisement(
                localName = completeName ?: shortName,
                txPowerDbm = txPower,
                flags = flags,
                serviceUuids = uuids.toList(),
                serviceData = serviceData.map { ServiceData(it.key, it.value) },
                manufacturerData = manufacturer.map { ManufacturerData(it.key, it.value) },
            )

        private fun readUuid(data: Bytes, offset: Int, size: Int): UUID =
            if (size == UUID128_SIZE) {
                UUID(
                    readLongBigEndianFromLittle(data, offset + UUID_HALF_SIZE),
                    readLongBigEndianFromLittle(data, offset),
                )
            } else {
                uuidFromShort(readLittleEndian(data, offset, size))
            }

        private fun readLittleEndian(data: Bytes, offset: Int, size: Int): Long {
            var value = 0L
            for (k in size - 1 downTo 0) value = (value shl BYTE_BITS) or data[offset + k].toLong()
            return value
        }

        /** Reads 8 little-endian bytes starting at [offset] as one big-endian-ordered Long (UUID halves). */
        private fun readLongBigEndianFromLittle(data: Bytes, offset: Int): Long =
            readLittleEndian(data, offset, UUID_HALF_SIZE)
    }
}
