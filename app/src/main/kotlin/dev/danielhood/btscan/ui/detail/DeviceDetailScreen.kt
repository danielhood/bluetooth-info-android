package dev.danielhood.btscan.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import dev.danielhood.btscan.R
import dev.danielhood.btscan.domain.model.DeviceSource
import dev.danielhood.btscan.domain.parser.AdFlag
import dev.danielhood.btscan.ui.common.TestTags
import dev.danielhood.btscan.ui.common.agoText
import dev.danielhood.btscan.ui.common.mergedTestTag
import dev.danielhood.btscan.ui.list.SignalBars

/** Stateless detail screen (the "secondary window"): reports intent through [onBack] and [onCopyRaw]. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceDetailContent(
    state: DeviceDetailUiState,
    onBack: () -> Unit,
    onCopyRaw: (String) -> Unit,
    modifier: Modifier = Modifier,
    onAllowSystemInfo: () -> Unit = {},
    onOpenAppSettings: () -> Unit = {},
    onLookUpProfiles: () -> Unit = {},
    onConfirmProfileLookup: () -> Unit = {},
    onDismissProfileLookup: () -> Unit = {},
) {
    val title =
        (state as? DeviceDetailUiState.Loaded)?.device?.name
            ?: stringResource(if (state is DeviceDetailUiState.Loaded) R.string.unnamed_device else R.string.not_found)
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(title, modifier = Modifier.testTag(TestTags.DETAIL_NAME)) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag(TestTags.BACK_BUTTON)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .testTag(TestTags.DETAIL_SCREEN)
                    .semantics { contentDescription = "Device detail screen" },
        ) {
            when (state) {
                DeviceDetailUiState.NotFound -> {
                    NotFound(onBack)
                }

                is DeviceDetailUiState.Loaded -> {
                    Loaded(
                        state,
                        onCopyRaw,
                        onAllowSystemInfo,
                        onOpenAppSettings,
                        ProfileLookupActions(onLookUpProfiles, onConfirmProfileLookup, onDismissProfileLookup),
                    )
                }
            }
        }
    }
}

@Composable
private fun NotFound(onBack: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.not_found),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.testTag(TestTags.NOT_FOUND_MESSAGE),
        )
        Button(onClick = onBack, modifier = Modifier.testTag(TestTags.NOT_FOUND_BACK)) {
            Text(stringResource(R.string.back_to_list))
        }
    }
}

@Composable
private fun Loaded(
    state: DeviceDetailUiState.Loaded,
    onCopyRaw: (String) -> Unit,
    onAllowSystemInfo: () -> Unit,
    onOpenAppSettings: () -> Unit,
    lookupActions: ProfileLookupActions,
) {
    val device = state.device
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        if (state.outOfRange) OutOfRangeBanner(state.lastSeenAgoSeconds)
        Section {
            Field(
                label = R.string.detail_address,
                value = device.address,
                tag = TestTags.DETAIL_ADDRESS,
                note = R.string.detail_address_note,
            )
            device.addressType?.let {
                Field(R.string.detail_address_type, stringResource(addressLabel(it)), TestTags.DETAIL_ADDRESS_TYPE)
            }
            if (device.source != DeviceSource.BLE) {
                Field(R.string.detail_found_by, stringResource(foundByLabel(device.source)), TestTags.DETAIL_FOUND_BY)
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Field(
                    label = R.string.detail_signal,
                    value = stringResource(R.string.rssi_dbm, device.rssi),
                    tag = TestTags.DETAIL_RSSI,
                    modifier = Modifier.weight(1f),
                )
                SignalBars(device.signalLevel, Modifier.padding(end = 16.dp))
            }
            Field(
                R.string.detail_connectable,
                stringResource(if (device.isConnectable) R.string.yes else R.string.no),
                TestTags.DETAIL_CONNECTABLE,
            )
            Field(
                R.string.detail_tx_power,
                device.txPowerDbm?.let { stringResource(R.string.rssi_dbm, it) }
                    ?: stringResource(R.string.not_advertised),
                TestTags.DETAIL_TX_POWER,
            )
            Field(R.string.detail_first_seen, agoText(state.firstSeenAgoSeconds), TestTags.DETAIL_FIRST_SEEN)
            Field(R.string.detail_last_seen, agoText(state.lastSeenAgoSeconds), TestTags.DETAIL_LAST_SEEN)
            Field(R.string.detail_ad_count, device.advertisementCount.toString(), TestTags.DETAIL_AD_COUNT)
        }
        DecodedSections(device.decoded)
        RadioSection(device)
        HorizontalDivider()
        SystemInfoSection(
            state.systemInfo,
            onAllowSystemInfo,
            onOpenAppSettings,
            ProfileLookupUi(state.profiles, state.canLookUpProfiles, device.name ?: device.address),
            lookupActions,
        )
        HorizontalDivider()
        ServicesSection(device)
        HorizontalDivider()
        ManufacturerSection(device)
        if (device.serviceData.isNotEmpty()) {
            HorizontalDivider()
            ServiceDataSection(device)
        }
        if (device.flagsHex != null) {
            HorizontalDivider()
            FlagsSection(device)
        }
        if (device.source != DeviceSource.CLASSIC) {
            HorizontalDivider()
            RawSection(device.rawHex, onCopyRaw)
        }
    }
}

@Composable
private fun OutOfRangeBanner(lastSeenAgoSeconds: Long) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(16.dp).mergedTestTag(TestTags.OUT_OF_RANGE_BANNER),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        Text(
            text = stringResource(R.string.out_of_range_banner, agoText(lastSeenAgoSeconds)),
            modifier = Modifier.padding(16.dp),
            color = MaterialTheme.colorScheme.onErrorContainer,
        )
    }
}

@Composable
private fun ServicesSection(device: DeviceDetailUi) =
    Section(R.string.detail_services) {
        if (device.services.isEmpty()) {
            Text(stringResource(R.string.none_advertised), style = MaterialTheme.typography.bodyMedium)
        }
        device.services.forEachIndexed { i, service ->
            Column(Modifier.mergedTestTag(TestTags.detailService(i))) {
                service.name?.let { Text(it, style = MaterialTheme.typography.titleSmall) }
                Text(service.uuid, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
            }
        }
    }

@Composable
private fun ManufacturerSection(device: DeviceDetailUi) =
    Section(R.string.detail_manufacturer) {
        if (device.manufacturers.isEmpty()) {
            Text(stringResource(R.string.none_advertised), style = MaterialTheme.typography.bodyMedium)
        }
        device.manufacturers.forEachIndexed { i, m ->
            Column(Modifier.mergedTestTag(TestTags.detailManufacturer(i))) {
                Text(
                    text = "${m.companyName ?: stringResource(R.string.company_unknown)} (${m.companyIdHex})",
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(m.payloadHex, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
            }
        }
    }

@Composable
private fun ServiceDataSection(device: DeviceDetailUi) =
    Section(R.string.detail_service_data) {
        device.serviceData.forEachIndexed { i, d ->
            Column(Modifier.mergedTestTag(TestTags.detailServiceData(i))) {
                Text(d.name ?: d.uuid, style = MaterialTheme.typography.titleSmall)
                Text(d.dataHex, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
            }
        }
    }

@Composable
private fun FlagsSection(device: DeviceDetailUi) =
    Section(R.string.detail_flags) {
        Column(Modifier.mergedTestTag(TestTags.DETAIL_FLAGS)) {
            Text(
                text = device.flagsHex.orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
            )
            device.flags.forEach {
                Text(stringResource(flagLabel(it)), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }

@Composable
private fun RawSection(rawHex: String, onCopyRaw: (String) -> Unit) =
    Section(R.string.detail_raw) {
        Text(
            text = rawHex,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.testTag(TestTags.DETAIL_RAW),
        )
        TextButton(onClick = { onCopyRaw(rawHex) }, modifier = Modifier.testTag(TestTags.COPY_RAW_BUTTON)) {
            Text(stringResource(R.string.copy))
        }
    }

private fun flagLabel(flag: AdFlag): Int =
    when (flag) {
        AdFlag.LE_LIMITED_DISCOVERABLE -> R.string.flag_le_limited_discoverable
        AdFlag.LE_GENERAL_DISCOVERABLE -> R.string.flag_le_general_discoverable
        AdFlag.BR_EDR_NOT_SUPPORTED -> R.string.flag_br_edr_not_supported
        AdFlag.SIMULTANEOUS_LE_BR_EDR_CONTROLLER -> R.string.flag_simultaneous_controller
        AdFlag.SIMULTANEOUS_LE_BR_EDR_HOST -> R.string.flag_simultaneous_host
    }

@Composable
private fun Section(title: Int? = null, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (title != null) {
            Text(
                text = stringResource(title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        content()
    }
}

@Composable
private fun Field(label: Int, value: String, tag: String, modifier: Modifier = Modifier, note: Int? = null) {
    Column(modifier) {
        Text(stringResource(label), style = MaterialTheme.typography.labelMedium)
        Text(value, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.testTag(tag))
        if (note != null) {
            Text(
                stringResource(note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

internal fun foundByLabel(source: DeviceSource): Int =
    when (source) {
        DeviceSource.BLE -> R.string.found_ble
        DeviceSource.CLASSIC -> R.string.found_classic
        DeviceSource.BOTH -> R.string.found_both
    }
