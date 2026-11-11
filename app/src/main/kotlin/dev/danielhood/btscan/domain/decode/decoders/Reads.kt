package dev.danielhood.btscan.domain.decode.decoders

import dev.danielhood.btscan.domain.model.Bytes

private const val BYTE_BITS = 8
private const val SHORT_BITS = 16
private const val SHORT_SIGN_LIMIT = 0x8000
private const val SHORT_RANGE = 0x10000

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

/** Unsigned little-endian integer of [size] bytes (1 to 7) at [offset]. */
internal fun littleEndian(data: Bytes, offset: Int, size: Int): Long {
    var value = 0L
    for (i in size - 1 downTo 0) value = (value shl BYTE_BITS) or data[offset + i].toLong()
    return value
}

/** Two's-complement interpretation of an unsigned 16-bit value. */
internal fun signed16(unsigned: Int): Int = if (unsigned >= SHORT_SIGN_LIMIT) unsigned - SHORT_RANGE else unsigned
