package dev.danielhood.btscan.testing

import dev.danielhood.btscan.domain.model.AdapterState
import dev.danielhood.btscan.domain.model.Bytes
import dev.danielhood.btscan.domain.model.ClassicSighting
import dev.danielhood.btscan.domain.model.PermissionState
import dev.danielhood.btscan.domain.model.RawAdvertisement
import dev.danielhood.btscan.domain.port.AdapterStateMonitor
import dev.danielhood.btscan.domain.port.BleScanner
import dev.danielhood.btscan.domain.port.ClassicScanner
import dev.danielhood.btscan.domain.port.Clock
import dev.danielhood.btscan.domain.port.PermissionChecker
import dev.danielhood.btscan.domain.port.ProfileLookup
import dev.danielhood.btscan.domain.port.SystemDeviceInfoSource
import dev.danielhood.btscan.domain.system.ProfileLookupResult
import dev.danielhood.btscan.domain.system.SystemInfoResult
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.test.TestScope
import java.time.Instant

/** Clock driven by the test scheduler's virtual time. */
fun TestScope.testClock(): Clock =
    object : Clock {
        override fun now(): Instant = Instant.EPOCH.plusMillis(testScheduler.currentTime)
    }

/** Advances virtual time and also runs tasks scheduled exactly at the new time. */
fun TestScope.advance(ms: Long) {
    testScheduler.advanceTimeBy(ms)
    testScheduler.runCurrent()
}

class FakePermissionChecker(
    var state: PermissionState = PermissionState.Granted,
    override val requiredPermissions: List<String> = listOf("android.permission.BLUETOOTH_SCAN"),
    /** State of the separate permission for reading system device info (Tier 2a). */
    var connect: PermissionState = PermissionState.Granted,
    override val connectPermissions: List<String> = listOf("android.permission.BLUETOOTH_CONNECT"),
) : PermissionChecker {
    override fun current(): PermissionState = state

    override fun connectState(): PermissionState = connect
}

/**
 * Profile lookup whose answer the test sets; records every address it was asked, which is the evidence that a
 * lookup (which contacts the device) never happens without the user's confirmation. [silent] never answers.
 */
class ManualProfileLookup(
    var result: ProfileLookupResult = ProfileLookupResult.Profiles(emptyList()),
    var silent: Boolean = false,
    var delayMs: Long = 0,
) : ProfileLookup {
    val asked = mutableListOf<String>()

    override suspend fun fetchProfiles(address: String): ProfileLookupResult {
        asked += address
        if (silent) awaitCancellation()
        if (delayMs > 0) delay(delayMs)
        return result
    }
}

/** System-info source whose answer the test sets; records each lookup. */
class FakeSystemInfoSource(
    var result: SystemInfoResult = SystemInfoResult.Unavailable,
) : SystemDeviceInfoSource {
    val lookups = mutableListOf<String>()

    override suspend fun lookup(address: String): SystemInfoResult {
        lookups += address
        return result
    }
}

/** Scanner whose advertisements are pushed by the test. */
class ManualScanner : BleScanner {
    val ads =
        MutableSharedFlow<RawAdvertisement>(
            extraBufferCapacity = 1024,
        )

    override fun scan(): Flow<RawAdvertisement> = ads
}

fun TestScope.rawAd(
    address: String,
    rssi: Int = -60,
    name: String? = null,
    connectable: Boolean = true,
): RawAdvertisement {
    val nameBytes = name?.toByteArray()?.let { byteArrayOf((it.size + 1).toByte(), 0x09) + it } ?: ByteArray(0)
    return RawAdvertisement(
        address = address,
        rssi = rssi,
        bytes = Bytes(nameBytes),
        isConnectable = connectable,
        timestamp = Instant.EPOCH.plusMillis(testScheduler.currentTime),
    )
}

/** Stand-in for the "real" adapter monitor in container tests. */
object AlwaysOnAdapterMonitor : AdapterStateMonitor {
    override val state: Flow<AdapterState> = flowOf(AdapterState.ON)
}

/** Application for whole-app JVM tests: fake scanner on the default scenario, permission granted, adapter on. */
class TestApp :
    android.app.Application(),
    dev.danielhood.btscan.ContainerHolder {
    override val container: dev.danielhood.btscan.AppContainer by lazy {
        dev.danielhood.btscan
            .AppContainer(
                permissionChecker = FakePermissionChecker(),
                real =
                    dev.danielhood.btscan.RealPorts(
                        ManualScanner(),
                        AlwaysOnAdapterMonitor,
                        FakeSystemInfoSource(),
                        NoClassicScanner,
                        ManualProfileLookup(),
                    ),
            ).also { it.debugConfig = dev.danielhood.btscan.DebugConfig(scanner = "fake", scenario = "default") }
    }
}

/** A classic scanner that finds nothing: for tests that are not about classic discovery. */
object NoClassicScanner : ClassicScanner {
    override fun discover(): Flow<ClassicSighting> = emptyFlow()
}

/**
 * Classic scanner driven by the test: [found] reports a device, [finish] ends the inquiry like Android does after
 * about 12 s. Counts how many inquiries were started and how many are active.
 */
class ManualClassicScanner : ClassicScanner {
    private sealed interface Event {
        data class Found(
            val sighting: ClassicSighting,
        ) : Event

        data object Finished : Event
    }

    private val events = MutableSharedFlow<Event>(extraBufferCapacity = 64)
    var starts = 0
        private set
    var active = 0
        private set

    suspend fun found(sighting: ClassicSighting) = events.emit(Event.Found(sighting))

    suspend fun finish() = events.emit(Event.Finished)

    override fun discover(): Flow<ClassicSighting> =
        events
            .onStart {
                starts++
                active++
            }.takeWhile { it !is Event.Finished }
            .filterIsInstance<Event.Found>()
            .map { it.sighting }
            .onCompletion { active-- }
}
