package dev.danielhood.btscan

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.rule.GrantPermissionRule
import dev.danielhood.btscan.data.SystemClock
import dev.danielhood.btscan.data.ble.android.AndroidAdapterStateMonitor
import dev.danielhood.btscan.data.ble.android.AndroidBleScanner
import dev.danielhood.btscan.domain.model.AdapterState
import dev.danielhood.btscan.domain.parser.AdvertisementParser
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Real BLE radio checks. They need a physical device with Bluetooth on and some BLE device in range, so they are
 * skipped on emulators. Run on the phone with:
 * `scripts/real-device-tests.sh` (sets ANDROID_SERIAL and runs only this class).
 */
class RealScannerOnDeviceTest {
    @get:Rule
    val permission: GrantPermissionRule =
        GrantPermissionRule.grant(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Manifest.permission.BLUETOOTH_SCAN
            } else {
                Manifest.permission.ACCESS_FINE_LOCATION
            },
        )

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun requirePhysicalDeviceWithBluetoothOn() {
        val onEmulator = Build.FINGERPRINT.contains("generic") || Build.MODEL.contains("sdk", ignoreCase = true)
        assumeTrue("needs a physical device", !onEmulator)
        val adapter = context.getSystemService(BluetoothManager::class.java).adapter
        assumeTrue("Bluetooth must be on", adapter?.state == BluetoothAdapter.STATE_ON)
    }

    @Test
    fun adapterMonitorReportsOn() =
        runBlocking {
            assertEquals(AdapterState.ON, AndroidAdapterStateMonitor(context).state.first())
        }

    @Test
    fun scanReceivesAdvertisementsThatParse() =
        runBlocking {
            val ads =
                withTimeoutOrNull(SCAN_TIMEOUT_MS) {
                    AndroidBleScanner(context, SystemClock).scan().take(MIN_ADS).toList()
                }
            val seconds = SCAN_TIMEOUT_MS / 1000
            assertTrue("no BLE advertisements within ${seconds}s; is a BLE device in range?", ads != null)
            ads!!.forEach { ad ->
                assertTrue("address ${ad.address}", Regex("([0-9A-F]{2}:){5}[0-9A-F]{2}").matches(ad.address))
                assertTrue("rssi ${ad.rssi}", ad.rssi in RSSI_RANGE)
                AdvertisementParser.parse(ad.bytes) // must not throw on real-world payloads
            }
        }

    private companion object {
        const val SCAN_TIMEOUT_MS = 20_000L
        const val MIN_ADS = 5
        val RSSI_RANGE = -127..20
    }
}
