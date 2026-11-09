package dev.danielhood.btscan.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.danielhood.btscan.domain.model.BleDevice
import dev.danielhood.btscan.domain.port.Clock
import dev.danielhood.btscan.domain.repo.DeviceRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val TICK_MS = 1000L
private const val STOP_TIMEOUT_MS = 5000L

class DeviceDetailViewModel(
    private val address: String,
    repository: DeviceRepository,
    private val clock: Clock,
) : ViewModel() {
    /** Last device data seen for [address]; kept after the repository drops the device. */
    private val lastKnown = MutableStateFlow(repository.devices.value.firstOrNull { it.address == address })

    /** Whether the repository currently has the device. */
    private val present = MutableStateFlow(lastKnown.value != null)

    init {
        viewModelScope.launch {
            repository.device(address).collect { device ->
                if (device != null) lastKnown.value = device
                present.value = device != null
            }
        }
    }

    private fun ticker(): Flow<Unit> =
        flow {
            while (true) {
                emit(Unit)
                delay(TICK_MS)
            }
        }

    /** The once-a-second age ticker runs only while something observes this state. */
    val uiState: StateFlow<DeviceDetailUiState> =
        combine(lastKnown, present, ticker()) { known, present, _ ->
            build(known, present)
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            build(lastKnown.value, present.value),
        )

    private fun build(known: BleDevice?, present: Boolean): DeviceDetailUiState {
        if (known == null) return DeviceDetailUiState.NotFound
        val now = clock.now()
        return DeviceDetailUiState.Loaded(
            device = known.toDetailUi(),
            outOfRange = !present,
            firstSeenAgoSeconds = secondsBetween(known.firstSeen, now),
            lastSeenAgoSeconds = secondsBetween(known.lastSeen, now),
        )
    }
}
