package dev.danielhood.btscan.domain.model

private const val HEX_DIGITS = "0123456789ABCDEF"
private const val BYTE_MASK = 0xFF
private const val NIBBLE_BITS = 4
private const val NIBBLE_MASK = 0x0F

/** Immutable byte sequence with value equality (unlike a raw ByteArray, safe inside data classes). */
class Bytes(
    bytes: ByteArray,
) {
    private val data = bytes.copyOf()

    val size: Int get() = data.size

    /** Unsigned value of the byte at [index]. */
    operator fun get(index: Int): Int = data[index].toInt() and BYTE_MASK

    fun toByteArray(): ByteArray = data.copyOf()

    fun slice(from: Int, until: Int): Bytes = Bytes(data.copyOfRange(from, until))

    fun toHex(separator: String = ""): String =
        buildString {
            data.forEachIndexed { i, b ->
                if (i > 0) append(separator)
                val v = b.toInt() and BYTE_MASK
                append(HEX_DIGITS[v shr NIBBLE_BITS]).append(HEX_DIGITS[v and NIBBLE_MASK])
            }
        }

    override fun equals(other: Any?): Boolean = other is Bytes && data.contentEquals(other.data)

    override fun hashCode(): Int = data.contentHashCode()

    override fun toString(): String = "Bytes(${toHex()})"

    companion object {
        val EMPTY = Bytes(ByteArray(0))
    }
}
