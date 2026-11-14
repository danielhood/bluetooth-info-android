package dev.danielhood.btscan.ui.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.danielhood.btscan.domain.model.AdapterState
import dev.danielhood.btscan.domain.model.BleDevice
import dev.danielhood.btscan.domain.model.DeviceSource
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
import kotlinx.coroutines.launch
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
        val sourceFilter: SourceFilter = SourceFilter.ALL,
        val classicNoticePending: Boolean = false,
        val notice: ListNotice? = null,
    )

    private val local = MutableStateFlow(Local(permissions.current()))
    private var lastToggle: Instant? = null

    /** The classic-scan notice is confirmed once per process; nothing about it is stored. */
    private var classicNoticeAccepted = false

    init {
        // When Bluetooth (or location, on old Android) goes away the platform silently ends the scan, so stop ours too;
        // otherwise the screen would claim to be scanning after Bluetooth comes back. The user starts again.
        viewModelScope.launch {
            adapterMonitor.state.collect { adapter ->
                if (adapter != AdapterState.ON) stopEverything()
            }
        }
    }

    /** Runtime permissions the UI should request when the user chooses "Grant permission". */
    val permissionsToRequest: List<String> get() = permissions.requiredPermissions

    val uiState: StateFlow<DeviceListUiState> =
        combine(
            repository.devices,
            combine(repository.scanning, repository.scanningClassic) { ble, classic -> ble to classic },
            repository.failure,
            adapterMonitor.state,
            local,
        ) { devices, scanning, failure, adapter, local ->
            build(devices, scanning.first, scanning.second, failure, adapter, local)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue =
                build(
                    repository.devices.value,
                    repository.scanning.value,
                    repository.scanningClassic.value,
                    repository.failure.value,
                    AdapterState.ON,
                    local.value,
                ),
        )

    private fun build(
        devices: List<BleDevice>,
        scanning: Boolean,
        classicScanning: Boolean,
        failure: ScanFailure?,
        adapter: AdapterState,
        local: Local,
    ): DeviceListUiState {
        val rows =
            devices
                .filter { !local.hideUnnamed || it.name != null }
                .filter { matches(local.sourceFilter, it.source) }
                .map { it.toRowUi() }
        return DeviceListUiState(
            status = deriveStatus(adapter, local.permission, local.permanentlyDenied, scanning, failure),
            devices = rows,
            hideUnnamed = local.hideUnnamed,
            hasScanned = local.hasScanned,
            classicScanning = classicScanning,
            classicNoticePending = local.classicNoticePending,
            notice = local.notice,
            sourceFilter = local.sourceFilter,
        )
    }

    /** Start (or retry) when ready, Stop while scanning. Rapid repeats are ignored (platform start throttling). */
    fun toggleScan() {
        val state = uiState.value
        if (!state.canToggleScan || isDebounced()) return
        if (state.isScanning) {
            repository.stopScan()
        } else {
            val interrupted = repository.scanningClassic.value
            repository.startScan()
            local.update {
                it.copy(hasScanned = true, notice = if (interrupted) ListNotice.CLASSIC_STOPPED_FOR_BLE else null)
            }
        }
    }

    // ---- Classic scan: gated by the UI. Only these three functions can start an inquiry, and only after the user
    // tapped the button and (once per process) confirmed the notice. Nothing starts it on launch, on a permission
    // grant, on resume, or because a BLE scan started (ArchitectureTest pins the call to this file).

    /** The classic scan button: stops a running inquiry, otherwise asks first (once per process) and then starts. */
    fun onClassicScanClicked() {
        val state = uiState.value
        if (state.classicScanning) {
            repository.stopClassicDiscovery()
            return
        }
        if (!state.canStartClassic || isDebounced()) return
        if (classicNoticeAccepted) {
            startClassic()
        } else {
            local.update { it.copy(classicNoticePending = true) }
        }
    }

    /** The user accepted the notice: remember that for this process and start the inquiry they asked for. */
    fun confirmClassicNotice() {
        classicNoticeAccepted = true
        local.update { it.copy(classicNoticePending = false) }
        if (uiState.value.canStartClassic) startClassic()
    }

    fun dismissClassicNotice() = local.update { it.copy(classicNoticePending = false) }

    private fun startClassic() {
        val interrupted = repository.scanning.value
        repository.startClassicDiscovery()
        local.update {
            it.copy(
                hasScanned = true,
                notice = if (interrupted) ListNotice.BLE_STOPPED_FOR_CLASSIC else null,
            )
        }
    }

    fun setSourceFilter(filter: SourceFilter) = local.update { it.copy(sourceFilter = filter) }

    private fun stopEverything() {
        if (repository.scanning.value) repository.stopScan()
        if (repository.scanningClassic.value) repository.stopClassicDiscovery()
    }

    fun clear() {
        repository.clear()
        local.update { it.copy(hasScanned = false, notice = null) }
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
    fun onBackgrounded() = stopEverything()

    private fun isDebounced(): Boolean {
        val now = clock.now()
        val last = lastToggle
        if (last != null && Duration.between(last, now).toMillis() < TOGGLE_DEBOUNCE_MS) return true
        lastToggle = now
        return false
    }
}

/** Whether a device found by [source] belongs in the list under [filter]; devices seen by both radios are in both. */
private fun matches(filter: SourceFilter, source: DeviceSource): Boolean =
    when (filter) {
        SourceFilter.ALL -> true
        SourceFilter.BLE -> source != DeviceSource.CLASSIC
        SourceFilter.CLASSIC -> source != DeviceSource.BLE
    }
