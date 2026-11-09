package dev.danielhood.btscan.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import dev.danielhood.btscan.ui.list.DeviceListScreen
import dev.danielhood.btscan.ui.theme.BtScanTheme

/** Everything the activity shows. Tests render this too, so screenshots match the real app. */
@Composable
fun AppRoot(debugLabel: String?, darkTheme: Boolean = isSystemInDarkTheme()) {
    BtScanTheme(darkTheme = darkTheme) {
        // testTagsAsResourceId makes Modifier.testTag values visible to `uiautomator dump` as resource-id.
        Surface(modifier = Modifier.fillMaxSize().semantics { testTagsAsResourceId = true }) {
            DeviceListScreen(debugLabel = debugLabel)
        }
    }
}
