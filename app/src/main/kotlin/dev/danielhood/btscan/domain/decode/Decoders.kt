package dev.danielhood.btscan.domain.decode

import dev.danielhood.btscan.domain.decode.decoders.AltBeaconDecoder
import dev.danielhood.btscan.domain.decode.decoders.AppleContinuityDecoder
import dev.danielhood.btscan.domain.decode.decoders.BtHomeDecoder
import dev.danielhood.btscan.domain.decode.decoders.EddystoneDecoder
import dev.danielhood.btscan.domain.decode.decoders.FastPairDecoder
import dev.danielhood.btscan.domain.decode.decoders.IBeaconDecoder
import dev.danielhood.btscan.domain.decode.decoders.RuuviV5Decoder
import dev.danielhood.btscan.domain.decode.decoders.TrackerHintDecoder
import dev.danielhood.btscan.domain.model.Advertisement

/**
 * Registry of all decoders. Adding a decoder: one file in `decoders/`, one test, enum entries, strings, a scenario.
 * Decoders must never throw (guarded by the fuzz test in `DecodersTest`), so no exception handling here.
 */
object Decoders {
    val all: List<PayloadDecoder> =
        listOf(
            IBeaconDecoder,
            EddystoneDecoder,
            AltBeaconDecoder,
            RuuviV5Decoder,
            BtHomeDecoder,
            FastPairDecoder,
            AppleContinuityDecoder,
            TrackerHintDecoder,
        )

    fun decode(ad: Advertisement): List<Decoded> = all.flatMap { it.decode(ad) }
}
