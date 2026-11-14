package dev.danielhood.btscan.ui.list

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import dev.danielhood.btscan.R
import dev.danielhood.btscan.domain.decode.DecoderId
import dev.danielhood.btscan.domain.model.ClassicInfo
import dev.danielhood.btscan.domain.model.DeviceSource
import dev.danielhood.btscan.domain.model.ScanFailure
import dev.danielhood.btscan.ui.common.ClassicTags
import dev.danielhood.btscan.ui.common.TestTags
import dev.danielhood.btscan.ui.detail.bondLabel
import dev.danielhood.btscan.ui.detail.classText

/** Stateless list screen: renders [state], reports user intent through [actions]. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceListContent(
    state: DeviceListUiState,
    actions: DeviceListActions,
    useLocationPermissionText: Boolean,
    debugLabel: String?,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = { TopAppBar(title = { Text(stringResource(R.string.app_name)) }) },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .testTag(TestTags.DEVICE_LIST_SCREEN)
                    .semantics { contentDescription = "Device list screen" },
        ) {
            Controls(state, actions)
            Box(Modifier.weight(1f).fillMaxWidth()) {
                Body(state, actions, useLocationPermissionText)
            }
            if (debugLabel != null) {
                Text(
                    text = debugLabel,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp).testTag(TestTags.DEBUG_LABEL),
                )
            }
        }
    }
    // The gate: nothing classic starts until the user confirms this notice (once per process).
    if (state.classicNoticePending) ClassicNoticeDialog(actions)
}

@Composable
private fun ClassicNoticeDialog(actions: DeviceListActions) {
    AlertDialog(
        onDismissRequest = actions.onDismissClassicNotice,
        title = { Text(stringResource(R.string.classic_notice_title)) },
        text = { Text(stringResource(R.string.classic_notice_text)) },
        confirmButton = {
            TextButton(
                onClick = actions.onConfirmClassicNotice,
                modifier = Modifier.testTag(TestTags.CLASSIC_NOTICE_CONFIRM),
            ) {
                Text(stringResource(R.string.classic_notice_scan))
            }
        },
        dismissButton = {
            TextButton(
                onClick = actions.onDismissClassicNotice,
                modifier = Modifier.testTag(TestTags.CLASSIC_NOTICE_CANCEL),
            ) {
                Text(stringResource(R.string.classic_notice_cancel))
            }
        },
        // A dialog is its own window, so it needs its own opt-in for test tags to reach `ui.sh dump` as resource ids.
        modifier = Modifier.semantics { testTagsAsResourceId = true }.testTag(TestTags.CLASSIC_NOTICE_DIALOG),
    )
}

@Composable
private fun Controls(state: DeviceListUiState, actions: DeviceListActions) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = actions.onToggleScan,
                enabled = state.canToggleScan,
                modifier = Modifier.testTag(TestTags.SCAN_BUTTON),
            ) {
                Text(stringResource(if (state.isScanning) R.string.stop_scan else R.string.start_scan))
            }
            TextButton(
                onClick = actions.onClear,
                enabled = state.devices.isNotEmpty() || state.hasScanned,
                modifier = Modifier.testTag(TestTags.CLEAR_BUTTON),
            ) { Text(stringResource(R.string.clear)) }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = actions.onClassicScan,
                enabled = state.canStartClassic,
                modifier = Modifier.testTag(TestTags.CLASSIC_SCAN_BUTTON),
            ) {
                Text(stringResource(if (state.classicScanning) R.string.classic_scan_stop else R.string.classic_scan))
            }
        }
        SourceFilterRow(state, actions)
        // The whole row toggles (label included), which also gives screen readers one labelled switch.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier =
                Modifier
                    .heightIn(min = 48.dp)
                    .toggleable(
                        value = state.hideUnnamed,
                        role = Role.Switch,
                        onValueChange = actions.onHideUnnamedChange,
                    ).testTag(TestTags.HIDE_UNNAMED_SWITCH),
        ) {
            Switch(checked = state.hideUnnamed, onCheckedChange = null)
            Text(stringResource(R.string.hide_unnamed), modifier = Modifier.padding(start = 12.dp))
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = pluralStringResource(R.plurals.device_count, state.devices.size, state.devices.size),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.testTag(TestTags.DEVICE_COUNT),
            )
            if (state.isScanning) ScanIndicator()
            if (state.classicScanning) ClassicIndicator()
        }
        state.notice?.let {
            Text(
                text =
                    stringResource(
                        if (it ==
                            ListNotice.BLE_STOPPED_FOR_CLASSIC
                        ) {
                            R.string.notice_ble_stopped
                        } else {
                            R.string.notice_classic_stopped
                        },
                    ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag(TestTags.LIST_NOTICE),
            )
        }
    }
    HorizontalDivider()
}

@Composable
private fun SourceFilterRow(state: DeviceListUiState, actions: DeviceListActions) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        listOf(
            Triple(SourceFilter.ALL, "all", R.string.filter_all),
            Triple(SourceFilter.BLE, "ble", R.string.filter_ble),
            Triple(SourceFilter.CLASSIC, "classic", R.string.filter_classic),
        ).forEach { (filter, tag, label) ->
            FilterChip(
                selected = state.sourceFilter == filter,
                onClick = { actions.onSourceFilterChange(filter) },
                label = { Text(stringResource(label)) },
                modifier = Modifier.testTag(ClassicTags.filter(tag)),
            )
        }
    }
}

@Composable
private fun ClassicIndicator() {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.testTag(TestTags.CLASSIC_SCAN_INDICATOR)) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(MaterialTheme.colorScheme.tertiary))
        Text(
            text = stringResource(R.string.classic_scanning),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(start = 6.dp),
        )
    }
}

/** Static (non-animated) "scanning" marker so uiautomator can always reach an idle state. */
@Composable
private fun ScanIndicator() {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.testTag(TestTags.SCAN_INDICATOR)) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
        Text(
            text = stringResource(R.string.scanning),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(start = 6.dp),
        )
    }
}

