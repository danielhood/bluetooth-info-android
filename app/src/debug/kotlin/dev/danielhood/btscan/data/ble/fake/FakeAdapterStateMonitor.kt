package dev.danielhood.btscan.data.ble.fake

import dev.danielhood.btscan.domain.model.AdapterState
import dev.danielhood.btscan.domain.port.AdapterStateMonitor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** Adapter state fixed by a scenario; tests can change it through [set]. */
class FakeAdapterStateMonitor(
    initial: AdapterState,
) : AdapterStateMonitor {
    private val current = MutableStateFlow(initial)
    override val state: Flow<AdapterState> = current

    fun set(value: AdapterState) {
        current.value = value
    }
}
