package dev.danielhood.btscan.domain.parser

private const val BIT_BR_EDR_NOT_SUPPORTED = 0x04
private const val BIT_SIMULTANEOUS_CONTROLLER = 0x08
private const val BIT_SIMULTANEOUS_HOST = 0x10

/** Bits of the advertising "Flags" structure. */
enum class AdFlag(
    val mask: Int,
) {
    LE_LIMITED_DISCOVERABLE(0x01),
    LE_GENERAL_DISCOVERABLE(0x02),
    BR_EDR_NOT_SUPPORTED(BIT_BR_EDR_NOT_SUPPORTED),
    SIMULTANEOUS_LE_BR_EDR_CONTROLLER(BIT_SIMULTANEOUS_CONTROLLER),
    SIMULTANEOUS_LE_BR_EDR_HOST(BIT_SIMULTANEOUS_HOST),
    ;

    companion object {
        fun decode(flags: Int): List<AdFlag> = entries.filter { flags and it.mask != 0 }
    }
}
