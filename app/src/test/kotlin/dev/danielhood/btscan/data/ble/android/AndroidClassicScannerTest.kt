package dev.danielhood.btscan.data.ble.android

import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.danielhood.btscan.domain.model.ClassicSighting
import dev.danielhood.btscan.domain.port.Clock
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import java.time.Instant

@RunWith(AndroidJUnit4::class)
class AndroidClassicScannerTest {
    private val app: Application = ApplicationProvider.getApplicationContext()
    private val adapter: BluetoothAdapter = app.getSystemService(BluetoothManager::class.java).adapter
    private val clock =
        object : Clock {
            override fun now(): Instant = Instant.EPOCH
        }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    private fun found(address: String, name: String?, rssi: Short?): Intent =
        Intent(BluetoothDevice.ACTION_FOUND)
            .putExtra(BluetoothDevice.EXTRA_DEVICE, adapter.getRemoteDevice(address))
            .apply {
                if (name != null) putExtra(BluetoothDevice.EXTRA_NAME, name)
                if (rssi != null) putExtra(BluetoothDevice.EXTRA_RSSI, rssi)
            }

    @Test
    fun `an inquiry reports what the platform broadcasts and ends when the platform says so`() =
        runBlocking {
            val seen = mutableListOf<ClassicSighting>()
            var completed = false
            val job =
                launch(Dispatchers.Unconfined, start = CoroutineStart.UNDISPATCHED) {
                    AndroidClassicScanner(app, clock).discover().collect { seen += it }
                    completed = true
                }
            idle()
            assertTrue("discovery was started", adapter.isDiscovering)
            app.sendBroadcast(found("C0:FF:EE:00:00:0A", "Car Audio", (-58).toShort()))
            app.sendBroadcast(found("C0:FF:EE:00:00:0B", null, null))
            idle()
            assertEquals(listOf("C0:FF:EE:00:00:0A", "C0:FF:EE:00:00:0B"), seen.map { it.address })
            assertEquals("Car Audio", seen[0].name)
            assertEquals(-58, seen[0].rssi)
            assertNull("no name reported means no name", seen[1].name)
            assertNull("no signal reading means none, not a number", seen[1].rssi)
            app.sendBroadcast(Intent(BluetoothAdapter.ACTION_DISCOVERY_FINISHED))
            idle()
            job.join()
            assertTrue("the flow completes when the inquiry finishes", completed)
            assertFalse("and the inquiry is not left running", adapter.isDiscovering)
        }

    @Test
    fun `cancelling the collector cancels the inquiry`() =
        runBlocking {
            val job: Job =
                launch(Dispatchers.Unconfined, start = CoroutineStart.UNDISPATCHED) {
                    AndroidClassicScanner(app, clock).discover().collect { }
                }
            idle()
            assertTrue(adapter.isDiscovering)
            job.cancel()
            job.join()
            idle()
            assertFalse(adapter.isDiscovering)
        }
}