@Composable
private fun Body(state: DeviceListUiState, actions: DeviceListActions, useLocationPermissionText: Boolean) {
    when (val status = state.status) {
        is ListStatus.PermissionNeeded -> {
            StatusPanel(
                message =
                    stringResource(
                        when {
                            status.permanentlyDenied -> R.string.permission_denied_forever
                            useLocationPermissionText -> R.string.permission_needed_location
                            else -> R.string.permission_needed_nearby
                        },
                    ),
                actionLabel =
                    stringResource(
                        if (status.permanentlyDenied) R.string.open_app_settings else R.string.grant_permission,
                    ),
                onAction = if (status.permanentlyDenied) actions.onOpenAppSettings else actions.onGrantPermission,
            )
        }

        ListStatus.BluetoothOff -> {
            StatusPanel(
                message = stringResource(R.string.bluetooth_off),
                actionLabel = stringResource(R.string.turn_on_bluetooth),
                onAction = actions.onOpenBluetoothSettings,
            )
        }

        ListStatus.LocationOff -> {
            StatusPanel(
                message = stringResource(R.string.location_off),
                actionLabel = stringResource(R.string.open_location_settings),
                onAction = actions.onOpenLocationSettings,
            )
        }

        ListStatus.Unsupported -> {
            StatusPanel(message = stringResource(R.string.unsupported), actionLabel = null)
        }

        else -> {
            DeviceListArea(state, actions)
        }
    }
}

@Composable
private fun StatusPanel(message: String, actionLabel: String?, onAction: () -> Unit = {}) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(message, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.testTag(TestTags.STATUS_MESSAGE))
        if (actionLabel != null) {
            Button(onClick = onAction, modifier = Modifier.testTag(TestTags.STATUS_ACTION)) { Text(actionLabel) }
        }
    }
}

