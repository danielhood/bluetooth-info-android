package dev.danielhood.btscan.data.ble.fake

import dev.danielhood.btscan.domain.model.Bytes

private const val RUUVI = 0x0499
private const val BTHOME = 0xFCD2
private const val FLAGS_GENERAL = 0x06

/**
 * Sensor advertisement payloads shared by the fake scenarios (the decoder tests repeat the same hex).
 *
 * - Ruuvi: the documentation's valid-data example for data format 5: 24.3 C, 53.49 %, 1000.44 hPa, acceleration
 *   0.004 / -0.004 / 1.036 G, 2977 mV, 4 dBm, movement counter 66, sequence 205, MAC CB:B8:33:4C:88:4F.
 * - BTHome plain: packet id 9, battery 97 %, 25.06 C, 50.55 %, 1008.83 hPa (objects in ascending id order).
 * - BTHome encrypted: the encryption bit set; the rest is opaque.
 */
object SensorFrames {
    fun ruuvi(): Bytes =
        advertisement {
            flags(FLAGS_GENERAL)
            manufacturer(RUUVI, hexBytes("05 12FC 5394 C37C 0004 FFFC 040C AC36 42 00CD CBB8334C884F"))
        }

    fun bthome(): Bytes =
        advertisement {
            flags(FLAGS_GENERAL)
            serviceData16(BTHOME, hexBytes("40 0009 0161 02CA09 03BF13 04138A01"))
        }

    fun bthomeEncrypted(): Bytes =
        advertisement {
            flags(FLAGS_GENERAL)
            serviceData16(BTHOME, hexBytes("41 A1B2C3D4E5F60718 00000001 DEADBEEF"))
        }
}
