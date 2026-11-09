package dev.danielhood.btscan.ui.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.danielhood.btscan.domain.model.AdapterState
import dev.danielhood.btscan.domain.model.BleDevice
import dev.danielhood.btscan.domain.model.PermissionState
import dev.danielhood.btscan.domain.model.ScanFailure
import dev.danielhood.btscan.domain.port.AdapterStateMonitor
import dev.danielhood.btscan.domain.port.Clock
import dev.danielhood.btscan.domain.port.PermissionChecker
import dev.danielhood.btscan.domain.repo.DeviceRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.Duration
import java.time.Instant

private const val TOGGLE_DEBOUNCE_MS = 1000L

class DeviceListViewModel(
    private val repository: DeviceRepository,
    adapterMonitor: AdapterStateMonitor,
    private val permissions: PermissionChecker,
    private val clock: Clock,
) : ViewModel() {
    private data class Local(
        val permission: PermissionState,
        val permanentlyDenied: Boolean = false,
        val hideUnnamed: Boolean = false,
        val hasScanned: Boolean = false,
    )

    private val local = MutableStateFlow(Local(permissions.current()))
    private var lastToggle: Instant? = null

    /** Runtime permissions the UI should request when the user chooses "Grant permission". */
    val permissionsToRequest: List<String> get() = permissions.requiredPermissions

    val uiState: StateFlow<DeviceListUiState> =
        combine(
            repository.devices,
            repository.scanning,
            repository.failure,
            adapterMonitor.state,
            local,
        ) { devices, scanning, failure, adapter, local -> build(devices, scanning, failure, adapter, local) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.Eagerly,
                initialValue =
                    build(
                        repository.devices.value,
                        repository.scanning.value,
                        repository.failure.value,
                        AdapterState.ON,
                        local.value,
                    ),
            )

    private fun build(
        devices: List<BleDevice>,
        scanning: Boolean,
        failure: ScanFailure?,
        adapter: AdapterState,
        local: Local,
    ): DeviceListUiState {
        val rows = devices.filter { !local.hideUnnamed || it.name != null }.map { it.toRowUi() }
        return DeviceListUiState(
            status = deriveStatus(adapter, local.permission, local.permanentlyDenied, scanning, failure),
            devices = rows,
            hideUnnamed = local.hideUnnamed,
            hasScanned = local.hasScanned,
        )
    }

    /** Start (or retry) when ready, Stop while scanning. Rapid repeats are ignored (platform start throttling). */
    fun toggleScan() {
        val state = uiState.value
        if (!state.canToggleScan || isDebounced()) return
        if (state.isScanning) {
            repository.stopScan()
        } else {
            repository.startScan()
            local.update { it.copy(hasScanned = true) }
        }
    }

    fun clear() {
        repository.clear()
        local.update { it.copy(hasScanned = false) }
    }

    fun setHideUnnamed(hide: Boolean) = local.update { it.copy(hideUnnamed = hide) }

    /** Result of the system permission request. [canAskAgain] false means the OS will not prompt again. */
    fun onPermissionResult(granted: Boolean, canAskAgain: Boolean) {
        local.update { it.copy(permission = permissions.current(), permanentlyDenied = !granted && !canAskAgain) }
    }

    /** Called on resume: the user may have changed settings elsewhere. Never starts a scan by itself. */
    fun refreshEnvironment() {
        local.update {
            val permission = permissions.current()
            it.copy(
                permission = permission,
                permanentlyDenied = it.permanentlyDenied && permission is PermissionState.Denied,
            )
        }
    }

    /** Called when the app leaves the foreground: scanning stops, found devices are kept. */
    fun onBackgrounded() {
        if (repository.scanning.value) repository.stopScan()
    }

    private fun isDebounced(): Boolean {
        val now = clock.now()
        val last = lastToggle
        if (last != null && Duration.between(last, now).toMillis() < TOGGLE_DEBOUNCE_MS) return true
        lastToggle = now
        return false
    }
}
