package dev.danielhood.btscan.data.ble.android

import android.Manifest
import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.danielhood.btscan.domain.system.BondState
import dev.danielhood.btscan.domain.system.SystemInfoResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
class AndroidSystemDeviceInfoSourceTest {
    private val app: Application = ApplicationProvider.getApplicationContext()
    private val address = "C0:FF:EE:00:00:01"

    private fun lookup(a: String = address): SystemInfoResult {
        val permissions = AndroidPermissionChecker(app)
        return runBlocking { AndroidSystemDeviceInfoSource(app, permissions, Dispatchers.Unconfined).lookup(a) }
    }

    @Test
    fun `without the connect permission nothing is read`() {
        assertEquals(SystemInfoResult.PermissionMissing, lookup())
    }

    @Test
    fun `with the permission a device the system does not know has empty values, not invented ones`() {
        shadowOf(app).grantPermissions(Manifest.permission.BLUETOOTH_CONNECT)
        val info = (lookup() as SystemInfoResult.Available).info
        assertNull(info.name)
        assertEquals(BondState.NONE, info.bondState)
        assertNull(info.deviceClass)
        assertTrue(info.cachedUuids.isEmpty())
    }

    @Test
    fun `a malformed address is unavailable`() {
        shadowOf(app).grantPermissions(Manifest.permission.BLUETOOTH_CONNECT)
        assertEquals(SystemInfoResult.Unavailable, lookup("not-an-address"))
    }

    @Test
    @Config(sdk = [30])
    fun `on android 11 no new permission is needed and address type is not reported`() {
        val permissions = AndroidPermissionChecker(app)
        assertTrue(permissions.connectPermissions.isEmpty())
        val info = (lookup() as SystemInfoResult.Available).info
        assertNull("address type needs Android 15", info.addressType)
    }

    @Test
    fun `the connect permission is requested on its own and is separate from scanning`() {
        val permissions = AndroidPermissionChecker(app)
        assertEquals(listOf(Manifest.permission.BLUETOOTH_CONNECT), permissions.connectPermissions)
        assertTrue(Manifest.permission.BLUETOOTH_CONNECT !in permissions.requiredPermissions)
    }
}
