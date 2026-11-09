package dev.danielhood.btscan.data.ble.android

import android.Manifest
import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.danielhood.btscan.domain.model.AdapterState
import dev.danielhood.btscan.domain.model.PermissionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
class AndroidAdapterStateMonitorTest {
    private val app: Application = ApplicationProvider.getApplicationContext()

    private fun setBle(supported: Boolean) =
        shadowOf(app.packageManager).setSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE, supported)

    private fun adapter(): BluetoothAdapter = app.getSystemService(BluetoothManager::class.java).adapter

    // enable()/disable() are deprecated for apps but are how Robolectric's adapter shadow is driven in tests.
    @Suppress("DEPRECATION")
    private fun setAdapterOn(on: Boolean) {
        if (on) adapter().enable() else adapter().disable()
    }

    private fun setLocation(on: Boolean) =
        shadowOf(app.getSystemService(LocationManager::class.java)).setLocationEnabled(on)

    private fun state() = AndroidAdapterStateMonitor(app).current()

    @Test
    fun `adapter on with ble hardware is on`() {
        setBle(true)
        setAdapterOn(true)
        assertEquals(AdapterState.ON, state())
    }

    @Test
    fun `adapter off is off`() {
        setBle(true)
        setAdapterOn(false)
        assertEquals(AdapterState.OFF, state())
    }

    @Test
    fun `missing ble hardware is unsupported even if the adapter is on`() {
        setBle(false)
        setAdapterOn(true)
        assertEquals(AdapterState.UNSUPPORTED, state())
    }

    @Test
    fun `location is not required on android 12 and above`() {
        setBle(true)
        setAdapterOn(true)
        setLocation(false)
        assertEquals(AdapterState.ON, state())
    }

    @Test
    @Config(sdk = [30])
    fun `location off blocks scanning on android 11`() {
        setBle(true)
        setAdapterOn(true)
        setLocation(false)
        assertEquals(AdapterState.LOCATION_OFF, state())
        setLocation(true)
        assertEquals(AdapterState.ON, state())
    }

    @Test
    fun `bluetooth scan permission decides the permission state on android 12 and above`() {
        val checker = AndroidPermissionChecker(app)
        assertEquals(listOf(Manifest.permission.BLUETOOTH_SCAN), checker.requiredPermissions)
        assertTrue(checker.current() is PermissionState.Denied)
        shadowOf(app).grantPermissions(Manifest.permission.BLUETOOTH_SCAN)
        assertEquals(PermissionState.Granted, checker.current())
    }

    @Test
    @Config(sdk = [30])
    fun `fine location decides the permission state on android 11`() {
        val checker = AndroidPermissionChecker(app)
        assertEquals(listOf(Manifest.permission.ACCESS_FINE_LOCATION), checker.requiredPermissions)
        assertTrue(checker.current() is PermissionState.Denied)
        shadowOf(app).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
        assertEquals(PermissionState.Granted, checker.current())
    }
}
