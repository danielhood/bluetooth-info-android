package dev.danielhood.btscan.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.danielhood.btscan.domain.model.BleDevice
import dev.danielhood.btscan.domain.model.DeviceSource
import dev.danielhood.btscan.domain.model.PermissionState
import dev.danielhood.btscan.domain.port.Clock
import dev.danielhood.btscan.domain.port.PermissionChecker
import dev.danielhood.btscan.domain.port.ProfileLookup
import dev.danielhood.btscan.domain.port.SystemDeviceInfoSource
import dev.danielhood.btscan.domain.repo.DeviceRepository
import dev.danielhood.btscan.domain.system.DeviceType
import dev.danielhood.btscan.domain.system.ProfileLookupResult
import dev.danielhood.btscan.domain.system.SystemInfoResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

private const val TICK_MS = 1000L
private const val STOP_TIMEOUT_MS = 5000L

/** A profile lookup that has not answered by now is given up on. */
private const val LOOKUP_TIMEOUT_MS = 10_000L

class DeviceDetailViewModel(
    private val address: String,
    repository: DeviceRepository,
    private val clock: Clock,
    private val systemInfoSource: SystemDeviceInfoSource,
    private val permissions: PermissionChecker,
    private val profileLookup: ProfileLookup,
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

    /** The profile lookup: it contacts the device, so it moves only on explicit user actions ([ProfileLookupState]). */
    private val profiles = MutableStateFlow<ProfileLookupState>(ProfileLookupState.Idle)
    private var lookupJob: Job? = null

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

    // ---- Profile lookup (Tier 2c): gated by the UI. fetchProfiles is called only from confirmProfileLookup, which
    // does nothing unless the user has just been shown the confirmation for this device (ArchitectureTest pins the
    // call to this file).

    /** The "Look up supported profiles" button: shows the confirmation. Nothing contacts the device yet. */
    fun onLookUpProfilesClicked() {
        val state = profiles.value
        if (state is ProfileLookupState.Running || state is ProfileLookupState.Confirming) return
        if (!canLookUp(lastKnown.value, systemInfo.value)) return
        profiles.value = ProfileLookupState.Confirming
    }

    /** The user confirmed the dialog naming this device: ask it (10 s limit). Ignored without a pending dialog. */
    fun confirmProfileLookup() {
        if (profiles.value != ProfileLookupState.Confirming) return
        profiles.value = ProfileLookupState.Running
        lookupJob =
            viewModelScope.launch {
                val result = withTimeoutOrNull(LOOKUP_TIMEOUT_MS) { profileLookup.fetchProfiles(address) }
                profiles.value =
                    when (result) {
                        null -> {
                            ProfileLookupState.TimedOut
                        }

                        is ProfileLookupResult.Profiles -> {
                            refreshSystemInfoQuietly()
                            ProfileLookupState.Done(result.uuids)
                        }

                        ProfileLookupResult.NotStarted -> {
                            ProfileLookupState.NotStarted
                        }

                        ProfileLookupResult.PermissionMissing -> {
                            systemInfo.value = SystemInfoState.NeedsPermission(permanentlyDenied = false)
                            ProfileLookupState.Idle
                        }
                    }
            }
    }

    fun dismissProfileLookup() {
        if (profiles.value == ProfileLookupState.Confirming) profiles.value = ProfileLookupState.Idle
    }

    /** Leaving the screen (or the app): drop an open dialog and cancel a running lookup. */
    fun cancelProfileLookup() {
        lookupJob?.cancel()
        val state = profiles.value
        if (state is ProfileLookupState.Running || state is ProfileLookupState.Confirming) {
            profiles.value = ProfileLookupState.Idle
        }
    }

    /**
     * After a lookup Android has cached what the device said (and may have changed the pairing state), so read the
     * system information again. Cache reads only; no "loading" state, so the result on screen does not flicker.
     */
    private fun refreshSystemInfoQuietly() {
        viewModelScope.launch {
            (systemInfoSource.lookup(address) as? SystemInfoResult.Available)?.let {
                systemInfo.value = SystemInfoState.Loaded(it.info)
            }
        }
    }

    private fun canLookUp(known: BleDevice?, info: SystemInfoState): Boolean {
        val classicCapable =
            known?.source != DeviceSource.BLE ||
                (info as? SystemInfoState.Loaded)?.info?.deviceType.let {
                    it == DeviceType.CLASSIC ||
                        it == DeviceType.DUAL
                }
        return known != null && info is SystemInfoState.Loaded && classicCapable
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
        combine(lastKnown, present, ticker(), systemInfo, profiles) { known, present, _, info, lookup ->
            build(known, present, info, lookup)
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            build(lastKnown.value, present.value, systemInfo.value, profiles.value),
        )

    private fun build(
        known: BleDevice?,
        present: Boolean,
        info: SystemInfoState,
        lookup: ProfileLookupState,
    ): DeviceDetailUiState {
        if (known == null) return DeviceDetailUiState.NotFound
        val now = clock.now()
        return DeviceDetailUiState.Loaded(
            device = known.toDetailUi(),
            outOfRange = !present,
            firstSeenAgoSeconds = secondsBetween(known.firstSeen, now),
            lastSeenAgoSeconds = secondsBetween(known.lastSeen, now),
            systemInfo = info,
            profiles = lookup,
            canLookUpProfiles = canLookUp(known, info),
        )
    }
}
