package dev.danielhood.btscan.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.danielhood.btscan.domain.model.BleDevice
import dev.danielhood.btscan.domain.model.PermissionState
import dev.danielhood.btscan.domain.port.Clock
import dev.danielhood.btscan.domain.port.PermissionChecker
import dev.danielhood.btscan.domain.port.SystemDeviceInfoSource
import dev.danielhood.btscan.domain.repo.DeviceRepository
import dev.danielhood.btscan.domain.system.SystemInfoResult
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
    private val systemInfoSource: SystemDeviceInfoSource,
    private val permissions: PermissionChecker,
) : ViewModel() {
    /** Last device data seen for [address]; kept after the repository drops the device. */
    private val lastKnown = MutableStateFlow(repository.devices.value.firstOrNull { it.address == address })

    /** Whether the repository currently has the device. */
    private val present = MutableStateFlow(lastKnown.value != null)

    /** What the System information section shows; the permission is asked for only when the user taps the button. */
    private val systemInfo =
        MutableStateFlow<SystemInfoState>(
            if (permissions.connectState() is PermissionState.Granted) {
                SystemInfoState.Loading
            } else {
                SystemInfoState.NeedsPermission(permanentlyDenied = false)
            },
        )

    /** Runtime permissions the UI asks for when the user taps "Allow system information". */
    val systemInfoPermissions: List<String> get() = permissions.connectPermissions

    init {
        if (systemInfo.value == SystemInfoState.Loading) loadSystemInfo()
        viewModelScope.launch {
            repository.device(address).collect { device ->
                if (device != null) lastKnown.value = device
                present.value = device != null
            }
        }
    }

    /** Result of the system permission request for [systemInfoPermissions]. */
    fun onSystemInfoPermissionResult(granted: Boolean, canAskAgain: Boolean) {
        if (granted) {
            loadSystemInfo()
        } else {
            systemInfo.value = SystemInfoState.NeedsPermission(permanentlyDenied = !canAskAgain)
        }
    }

    /** Called on resume: the user may have granted the permission in the app's settings. */
    fun refreshSystemInfoPermission() {
        if (systemInfo.value is SystemInfoState.NeedsPermission &&
            permissions.connectState() is PermissionState.Granted
        ) {
            loadSystemInfo()
        }
    }

    private fun loadSystemInfo() {
        systemInfo.value = SystemInfoState.Loading
        viewModelScope.launch {
            systemInfo.value =
                when (val result = systemInfoSource.lookup(address)) {
                    is SystemInfoResult.Available -> SystemInfoState.Loaded(result.info)
                    SystemInfoResult.PermissionMissing -> SystemInfoState.NeedsPermission(permanentlyDenied = false)
                    SystemInfoResult.Unavailable -> SystemInfoState.Unavailable
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
        combine(lastKnown, present, ticker(), systemInfo) { known, present, _, info ->
            build(known, present, info)
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            build(lastKnown.value, present.value, systemInfo.value),
        )

    private fun build(known: BleDevice?, present: Boolean, info: SystemInfoState): DeviceDetailUiState {
        if (known == null) return DeviceDetailUiState.NotFound
        val now = clock.now()
        return DeviceDetailUiState.Loaded(
            device = known.toDetailUi(),
            outOfRange = !present,
            firstSeenAgoSeconds = secondsBetween(known.firstSeen, now),
            lastSeenAgoSeconds = secondsBetween(known.lastSeen, now),
            systemInfo = info,
        )
    }
}
