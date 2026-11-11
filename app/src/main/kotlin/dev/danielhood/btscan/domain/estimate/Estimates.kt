package dev.danielhood.btscan.domain.estimate

import dev.danielhood.btscan.domain.decode.Decoded
import dev.danielhood.btscan.domain.decode.DecoderId
import dev.danielhood.btscan.domain.decode.FieldKey
import dev.danielhood.btscan.domain.decode.FieldValue
import dev.danielhood.btscan.domain.model.BleDevice
import java.time.Duration
import kotlin.math.pow

/** Coarse distance bands. Never shown as a number: signal strength is far too noisy for decimals. */
enum class DistanceBand { VERY_CLOSE, ABOUT_ONE_METRE, A_FEW_METRES, FAR }

private const val MIN_PACKETS = 5
private const val MIN_OBSERVED_MS = 2000L
private const val EDDYSTONE_LOSS_0M_TO_1M_DB = 41
private const val PATH_LOSS_EXPONENT = 2.0
private const val DB_PER_DECADE_FACTOR = 10.0
private const val VERY_CLOSE_METRES = 0.5
private const val ONE_METRE_UPPER = 2.0
private const val FEW_METRES_UPPER = 10.0
private const val MIN_PLAUSIBLE_POWER_DBM = -127

/** Estimates derived from what an advertisement carries. Every result is an estimate and the UI says so. */
object Estimates {
    /**
     * Average time between received packets, or null while there is too little data (fewer than 5 packets or under
     * 2 s observed). Packets the phone missed make this larger than the true advertising interval.
     */
    fun advertisingIntervalMs(device: BleDevice): Long? {
        val observed = Duration.between(device.firstSeen, device.lastSeen).toMillis()
        return if (device.advertisementCount >= MIN_PACKETS && observed >= MIN_OBSERVED_MS) {
            observed / (device.advertisementCount - 1)
        } else {
            null
        }
    }

    /** A distance band, only for devices that advertise a calibrated reference power. Otherwise null. */
    fun distanceBand(device: BleDevice): DistanceBand? =
        referencePowerAt1mDbm(device.decoded)?.let { band(distanceMetres(it, device.rssi)) }

    /**
     * Reference received power at 1 m: iBeacon measured power and AltBeacon reference RSSI are 1 m values;
     * Eddystone's calibrated power is at 0 m, so 41 dB (free-space loss at 1 m, 2.4 GHz) is subtracted.
     * Zero or implausible values mean "not calibrated" and give null.
     */
    internal fun referencePowerAt1mDbm(decoded: List<Decoded>): Int? =
        decoded
            .firstNotNullOfOrNull { d ->
                when (d.decoder) {
                    DecoderId.IBEACON, DecoderId.ALTBEACON -> {
                        d.power(FieldKey.MEASURED_POWER_1M_DBM)
                    }

                    DecoderId.EDDYSTONE_UID, DecoderId.EDDYSTONE_URL, DecoderId.EDDYSTONE_EID -> {
                        d.power(FieldKey.TX_POWER_0M_DBM)?.minus(EDDYSTONE_LOSS_0M_TO_1M_DB)
                    }

                    else -> {
                        null
                    }
                }
            }?.takeIf { it < 0 && it >= MIN_PLAUSIBLE_POWER_DBM }

    /** Log-distance path loss with exponent 2 (free space). */
    internal fun distanceMetres(referenceAt1mDbm: Int, rssiDbm: Int): Double =
        DB_PER_DECADE_FACTOR.pow((referenceAt1mDbm - rssiDbm) / (DB_PER_DECADE_FACTOR * PATH_LOSS_EXPONENT))

    internal fun band(metres: Double): DistanceBand =
        when {
            metres < VERY_CLOSE_METRES -> DistanceBand.VERY_CLOSE
            metres < ONE_METRE_UPPER -> DistanceBand.ABOUT_ONE_METRE
            metres < FEW_METRES_UPPER -> DistanceBand.A_FEW_METRES
            else -> DistanceBand.FAR
        }

    private fun Decoded.power(key: FieldKey): Int? =
        (fields.firstOrNull { it.key == key }?.value as? FieldValue.Number)?.value?.toInt()
}
