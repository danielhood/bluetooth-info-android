package dev.danielhood.btscan.domain.repo

import dev.danielhood.btscan.domain.model.BleDevice
import dev.danielhood.btscan.domain.model.RawAdvertisement
import dev.danielhood.btscan.domain.model.ScanFailure
import dev.danielhood.btscan.domain.parser.AdvertisementParser
import dev.danielhood.btscan.domain.port.BleScanner
import dev.danielhood.btscan.domain.port.Clock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

private const val DEFAULT_STALE_SECONDS = 30L
private const val DEFAULT_SNAPSHOT_MILLIS = 500L

data class DeviceRepositoryConfig(
    /** A device not heard from for this long is dropped while scanning. */
    val staleAfter: Duration = Duration.ofSeconds(DEFAULT_STALE_SECONDS),
    /** Minimum time between rebuilds of the [DeviceRepository.devices] snapshot. */
    val snapshotInterval: Duration = Duration.ofMillis(DEFAULT_SNAPSHOT_MILLIS),
)

/**
 * Aggregates raw advertisements by address, prunes stale devices and publishes a throttled, sorted snapshot.
 * Pure Kotlin: time comes from [clock] and the coroutine clock, so tests run on virtual time.
 */
class DefaultDeviceRepository(
    private val scanner: BleScanner,
    private val clock: Clock,
    private val scope: CoroutineScope,
    private val config: DeviceRepositoryConfig = DeviceRepositoryConfig(),
) : DeviceRepository {
    private val table = ConcurrentHashMap<String, BleDevice>()
    private val _devices = MutableStateFlow<List<BleDevice>>(emptyList())
    private val _scanning = MutableStateFlow(false)
    private val _failure = MutableStateFlow<ScanFailure?>(null)
    private var scanJob: Job? = null

    /** Identifies the current scan so a cancelled scan's cleanup cannot clobber the state of a newer one. */
    private val generation = AtomicInteger()

    override val devices: StateFlow<List<BleDevice>> = _devices.asStateFlow()
    override val scanning: StateFlow<Boolean> = _scanning.asStateFlow()
    override val failure: StateFlow<ScanFailure?> = _failure.asStateFlow()

    override fun device(address: String): Flow<BleDevice?> =
        devices.map { list -> list.firstOrNull { it.address == address } }.distinctUntilChanged()

    @Synchronized
    override fun startScan() {
        if (scanJob?.isActive == true) return
        val gen = generation.incrementAndGet()
        _failure.value = null
        _scanning.value = true
        scanJob = scope.launch { runScan(gen) }
    }

    @Synchronized
    override fun stopScan() {
        generation.incrementAndGet()
        _scanning.value = false
        scanJob?.cancel()
        scanJob = null
    }

    override fun clear() {
        table.clear()
        publish()
    }

    private suspend fun runScan(gen: Int) {
        try {
            coroutineScope {
                val ticker = launch { tick() }
                try {
                    scanner.scan().collect { ingest(it) }
                } finally {
                    ticker.cancel()
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: ScanFailure) {
            if (gen == generation.get()) _failure.value = e
        } finally {
            if (gen == generation.get()) _scanning.value = false
            publish()
        }
    }

    private suspend fun tick() {
        while (true) {
            delay(config.snapshotInterval.toMillis())
            pruneStale()
            publish()
        }
    }

    private fun ingest(raw: RawAdvertisement) {
        val adv = AdvertisementParser.parse(raw.bytes)
        table.compute(raw.address) { _, existing ->
            existing?.merge(raw, adv) ?: BleDevice(
                address = raw.address,
                name = adv.localName,
                rssi = raw.rssi,
                txPowerDbm = adv.txPowerDbm,
                isConnectable = raw.isConnectable,
                flags = adv.flags,
                serviceUuids = adv.serviceUuids,
                serviceData = adv.serviceData,
                manufacturerData = adv.manufacturerData,
                rawBytes = raw.bytes,
                firstSeen = raw.timestamp,
                lastSeen = raw.timestamp,
                advertisementCount = 1,
            )
        }
    }

    private fun pruneStale() {
        val now = clock.now()
        table.values.removeIf { Duration.between(it.lastSeen, now) >= config.staleAfter }
    }

    private fun publish() {
        _devices.value = table.values.sortedWith(compareByDescending<BleDevice> { it.rssi }.thenBy { it.address })
    }
}
