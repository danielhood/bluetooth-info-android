package dev.danielhood.btscan.data.ble.fake

import dev.danielhood.btscan.domain.model.Bytes

private const val APPLE = 0x004C
private const val EDDYSTONE = 0xFEAA
private const val FLAGS_GENERAL = 0x06
private const val HEX_RADIX = 16
private const val HEX_PAIR = 2

private fun hex(s: String): Bytes {
    val clean = s.replace(" ", "")
    return Bytes(
        ByteArray(clean.length / HEX_PAIR) {
            clean.substring(it * HEX_PAIR, it * HEX_PAIR + HEX_PAIR).toInt(HEX_RADIX).toByte()
        },
    )
}

/**
 * Exact beacon payloads shared by the fake scenarios. The same byte strings appear in the decoder tests
 * (`IBeaconDecoderTest`, `EddystoneDecoderTest`) so a value change is caught on both sides.
 *
 * - iBeacon: UUID E2C56DB5-DFFB-48D2-B060-D0F5A71096E0, major 1, minor 2, measured power -59 dBm.
 * - Eddystone UID: namespace 00112233445566778899, instance AABBCCDDEEFF, power at 0 m -21 dBm.
 * - Eddystone URL: https://example.com/ (scheme 0x03, "example", expansion 0x00 ".com/").
 * - Eddystone TLM: 3000 mV, 24.5 degrees C, 1000 packets, 3600.0 s since power-on.
 * - Eddystone EID: opaque 0123456789ABCDEF.
 */
object BeaconFrames {
    private fun eddystone(frame: String) =
        advertisement {
            flags(FLAGS_GENERAL)
            uuids16(EDDYSTONE)
            serviceData16(EDDYSTONE, hex(frame))
        }

    fun ibeacon(): Bytes =
        advertisement {
            flags(FLAGS_GENERAL)
            manufacturer(APPLE, hex("02 15 E2C56DB5DFFB48D2B060D0F5A71096E0 0001 0002 C5"))
        }

    fun eddystoneUid(): Bytes = eddystone("00 EB 00112233445566778899 AABBCCDDEEFF 0000")

    fun eddystoneUrl(): Bytes = eddystone("10 EB 03 6578616D706C65 00")

    fun eddystoneTlm(): Bytes = eddystone("20 00 0BB8 1880 000003E8 00008CA0")

    fun eddystoneEid(): Bytes = eddystone("30 EB 0123456789ABCDEF")
}
