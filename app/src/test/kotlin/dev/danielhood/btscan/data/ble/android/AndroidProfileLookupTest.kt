package dev.danielhood.btscan.data.ble.android

import android.Manifest
import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.os.Looper
import android.os.ParcelUuid
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.danielhood.btscan.domain.parser.AdvertisementParser
import dev.danielhood.btscan.domain.system.ProfileLookupResult
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

@RunWith(AndroidJUnit4::class)
class AndroidProfileLookupTest {
    private val app: Application = ApplicationProvider.getApplicationContext()
    private val adapter: BluetoothAdapter = app.getSystemService(BluetoothManager::class.java).adapter
    private val address = "C0:FF:EE:00:00:0A"

    private fun lookup() = AndroidProfileLookup(app, AndroidPermissionChecker(app))

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    /** Robolectric's device refuses the query unless told otherwise; a real phone accepts it for a known device. */
    private fun platformAccepts() = shadowOf(adapter.getRemoteDevice(address)).setFetchUuidsWithSdpResult(true)

    private fun registeredUuidReceivers() =
        shadowOf(app).registeredReceivers.count { it.intentFilter.hasAction(BluetoothDevice.ACTION_UUID) }

    @Test
    fun `without the connect permission nothing is queried`() =
        runBlocking {
            assertEquals(ProfileLookupResult.PermissionMissing, lookup().fetchProfiles(address))
            assertEquals("no receiver was left registered", 0, registeredUuidReceivers())
        }

    @Test
    fun `a malformed address is not started`() =
        runBlocking {
            shadowOf(app).grantPermissions(Manifest.permission.BLUETOOTH_CONNECT)
            assertEquals(ProfileLookupResult.NotStarted, lookup().fetchProfiles("nope"))
        }

    @Test
    fun `the device's answer comes back as its profile uuids, and the receiver is released`() =
        runBlocking {
            shadowOf(app).grantPermissions(Manifest.permission.BLUETOOTH_CONNECT)
            platformAccepts()
            var result: ProfileLookupResult? = null
            val job =
                launch(Dispatchers.Unconfined, start = CoroutineStart.UNDISPATCHED) {
                    result = lookup().fetchProfiles(address)
                }
            idle()
            assertEquals("a receiver waits for the answer", 1, registeredUuidReceivers())
            val device = adapter.getRemoteDevice(address)
            val uuids = arrayOf(0x110B, 0x111E).map { ParcelUuid(AdvertisementParser.uuidFromShort(it.toLong())) }
            app.sendBroadcast(
                Intent(BluetoothDevice.ACTION_UUID)
                    .putExtra(BluetoothDevice.EXTRA_DEVICE, device)
                    .putExtra(BluetoothDevice.EXTRA_UUID, uuids.toTypedArray()),
            )
            idle()
            job.join()
            assertEquals(
                ProfileLookupResult.Profiles(uuids.map { it.uuid }),
                result,
            )
            assertEquals(0, registeredUuidReceivers())
        }

    @Test
    fun `an answer for another device is ignored`() =
        runBlocking {
            shadowOf(app).grantPermissions(Manifest.permission.BLUETOOTH_CONNECT)
            platformAccepts()
            var result: ProfileLookupResult? = null
            val job =
                launch(Dispatchers.Unconfined, start = CoroutineStart.UNDISPATCHED) {
                    result = lookup().fetchProfiles(address)
                }
            idle()
            app.sendBroadcast(
                Intent(BluetoothDevice.ACTION_UUID)
                    .putExtra(BluetoothDevice.EXTRA_DEVICE, adapter.getRemoteDevice("C0:FF:EE:00:00:0B")),
            )
            idle()
            assertTrue("still waiting for the right device", result == null)
            job.cancel()
            job.join()
            assertEquals("cancelling releases the receiver", 0, registeredUuidReceivers())
        }

    @Test
    fun `an answer with no profiles is an answer, not a timeout`() =
        runBlocking {
            shadowOf(app).grantPermissions(Manifest.permission.BLUETOOTH_CONNECT)
            platformAccepts()
            var result: ProfileLookupResult? = null
            val job =
                launch(Dispatchers.Unconfined, start = CoroutineStart.UNDISPATCHED) {
                    result = lookup().fetchProfiles(address)
                }
            idle()
            app.sendBroadcast(
                Intent(
                    BluetoothDevice.ACTION_UUID,
                ).putExtra(BluetoothDevice.EXTRA_DEVICE, adapter.getRemoteDevice(address)),
            )
            idle()
            job.join()
            assertEquals(ProfileLookupResult.Profiles(emptyList()), result)
        }

    @Test
    fun `a platform that will not start the query is reported, not waited on`() =
        runBlocking {
            shadowOf(app).grantPermissions(Manifest.permission.BLUETOOTH_CONNECT)
            assertEquals(ProfileLookupResult.NotStarted, lookup().fetchProfiles(address))
            assertEquals(0, registeredUuidReceivers())
        }
}
