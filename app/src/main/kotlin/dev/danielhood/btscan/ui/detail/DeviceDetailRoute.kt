package dev.danielhood.btscan.ui.detail

import android.content.ClipData
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch

/** Stateful wrapper connecting [DeviceDetailContent] to its ViewModel and the clipboard. */
@Composable
fun DeviceDetailRoute(viewModel: DeviceDetailViewModel, onBack: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    DeviceDetailContent(
        state = state,
        onBack = onBack,
        onCopyRaw = { hex ->
            scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("advertisement", hex))) }
        },
    )
}
