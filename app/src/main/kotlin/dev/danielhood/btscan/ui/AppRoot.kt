package dev.danielhood.btscan.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.danielhood.btscan.AppContainer
import dev.danielhood.btscan.ui.list.DeviceListRoute
import dev.danielhood.btscan.ui.list.DeviceListViewModel
import dev.danielhood.btscan.ui.theme.BtScanTheme

/** Everything the activity shows. */
@Composable
fun AppRoot(container: AppContainer, debugLabel: String?, darkTheme: Boolean = isSystemInDarkTheme()) {
    val listViewModel: DeviceListViewModel =
        viewModel(
            factory =
                viewModelFactory {
                    initializer {
                        DeviceListViewModel(
                            repository = container.deviceRepository,
                            adapterMonitor = container.adapterStateMonitor,
                            permissions = container.permissionChecker,
                            clock = container.clock,
                        )
                    }
                },
        )
    AppSurface(darkTheme) {
        DeviceListRoute(viewModel = listViewModel, debugLabel = debugLabel, onDeviceClick = {})
    }
}

/** Theme plus root surface; tests render screens inside this so screenshots match the app. */
@Composable
fun AppSurface(darkTheme: Boolean, content: @Composable () -> Unit) {
    BtScanTheme(darkTheme = darkTheme) {
        // testTagsAsResourceId makes Modifier.testTag values visible to `uiautomator dump` as resource-id.
        Surface(modifier = Modifier.fillMaxSize().semantics { testTagsAsResourceId = true }, content = content)
    }
}
