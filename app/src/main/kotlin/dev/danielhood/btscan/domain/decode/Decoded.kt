package dev.danielhood.btscan.domain.decode

import dev.danielhood.btscan.domain.model.Bytes

/** Which decoder (and, for formats with several frame kinds, which frame kind) produced a [Decoded]. */
enum class DecoderId {
    IBEACON,
    EDDYSTONE_UID,
    EDDYSTONE_URL,
    EDDYSTONE_TLM,
    EDDYSTONE_EID,
    ALTBEACON,
    RUUVI_V5,
    BTHOME,
}

/** How far a decoded result can be trusted. Shown to the user; see docs/phase2/policy-and-guardrails.md. */
enum class Confidence {
    /** Decoded from an open, published specification. */
    SPECIFICATION,

    /** Based on community reverse engineering; a label only, never a claim about model or owner. */
    UNOFFICIAL_HINT,

    /** The frame is encrypted or rotating; nothing meaningful can be decoded. */
    ENCRYPTED,
}

/** Language-neutral names of decoded values. The UI maps each key to a string resource. */
enum class FieldKey {
    BEACON_UUID,
    BEACON_MAJOR,
    BEACON_MINOR,
    MEASURED_POWER_1M_DBM,
    TX_POWER_0M_DBM,
    EDDYSTONE_NAMESPACE,
    EDDYSTONE_INSTANCE,
    EDDYSTONE_URL,
    EDDYSTONE_EID,
    BATTERY_MV,
    TEMPERATURE_C,
    ADVERTISEMENT_COUNT,
    UPTIME_SECONDS,
    HUMIDITY_PERCENT,
    PRESSURE_HPA,
    ACCEL_X_G,
    ACCEL_Y_G,
    ACCEL_Z_G,
    TX_POWER_DBM,
    MOVEMENT_COUNTER,
    SEQUENCE_NUMBER,
    DEVICE_MAC,
    BATTERY_PERCENT,
    ILLUMINANCE_LUX,
    VOLTAGE_V,
    PM25,
    PM10,
    CO2_PPM,
    TVOC,
    PACKET_ID,
}

/** Units for [FieldValue.Number]; the UI formats them. */
enum class Unit {
    NONE,
    DBM,
    MILLIVOLT,
    CELSIUS,
    SECONDS,
    PERCENT,
    HECTOPASCAL,
    G_FORCE,
    VOLT,
    LUX,
    PPM,
    MICROGRAM_PER_CUBIC_METRE,
}

sealed interface FieldValue {
    data class Text(
        val value: String,
    ) : FieldValue

    data class Number(
        val value: Double,
        val unit: Unit = Unit.NONE,
    ) : FieldValue

    data class Hex(
        val value: Bytes,
    ) : FieldValue
}

data class DecodedField(
    val key: FieldKey,
    val value: FieldValue,
)

/** One decoder's interpretation of an advertisement. [fields] may be empty (for example an encrypted frame). */
data class Decoded(
    val decoder: DecoderId,
    val confidence: Confidence,
    val fields: List<DecodedField>,
)
