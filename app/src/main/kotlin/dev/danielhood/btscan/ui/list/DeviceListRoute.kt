package dev.danielhood.btscan.ui.list

import android.os.Build
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.danielhood.btscan.ui.common.SystemIntents

/** Stateful wrapper: connects [DeviceListContent] to the ViewModel, the permission prompt and system settings. */
@Composable
fun DeviceListRoute(
    viewModel: DeviceListViewModel,
    debugLabel: String?,
    onDeviceClick: (String) -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = LocalActivity.current

    val permissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
            val granted = results.values.all { it }
            // After a denial, shouldShowRequestPermissionRationale == false means the OS will not prompt again.
            val canAskAgain =
                granted ||
                    results.filterValues { !it }.keys.any { activity?.shouldShowRequestPermissionRationale(it) == true }
            viewModel.onPermissionResult(granted, canAskAgain)
        }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refreshEnvironment() }
    // Stop scanning when the *app* leaves the foreground. The navigation entry's own lifecycle also reaches STOP
    // when the detail screen opens, which must not stop the scan, so observe the activity instead.
    val activityOwner = activity as? LifecycleOwner ?: LocalLifecycleOwner.current
    LifecycleEventEffect(Lifecycle.Event.ON_STOP, lifecycleOwner = activityOwner) { viewModel.onBackgrounded() }

    val actions =
        remember(viewModel, onDeviceClick) {
            DeviceListActions(
                onToggleScan = viewModel::toggleScan,
                onClear = viewModel::clear,
                onHideUnnamedChange = viewModel::setHideUnnamed,
                onDeviceClick = onDeviceClick,
                onGrantPermission = { permissionLauncher.launch(viewModel.permissionsToRequest.toTypedArray()) },
                onOpenAppSettings = { SystemIntents.openAppSettings(context) },
                onOpenBluetoothSettings = { SystemIntents.openBluetoothSettings(context) },
                onOpenLocationSettings = { SystemIntents.openLocationSettings(context) },
            )
        }

    DeviceListContent(
        state = state,
        actions = actions,
        useLocationPermissionText = Build.VERSION.SDK_INT < Build.VERSION_CODES.S,
        debugLabel = debugLabel,
    )
}
