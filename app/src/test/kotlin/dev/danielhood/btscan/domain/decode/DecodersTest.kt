package dev.danielhood.btscan.domain.decode

import dev.danielhood.btscan.domain.model.Advertisement
import dev.danielhood.btscan.domain.model.Bytes
import dev.danielhood.btscan.domain.model.ManufacturerData
import dev.danielhood.btscan.domain.model.ServiceData
import dev.danielhood.btscan.domain.parser.AdvertisementParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random

class DecodersTest {
    @Test
    fun `nothing recognisable decodes to nothing`() {
        assertTrue(Decoders.decode(Advertisement()).isEmpty())
        assertTrue(Decoders.decode(AdvertisementParser.parse(hex("02 01 06 06 09 48 65 6C 6C 6F"))).isEmpty())
    }

    @Test
    fun `decoders work on a whole parsed advertisement`() {
        val bytes = hex("02 01 06 03 03 AA FE 0E 16 AA FE 10 EB 03 6578616D706C65 00")
        val decoded = Decoders.decode(AdvertisementParser.parse(bytes))
        assertEquals(listOf(DecoderId.EDDYSTONE_URL), decoded.map { it.decoder })
    }

    /** Random and structured-random inputs: decoders never throw and only emit finite numbers. */
    @Test
    fun `fuzz inputs never throw and numbers are finite`() {
        val random = Random(SEED)
        val prefixes =
            listOf(
                "",
                "0215",
                "00",
                "10",
                "20",
                "20 00",
                "30",
                "02",
                "05",
                "BEAC",
                "40",
                "41",
                "4001",
                "4002",
                "12",
                "1219",
                "0512",
            )
        repeat(ITERATIONS) {
            val tail = ByteArray(random.nextInt(MAX_LEN)).also { random.nextBytes(it) }
            val prefix = hex(prefixes[random.nextInt(prefixes.size)]).toByteArray()
            val data = Bytes(prefix + tail)
            val ad =
                Advertisement(
                    serviceData =
                        listOf(
                            ServiceData(AdvertisementParser.uuidFromShort(0xFEAA), data),
                            ServiceData(AdvertisementParser.uuidFromShort(0xFCD2), data),
                            ServiceData(AdvertisementParser.uuidFromShort(0xFE2C), data),
                            ServiceData(AdvertisementParser.uuidFromShort(0xFEED), data),
                        ),
                    manufacturerData =
                        listOf(
                            ManufacturerData(0x004C, data),
                            ManufacturerData(0x0499, data),
                            ManufacturerData(0x0118, data),
                        ),
                )
            val results = Decoders.decode(ad)
            results
                .flatMap { it.fields }
                .map { it.value }
                .filterIsInstance<FieldValue.Number>()
                .forEach { assertTrue(it.value.isFinite()) }
            assertHintsCarryNoFreeText(results)
        }
    }

    /** Wording rule: an unofficial hint is a fixed label or a code, never free text a decoder composed. */
    private fun assertHintsCarryNoFreeText(results: List<Decoded>) {
        results
            .filter { it.confidence == Confidence.UNOFFICIAL_HINT }
            .flatMap { it.fields }
            .forEach { assertTrue("hint with free text: $it", it.value !is FieldValue.Text) }
    }

    private companion object {
        const val SEED = 20261110L
        const val ITERATIONS = 5000
        const val MAX_LEN = 40
    }
}
