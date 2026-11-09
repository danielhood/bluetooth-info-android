package dev.danielhood.btscan.testing

import dev.danielhood.btscan.domain.model.Bytes
import dev.danielhood.btscan.domain.model.PermissionState
import dev.danielhood.btscan.domain.model.RawAdvertisement
import dev.danielhood.btscan.domain.port.BleScanner
import dev.danielhood.btscan.domain.port.Clock
import dev.danielhood.btscan.domain.port.PermissionChecker
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
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
) : PermissionChecker {
    override fun current(): PermissionState = state
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
