package dev.danielhood.btscan.data.ble

import dev.danielhood.btscan.domain.model.AdapterState
import dev.danielhood.btscan.domain.port.AdapterStateMonitor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/** Stand-in until the real adapter monitor lands (roadmap M5): always reports the adapter as on. */
object PlaceholderAdapterStateMonitor : AdapterStateMonitor {
    override val state: Flow<AdapterState> = flowOf(AdapterState.ON)
}
