package dev.danielhood.btscan.domain.decode

import dev.danielhood.btscan.domain.model.Advertisement

/**
 * Interprets one advertisement. Pure and total: returns an empty list when the advertisement is not this format
 * (including truncated or corrupt frames) and never throws.
 */
fun interface PayloadDecoder {
    fun decode(ad: Advertisement): List<Decoded>
}
