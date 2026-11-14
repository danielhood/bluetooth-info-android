package dev.danielhood.btscan.data.ble.fake

import dev.danielhood.btscan.domain.model.PermissionState
import dev.danielhood.btscan.domain.system.BondState
import dev.danielhood.btscan.domain.system.MajorClass
import dev.danielhood.btscan.domain.system.MinorClass
import dev.danielhood.btscan.domain.system.SystemInfoResult
import dev.danielhood.btscan.testing.FakePermissionChecker
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeSystemInfoScenarioTest {
    private fun source(permissions: FakePermissionChecker = FakePermissionChecker()) =
        FakeSystemDeviceInfoSource(permissions, Scenarios.require("system-info").systemInfo)

    private val addresses = Scenarios.require("system-info").devices.map { it.address }

    @Test
    fun `the paired headset, the sensor, the pairing keyboard and the unknown device are all different`() =
        runBlocking {
            val headset = (source().lookup(addresses[0]) as SystemInfoResult.Available).info
            assertEquals("My buds", headset.alias)
            assertEquals(BondState.BONDED, headset.bondState)
            assertEquals(MajorClass.AUDIO_VIDEO, headset.deviceClass?.major)
            assertEquals(MinorClass.HEADPHONES, headset.deviceClass?.minor)
            assertEquals(3, headset.cachedUuids.size)
            val sensor = (source().lookup(addresses[1]) as SystemInfoResult.Available).info
            assertEquals(null, sensor.deviceClass)
            assertEquals(BondState.NONE, sensor.bondState)
            val keyboard = (source().lookup(addresses[2]) as SystemInfoResult.Available).info
            assertEquals(BondState.BONDING, keyboard.bondState)
            assertEquals(MinorClass.KEYBOARD_AND_POINTING_DEVICE, keyboard.deviceClass?.minor)
            assertEquals(SystemInfoResult.Unavailable, source().lookup(addresses[3]))
        }

    @Test
    fun `the fake honours the real permission so the ask and grant flow is identical`() =
        runBlocking {
            val permissions = FakePermissionChecker(connect = PermissionState.Denied(canAskAgain = true))
            assertEquals(SystemInfoResult.PermissionMissing, source(permissions).lookup(addresses[0]))
            permissions.connect = PermissionState.Granted
            assertTrue(source(permissions).lookup(addresses[0]) is SystemInfoResult.Available)
        }
}
