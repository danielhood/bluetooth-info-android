package dev.danielhood.btscan.ui.list

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.danielhood.btscan.R
import dev.danielhood.btscan.ui.common.TestTags

/** M1 placeholder. Replaced by the real list in M3. */
@Composable
fun DeviceListScreen(debugLabel: String?, modifier: Modifier = Modifier) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(16.dp)
                .testTag(TestTags.DEVICE_LIST_SCREEN)
                .semantics { contentDescription = "Device list screen" },
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(text = stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium)
        Button(onClick = {}, enabled = false, modifier = Modifier.testTag(TestTags.SCAN_BUTTON)) {
            Text(text = stringResource(R.string.start_scan))
        }
        if (debugLabel != null) {
            Text(text = debugLabel, modifier = Modifier.testTag(TestTags.DEBUG_LABEL))
        }
    }
}
