# Architecture

## Principles

1. **Android types stay at the edge.** Only one package touches `android.bluetooth.*`. Everything else is JVM-testable Kotlin.
2. **State flows down, events flow up.** Screens render an immutable `UiState` and emit events; ViewModels own state.
3. **Time is injected.** No direct `System.currentTimeMillis()` or `delay` in logic; use a `Clock`/`TimeSource` and coroutine dispatchers passed in. Tests use virtual time.
4. **Fake and real share a path.** Both scanners emit `RawAdvertisement`; parsing, aggregation and UI are identical.
5. **Predictable recipe.** New screen = `Screen.kt` + `ViewModel.kt` + `UiState.kt` + route entry + test tags + tests.

## Layers

```
┌──────────────────────────────────────────────────────────────┐
│ ui            Compose screens, theme, nav graph, TestTags     │
│               DeviceListScreen/ViewModel, DeviceDetailScreen/…│
├──────────────────────────────────────────────────────────────┤
│ domain        Pure Kotlin: models, ports (interfaces),         │
│               AdvertisementParser, DeviceRepository            │
├──────────────────────────────────────────────────────────────┤
│ data          Port implementations                             │
│   data.ble.android   AndroidBleScanner, AdapterStateMonitor,   │
│                      PermissionChecker  (ONLY place with       │
│                      android.bluetooth.* imports)              │
│   (debug) data.ble.fake  FakeBleScanner, Scenarios             │
└──────────────────────────────────────────────────────────────┘
Dependency rule: ui -> domain <- data. ui must not import data.
                 domain imports neither android.* nor ui.
```

Wiring is done only in `AppContainer` (`app/`), the one place allowed to see all layers.

## Package layout (single module `:app`)

```
app/src/main/kotlin/dev/danielhood/btscan/
  BtScanApp.kt                 Application; creates AppContainer
  MainActivity.kt              Single activity; sets content, root semantics
  AppContainer.kt              Manual DI; reads DebugOverrides
  DebugOverrides.kt            interface (impl in debug/release source sets)
  domain/
    model/   BleDevice, RawAdvertisement, Advertisement, ManufacturerData,
             ServiceData, AdapterState, PermissionState, ScanFailure
    port/    BleScanner, AdapterStateMonitor, PermissionChecker, Clock
    parser/  AdvertisementParser, CompanyIds (tiny lookup), ServiceUuids (tiny lookup)
    repo/    DeviceRepository (+ DefaultDeviceRepository)
  data/ble/android/
             AndroidBleScanner, AndroidAdapterStateMonitor, AndroidPermissionChecker
  ui/
    nav/     Routes.kt, AppNavHost.kt
    list/    DeviceListScreen, DeviceListViewModel, DeviceListUiState
    detail/  DeviceDetailScreen, DeviceDetailViewModel, DeviceDetailUiState
    common/  TestTags.kt, formatters (Rssi, time-ago, hex), shared composables
    theme/   Theme.kt, Color.kt, Type.kt
  util/      AppLog.kt (tagged logging wrapper)
app/src/debug/kotlin/.../
  DebugOverrides.kt            reads intent extras; returns fake ports
  data/ble/fake/               FakeBleScanner, Scenarios, FakeAdapterStateMonitor
  debug/                       optional DumpStateReceiver
app/src/release/kotlin/.../
  DebugOverrides.kt            no-op (always real ports)
app/src/test/                  JVM tests, Roborazzi screenshots, architecture tests
app/src/androidTest/           instrumented tests
```

## Domain model

```kotlin
data class RawAdvertisement(
    val address: String,          // MAC; may be a rotating private address
    val rssi: Int,                // dBm
    val bytes: ByteArray,         // complete advertisement payload (+scan response)
    val isConnectable: Boolean,
    val timestamp: Instant,       // from injected Clock, not the platform
)

data class Advertisement(         // output of AdvertisementParser
    val localName: String?,
    val txPowerDbm: Int?,
    val flags: Int?,
    val serviceUuids: List<Uuid>,
    val serviceData: List<ServiceData>,
    val manufacturerData: List<ManufacturerData>,   // companyId + payload
)

data class BleDevice(             // aggregated, what the UI consumes
    val address: String,
    val name: String?,
    val rssi: Int,                // latest
    val txPowerDbm: Int?,
    val isConnectable: Boolean,
    val serviceUuids: List<Uuid>,
    val serviceData: List<ServiceData>,
    val manufacturerData: List<ManufacturerData>,
    val flags: Int?,
    val rawBytes: ByteArray,
    val firstSeen: Instant,
    val lastSeen: Instant,
    val advertisementCount: Int,
)
```

Merge rule when a device advertises repeatedly: take the latest `rssi`, `lastSeen`, `isConnectable`; keep the **last non-null** value for name/TX power/flags and the **union** of UUIDs/manufacturer/service data (scan responses and advertising packets carry different fields).

## Ports

```kotlin
interface BleScanner {
    /** Cold flow. Collecting starts the scan; cancelling stops it. Throws ScanFailure. */
    fun scan(): Flow<RawAdvertisement>
}

interface AdapterStateMonitor {
    val state: Flow<AdapterState>          // Unsupported | Off | On, plus LocationOff on API<=30
}

interface PermissionChecker {
    fun current(): PermissionState         // Granted | Denied(canAskAgain) 
}

interface Clock { fun now(): Instant }
```

