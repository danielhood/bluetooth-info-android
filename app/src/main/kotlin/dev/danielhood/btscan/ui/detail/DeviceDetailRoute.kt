package dev.danielhood.btscan.ui.detail

import android.content.ClipData
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.danielhood.btscan.ui.common.SystemIntents
import kotlinx.coroutines.launch

/** Stateful wrapper connecting [DeviceDetailContent] to its ViewModel and the clipboard. */
@Composable
fun DeviceDetailRoute(viewModel: DeviceDetailViewModel, onBack: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val activity = LocalActivity.current
    val permissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
            val granted = results.values.all { it }
            val canAskAgain =
                granted ||
                    results.filterValues { !it }.keys.any { activity?.shouldShowRequestPermissionRationale(it) == true }
            viewModel.onSystemInfoPermissionResult(granted, canAskAgain)
        }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refreshSystemInfoPermission() }
    // Leaving the screen or the app drops an open confirmation and cancels a running lookup.
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.cancelProfileLookup() }
    DeviceDetailContent(
        state = state,
        onBack = onBack,
        onAllowSystemInfo = { permissionLauncher.launch(viewModel.systemInfoPermissions.toTypedArray()) },
        onOpenAppSettings = { SystemIntents.openAppSettings(context) },
        onLookUpProfiles = viewModel::onLookUpProfilesClicked,
        onConfirmProfileLookup = viewModel::confirmProfileLookup,
        onDismissProfileLookup = viewModel::dismissProfileLookup,
        onCopyRaw = { hex ->
            scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("advertisement", hex))) }
        },
    )
}