@Composable
private fun DeviceListArea(state: DeviceListUiState, actions: DeviceListActions) {
    Column(Modifier.fillMaxSize()) {
        (state.status as? ListStatus.Failed)?.let { FailureBanner(it.kind, actions.onToggleScan) }
        if (state.devices.isEmpty()) {
            val text =
                when {
                    state.isScanning -> R.string.empty_searching
                    state.hasScanned -> R.string.empty_none
                    else -> R.string.empty_initial
                }
            Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(text),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.testTag(TestTags.EMPTY_MESSAGE),
                )
            }
        } else {
            val listState = rememberTopPinnedListState(state.devices.first().address)
            LazyColumn(Modifier.fillMaxSize().testTag(TestTags.DEVICE_LIST), state = listState) {
                items(state.devices, key = { it.address }) { device ->
                    DeviceRow(device, state.showSourceBadges, onClick = { actions.onDeviceClick(device.address) })
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun FailureBanner(kind: ScanFailure.Kind, onRetry: () -> Unit) {
    val message =
        if (kind == ScanFailure.Kind.SCANNING_TOO_FREQUENTLY) {
            stringResource(R.string.scan_failed_too_frequent)
        } else {
            stringResource(R.string.scan_failed, kind.name.lowercase().replace('_', ' '))
        }
    Card(Modifier.fillMaxWidth().padding(16.dp).testTag(TestTags.FAILURE_BANNER)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(message, style = MaterialTheme.typography.bodyMedium)
            Button(onClick = onRetry, modifier = Modifier.testTag(TestTags.RETRY_BUTTON)) {
                Text(stringResource(R.string.retry))
            }
        }
    }
}

@Composable
private fun DeviceRow(device: DeviceRowUi, showBadge: Boolean, onClick: () -> Unit) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .testTag(TestTags.deviceRow(device.address))
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            if (device.name != null) {
                Text(device.name, style = MaterialTheme.typography.titleMedium)
            } else {
                Text(
                    text = stringResource(R.string.unnamed_device),
                    style = MaterialTheme.typography.titleMedium.copy(fontStyle = FontStyle.Italic),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(device.address, style = MaterialTheme.typography.bodyMedium)
            if (device.isConnectable) {
                Text(
                    stringResource(R.string.connectable),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            classicLine(device.classic)?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            if (showBadge) SourceBadge(device.address, device.source)
            if (device.kind != null) KindChip(device.address, device.kind)
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.rssi_dbm, device.rssi), style = MaterialTheme.typography.bodyMedium)
            SignalBars(device.signalLevel)
        }
    }
}

@Composable
private fun KindChip(address: String, kind: DecoderId) {
    Text(
        text = stringResource(kindChipLabel(kind)),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier =
            Modifier
                .padding(top = 4.dp)
                .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(8.dp))
                .padding(horizontal = 8.dp, vertical = 2.dp)
                .testTag(TestTags.kindChip(address)),
    )
}

/** Short chip text per decoder; hint-style kinds carry their caution in the words. */
internal fun kindChipLabel(kind: DecoderId): Int =
    when (kind) {
        DecoderId.IBEACON -> {
            R.string.chip_ibeacon
        }

        DecoderId.EDDYSTONE_UID, DecoderId.EDDYSTONE_URL, DecoderId.EDDYSTONE_TLM, DecoderId.EDDYSTONE_EID -> {
            R.string.chip_eddystone
        }

        DecoderId.ALTBEACON -> {
            R.string.chip_altbeacon
        }

        DecoderId.RUUVI_V5 -> {
            R.string.chip_ruuvi
        }

        DecoderId.BTHOME -> {
            R.string.chip_bthome
        }

        DecoderId.FAST_PAIR -> {
            R.string.chip_fast_pair
        }

        DecoderId.APPLE_CONTINUITY -> {
            R.string.chip_apple
        }

        DecoderId.TRACKER_HINT -> {
            R.string.chip_tracker_hint
        }
    }

/**
 * The list is sorted by signal, so a new strongest device is inserted at the top. A keyed lazy list keeps the row that
 * was first in view when something is inserted above it, which hid exactly that device off screen (found by the
 * real-radio spike). Whether the list was at the top is read during composition, i.e. from the last layout and so
 * before the new row is measured; if it was, show the top again. A list the user scrolled down is left alone.
 */
@Composable
private fun rememberTopPinnedListState(topKey: String): LazyListState {
    val listState = rememberLazyListState()
    val atTop by remember(listState) {
        derivedStateOf { listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0 }
    }
    // Captured now, during this composition: after the new row is laid out the list reports the shifted position.
    val wasAtTop = atTop
    LaunchedEffect(topKey) { if (wasAtTop) listState.scrollToItem(0) }
    return listState
}

@Composable
private fun classicLine(info: ClassicInfo?): String? {
    val parts =
        buildList {
            info?.bondState?.let { add(stringResource(bondLabel(it))) }
            info?.deviceClass?.let { add(classText(it).orEmpty()) }
        }
    return parts.takeIf { it.isNotEmpty() }?.joinToString(" \u00B7 ")
}

@Composable
private fun SourceBadge(address: String, source: DeviceSource) {
    Text(
        text =
            stringResource(
                when (source) {
                    DeviceSource.BLE -> R.string.badge_ble
                    DeviceSource.CLASSIC -> R.string.badge_classic
                    DeviceSource.BOTH -> R.string.badge_both
                },
            ),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.tertiary,
        modifier = Modifier.padding(top = 2.dp).testTag(ClassicTags.badge(address)),
    )
}