Runtime permission *requests* are a UI concern (`ActivityResultContracts.RequestMultiplePermissions` in the screen); `PermissionChecker` only reports status, which the ViewModel re-reads on resume.

## DeviceRepository

```kotlin
interface DeviceRepository {
    val devices: StateFlow<List<BleDevice>>        // snapshot, sorted, throttled
    val scanning: StateFlow<Boolean>
    val failure: StateFlow<ScanFailure?>
    fun device(address: String): Flow<BleDevice?>  // for the detail screen
    fun startScan()
    fun stopScan()
    fun clear()
}
```

Behaviour (all covered by virtual-time unit tests):

- `startScan()` launches a collector of `BleScanner.scan()` in an app-scoped `CoroutineScope`; `stopScan()` cancels it. Devices are **kept** after stopping so the user can still inspect them; `clear()` and a fresh `startScan()` after clear reset them.
- Aggregates by `address` using the merge rule above.
- **Staleness:** while scanning, a device unseen for 30 s is dropped from the list (configurable constant). The detail screen sees `null` and shows its "out of range" state instead of crashing.
- **Throttling:** raw advertisements arrive many times per second. The `devices` snapshot is rebuilt at most once per 500 ms so the list does not thrash.
- **Ordering:** by RSSI descending, ties by address. The 500 ms throttle bounds reordering jitter.
- Scanner exceptions become `failure` state; the flow is not rethrown to the UI.

## ViewModels and UI state

`DeviceListViewModel` combines `DeviceRepository`, `AdapterStateMonitor` and `PermissionChecker` into one immutable state, deriving a **status** that fully determines which screen content shows:

```kotlin
data class DeviceListUiState(
    val status: ListStatus,
    val devices: List<DeviceRowUi>,
    val hideUnnamed: Boolean,
)

sealed interface ListStatus {
    data object Idle : ListStatus                         // ready, not scanning
    data object Scanning : ListStatus
    data class PermissionNeeded(val permanentlyDenied: Boolean) : ListStatus
    data object BluetoothOff : ListStatus
    data object LocationOff : ListStatus                  // API <= 30 only
    data object Unsupported : ListStatus                  // no BLE hardware
    data class Failed(val reason: ScanFailureUi) : ListStatus
}
```

Events: `StartScan`, `StopScan`, `Clear`, `PermissionResult`, `ToggleHideUnnamed`, `RefreshEnvironment` (on resume), `DeviceClicked(address)`.

`DeviceDetailViewModel(address)` maps `repository.device(address)` to `DeviceDetailUiState` = `Loaded(device, secondsSinceSeen, isStale)` or `NotFound`.

ViewModels receive a `CoroutineDispatcher`/`TimeSource` and never call `Dispatchers.*` directly.

## Data flow (scan to pixels)

```
BluetoothLeScanner callback (platform thread)
  -> AndroidBleScanner (callbackFlow)   emits RawAdvertisement
  -> DefaultDeviceRepository            parse -> merge by address -> StateFlow<List<BleDevice>> (500 ms)
  -> DeviceListViewModel                combine with adapter/permission state -> StateFlow<DeviceListUiState>
  -> DeviceListScreen                   collectAsStateWithLifecycle -> LazyColumn(key = address)
        tap row -> navigate(DeviceDetail(address))
  -> DeviceDetailViewModel              repository.device(address) -> DeviceDetailUiState
```

In debug fake mode only the first arrow changes (`FakeBleScanner` instead of `AndroidBleScanner`).

## Scan lifecycle

- Scanning is user-initiated (Start/Stop button). Scan mode `SCAN_MODE_LOW_LATENCY` (foreground only).
- The scan **continues while the detail screen is open** (so RSSI is live) because the repository is app-scoped.
- The scan **stops when the app goes to the background** (`ON_STOP`) to avoid battery drain and background-scan restrictions. On return the list shows `Idle` with the retained devices.
- Process death: state is lost by design (D12). The detail route restores to `NotFound`.

## Navigation

`AppNavHost` with two `@Serializable` destinations, `DeviceList` (start) and `DeviceDetail(address)`. System back from detail returns to the list with scan state unchanged.

## Observability hooks (agent-facing)

- **Test tags:** all in `ui/common/TestTags.kt` as constants/functions (`deviceRow(address)`, `scanButton`, etc.). The root composable sets `semantics { testTagsAsResourceId = true }` so tags appear as `resource-id` in `uiautomator dump`.
- **Logging:** `AppLog` wraps `android.util.Log` with the tag prefix `BtScan/` (for example `BtScan/Scan`, `BtScan/Repo`, `BtScan/UI`). Logs state transitions, never per-advertisement spam above `DEBUG`.
- **Optional state dump (debug only):** a broadcast receiver that writes the current `DeviceListUiState` as one JSON line to logcat tag `BtScan/State`, giving agents ground truth beyond what the UI shows.

## Build variants

Only `debug` and `release`. No flavors (D11). The fake scanner and `DumpStateReceiver` exist only in `src/debug`. `release` is minified and must still build in M6 to prove nothing references debug code.

## Architecture tests (enforced in `verify.sh`)

- `domain` has no `android.*` import.
- `ui` has no `data.*` import.
- `android.bluetooth.*` is imported only under `data.ble.android`.
- Every `*ViewModel` takes no `Context`.
- `main` source set has no reference to `data.ble.fake`.
