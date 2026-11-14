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
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import dev.danielhood.btscan.AppContainer
import dev.danielhood.btscan.ui.detail.DeviceDetailRoute
import dev.danielhood.btscan.ui.detail.DeviceDetailViewModel
import dev.danielhood.btscan.ui.list.DeviceListRoute
import dev.danielhood.btscan.ui.list.DeviceListViewModel
import dev.danielhood.btscan.ui.nav.DetailDestination
import dev.danielhood.btscan.ui.nav.ListDestination
import dev.danielhood.btscan.ui.theme.BtScanTheme

/** Everything the activity shows: theme, root surface and the two-screen navigation graph. */
@Composable
fun AppRoot(container: AppContainer, debugLabel: String?, darkTheme: Boolean = isSystemInDarkTheme()) {
    AppSurface(darkTheme) {
        val nav = rememberNavController()
        NavHost(navController = nav, startDestination = ListDestination) {
            composable<ListDestination> {
                val viewModel: DeviceListViewModel =
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
                DeviceListRoute(
                    viewModel = viewModel,
                    debugLabel = debugLabel,
                    onDeviceClick = { address -> nav.navigate(DetailDestination(address)) },
                )
            }
            composable<DetailDestination> { entry ->
                val address = entry.toRoute<DetailDestination>().address
                val viewModel: DeviceDetailViewModel =
                    viewModel(
                        key = "detail-$address",
                        factory =
                            viewModelFactory {
                                initializer {
                                    DeviceDetailViewModel(
                                        address,
                                        container.deviceRepository,
                                        container.clock,
                                        container.systemDeviceInfoSource,
                                        container.permissionChecker,
                                    )
                                }
                            },
                    )
                DeviceDetailRoute(viewModel = viewModel, onBack = { nav.popBackStack() })
            }
        }
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
