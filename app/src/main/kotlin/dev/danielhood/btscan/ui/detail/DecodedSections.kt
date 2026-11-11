package dev.danielhood.btscan.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import dev.danielhood.btscan.R
import dev.danielhood.btscan.domain.decode.Confidence
import dev.danielhood.btscan.domain.decode.Decoded
import dev.danielhood.btscan.domain.decode.DecodedField
import dev.danielhood.btscan.domain.decode.DecoderId
import dev.danielhood.btscan.domain.decode.FieldKey
import dev.danielhood.btscan.domain.decode.FieldValue
import dev.danielhood.btscan.domain.decode.Unit
import dev.danielhood.btscan.ui.common.TestTags
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

/** "Decoded data": one card per decoder that recognised something. Nothing is shown when nothing matched. */
@Composable
internal fun DecodedSections(decoded: List<Decoded>) {
    if (decoded.isEmpty()) return
    HorizontalDivider()
    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = stringResource(R.string.detail_decoded),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        decoded.forEach { DecodedCard(it) }
    }
}

@Composable
private fun DecodedCard(decoded: Decoded) {
    val id = decoded.decoder.name.lowercase()
    Column {
        Text(
            text = stringResource(decoderTitle(decoded.decoder)),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.testTag(TestTags.detailDecoded(id)),
        )
        Text(
            text = stringResource(confidenceLabel(decoded.confidence)),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag(TestTags.decodedField(id, "confidence")),
        )
        decoded.fields.forEach { DecodedFieldRow(id, it) }
    }
}

@Composable
private fun DecodedFieldRow(decoderId: String, field: DecodedField) {
    Column(Modifier.padding(top = 4.dp)) {
        Text(stringResource(fieldLabel(field.key)), style = MaterialTheme.typography.labelMedium)
        Text(
            text = formatValue(field.value),
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = if (field.value is FieldValue.Number) FontFamily.Default else FontFamily.Monospace,
            modifier = Modifier.testTag(TestTags.decodedField(decoderId, field.key.name.lowercase())),
        )
    }
}

internal fun formatValue(value: FieldValue): String =
    when (value) {
        is FieldValue.Text -> {
            value.value
        }

        is FieldValue.Hex -> {
            value.value.toHex(" ")
        }

        is FieldValue.Number -> {
            val text = DecimalFormat("0.###", DecimalFormatSymbols(Locale.getDefault())).format(value.value)
            unitSuffix(value.unit)?.let { "$text $it" } ?: text
        }
    }

private fun unitSuffix(unit: Unit): String? =
    when (unit) {
        Unit.NONE -> null
        Unit.DBM -> "dBm"
        Unit.MILLIVOLT -> "mV"
        Unit.CELSIUS -> "°C"
        Unit.SECONDS -> "s"
        Unit.PERCENT -> "%"
        Unit.HECTOPASCAL -> "hPa"
        Unit.G_FORCE -> "G"
        Unit.VOLT -> "V"
        Unit.LUX -> "lx"
        Unit.PPM -> "ppm"
        Unit.MICROGRAM_PER_CUBIC_METRE -> "\u00B5g/m\u00B3"
    }

internal fun decoderTitle(id: DecoderId): Int =
    when (id) {
        DecoderId.IBEACON -> R.string.decoder_ibeacon
        DecoderId.EDDYSTONE_UID -> R.string.decoder_eddystone_uid
        DecoderId.EDDYSTONE_URL -> R.string.decoder_eddystone_url
        DecoderId.EDDYSTONE_TLM -> R.string.decoder_eddystone_tlm
        DecoderId.EDDYSTONE_EID -> R.string.decoder_eddystone_eid
        DecoderId.ALTBEACON -> R.string.decoder_altbeacon
        DecoderId.RUUVI_V5 -> R.string.decoder_ruuvi_v5
        DecoderId.BTHOME -> R.string.decoder_bthome
    }

internal fun confidenceLabel(confidence: Confidence): Int =
    when (confidence) {
        Confidence.SPECIFICATION -> R.string.confidence_specification
        Confidence.UNOFFICIAL_HINT -> R.string.confidence_hint
        Confidence.ENCRYPTED -> R.string.confidence_encrypted
    }

/** String resource per field key; `DecodedSectionsTest` asserts every [FieldKey] has an entry. */
private val FIELD_LABELS: Map<FieldKey, Int> =
    mapOf(
        FieldKey.BEACON_UUID to R.string.field_beacon_uuid,
        FieldKey.BEACON_MAJOR to R.string.field_beacon_major,
        FieldKey.BEACON_MINOR to R.string.field_beacon_minor,
        FieldKey.MEASURED_POWER_1M_DBM to R.string.field_measured_power_1m,
        FieldKey.TX_POWER_0M_DBM to R.string.field_tx_power_0m,
        FieldKey.EDDYSTONE_NAMESPACE to R.string.field_eddystone_namespace,
        FieldKey.EDDYSTONE_INSTANCE to R.string.field_eddystone_instance,
        FieldKey.EDDYSTONE_URL to R.string.field_eddystone_url,
        FieldKey.EDDYSTONE_EID to R.string.field_eddystone_eid,
        FieldKey.BATTERY_MV to R.string.field_battery,
        FieldKey.TEMPERATURE_C to R.string.field_temperature,
        FieldKey.ADVERTISEMENT_COUNT to R.string.field_advertisement_count,
        FieldKey.UPTIME_SECONDS to R.string.field_uptime,
        FieldKey.HUMIDITY_PERCENT to R.string.field_humidity,
        FieldKey.PRESSURE_HPA to R.string.field_pressure,
        FieldKey.ACCEL_X_G to R.string.field_accel_x,
        FieldKey.ACCEL_Y_G to R.string.field_accel_y,
        FieldKey.ACCEL_Z_G to R.string.field_accel_z,
        FieldKey.TX_POWER_DBM to R.string.field_tx_power_reported,
        FieldKey.MOVEMENT_COUNTER to R.string.field_movement_counter,
        FieldKey.SEQUENCE_NUMBER to R.string.field_sequence_number,
        FieldKey.DEVICE_MAC to R.string.field_device_mac,
        FieldKey.BATTERY_PERCENT to R.string.field_battery_percent,
        FieldKey.ILLUMINANCE_LUX to R.string.field_illuminance,
        FieldKey.VOLTAGE_V to R.string.field_voltage,
        FieldKey.PM25 to R.string.field_pm25,
        FieldKey.PM10 to R.string.field_pm10,
        FieldKey.CO2_PPM to R.string.field_co2,
        FieldKey.TVOC to R.string.field_tvoc,
        FieldKey.PACKET_ID to R.string.field_packet_id,
    )

internal fun fieldLabel(key: FieldKey): Int = FIELD_LABELS.getValue(key)
