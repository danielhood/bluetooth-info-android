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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.danielhood.btscan.R
import dev.danielhood.btscan.domain.estimate.DistanceBand
import dev.danielhood.btscan.domain.model.DataStatus
import dev.danielhood.btscan.domain.model.Phy
import dev.danielhood.btscan.ui.common.TestTags
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

/** Milliseconds per unit of the periodic advertising interval (Bluetooth Core: 1.25 ms). */
private const val PERIODIC_UNIT_MS = 1.25

/**
 * "Radio": what the platform reported about how the advertisement was sent, plus two labelled estimates.
 * Values the platform marks unavailable are null in the model and simply not shown. Nothing renders when there
 * is nothing to show.
 */
@Composable
internal fun RadioSection(device: DeviceDetailUi) {
    val radio = device.radio
    val locale = LocalConfiguration.current.locales[0]
    if (radio == null && device.intervalEstimateMs == null && device.distanceBand == null) return
    HorizontalDivider()
    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.detail_radio),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        radio?.primaryPhy?.let { RadioField(R.string.radio_primary_phy, stringResource(phyLabel(it)), "primary_phy") }
        radio?.secondaryPhy?.let {
            RadioField(
                R.string.radio_secondary_phy,
                stringResource(phyLabel(it)),
                "secondary_phy",
            )
        }
        radio?.let {
            RadioField(
                R.string.radio_advertising_type,
                stringResource(if (it.isLegacy) R.string.radio_legacy else R.string.radio_extended),
                "advertising_type",
            )
        }
        radio?.advertisingSid?.let { RadioField(R.string.radio_sid, it.toString(), "sid") }
        radio?.txPowerDbm?.let {
            RadioField(
                R.string.radio_tx_power,
                stringResource(R.string.rssi_dbm, it),
                "tx_power",
            )
        }
        radio?.periodicIntervalUnits?.let {
            RadioField(
                R.string.radio_periodic_interval,
                stringResource(R.string.radio_ms, periodicMs(it, locale)),
                "periodic_interval",
            )
        }
        radio?.dataStatus?.let {
            RadioField(
                R.string.radio_data_status,
                stringResource(dataStatusLabel(it)),
                "data_status",
            )
        }
        device.intervalEstimateMs?.let {
            RadioField(
                R.string.radio_interval_estimate,
                stringResource(R.string.radio_interval_value, it),
                "interval",
                note = R.string.radio_interval_note,
            )
        }
        device.distanceBand?.let {
            RadioField(
                R.string.radio_distance,
                stringResource(distanceLabel(it)),
                "distance",
                note = R.string.radio_distance_note,
            )
        }
    }
}

@Composable
private fun RadioField(label: Int, value: String, field: String, note: Int? = null) {
    Column {
        Text(stringResource(label), style = MaterialTheme.typography.labelMedium)
        Text(
            value,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.testTag(TestTags.detailRadio(field)),
        )
        if (note != null) {
            Text(
                stringResource(note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun periodicMs(units: Int, locale: Locale): String =
    DecimalFormat("0.##", DecimalFormatSymbols(locale)).format(units * PERIODIC_UNIT_MS)

internal fun phyLabel(phy: Phy): Int =
    when (phy) {
        Phy.LE_1M -> R.string.phy_le_1m
        Phy.LE_2M -> R.string.phy_le_2m
        Phy.LE_CODED -> R.string.phy_le_coded
    }

internal fun dataStatusLabel(status: DataStatus): Int =
    when (status) {
        DataStatus.COMPLETE -> R.string.data_complete
        DataStatus.TRUNCATED -> R.string.data_truncated
    }

internal fun distanceLabel(band: DistanceBand): Int =
    when (band) {
        DistanceBand.VERY_CLOSE -> R.string.distance_very_close
        DistanceBand.ABOUT_ONE_METRE -> R.string.distance_one_metre
        DistanceBand.A_FEW_METRES -> R.string.distance_few_metres
        DistanceBand.FAR -> R.string.distance_far
    }
