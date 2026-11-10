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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import dev.danielhood.btscan.R
import dev.danielhood.btscan.domain.model.ScanFailure
import dev.danielhood.btscan.ui.common.TestTags

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
        }
    }
    HorizontalDivider()
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
            LazyColumn(Modifier.fillMaxSize().testTag(TestTags.DEVICE_LIST)) {
                items(state.devices, key = { it.address }) { device ->
                    DeviceRow(device, onClick = { actions.onDeviceClick(device.address) })
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
private fun DeviceRow(device: DeviceRowUi, onClick: () -> Unit) {
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
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.rssi_dbm, device.rssi), style = MaterialTheme.typography.bodyMedium)
            SignalBars(device.signalLevel)
        }
    }
}
