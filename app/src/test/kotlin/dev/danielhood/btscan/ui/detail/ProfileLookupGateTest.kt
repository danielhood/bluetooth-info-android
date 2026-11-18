package dev.danielhood.btscan.ui.detail

import dev.danielhood.btscan.domain.model.ClassicSighting
import dev.danielhood.btscan.domain.model.PermissionState
import dev.danielhood.btscan.domain.parser.AdvertisementParser
import dev.danielhood.btscan.domain.repo.DefaultDeviceRepository
import dev.danielhood.btscan.domain.repo.DeviceRepository
import dev.danielhood.btscan.domain.system.BondState
import dev.danielhood.btscan.domain.system.DeviceType
import dev.danielhood.btscan.domain.system.ProfileLookupResult
import dev.danielhood.btscan.domain.system.SystemDeviceInfo
import dev.danielhood.btscan.domain.system.SystemInfoResult
import dev.danielhood.btscan.testing.FakePermissionChecker
import dev.danielhood.btscan.testing.FakeSystemInfoSource
import dev.danielhood.btscan.testing.ManualClassicScanner
import dev.danielhood.btscan.testing.ManualProfileLookup
import dev.danielhood.btscan.testing.ManualScanner
import dev.danielhood.btscan.testing.advance
import dev.danielhood.btscan.testing.rawAd
import dev.danielhood.btscan.testing.testClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.util.UUID

/**
 * The UI gate for the classic service lookup. Unlike every other read in the app it CONTACTS the device, so it only
 * ever happens after the user tapped the button for that device and confirmed a dialog naming it, every time. The
 * port's call log (`queried`) is the evidence: it must stay empty on every other path.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ProfileLookupGateTest {
    @Before
    fun setUp() = Dispatchers.setMain(StandardTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private val a2dp: UUID = AdvertisementParser.uuidFromShort(0x110B)
    private val hfp: UUID = AdvertisementParser.uuidFromShort(0x111E)

    private class Rig(
        val vm: DeviceDetailViewModel,
        val lookup: ManualProfileLookup,
        val permissions: FakePermissionChecker,
        val address: String,
    ) {
        val state get() = vm.uiState.value as DeviceDetailUiState.Loaded
    }

    private fun info(type: DeviceType?) =
        SystemDeviceInfo(
            name = "Buds",
            alias = null,
            bondState = BondState.NONE,
            deviceType = type,
            deviceClass = null,
        )

    /** A BLE-advertising device (source BLE) unless [classicOnly]; the system says its type is [type]. */
    private fun TestScope.rig(
        type: DeviceType? = DeviceType.DUAL,
        classicOnly: Boolean = false,
        permission: PermissionState = PermissionState.Granted,
        lookup: ManualProfileLookup = ManualProfileLookup(ProfileLookupResult.Profiles(listOf(a2dp, hfp))),
        source: FakeSystemInfoSource = FakeSystemInfoSource(SystemInfoResult.Available(info(type))),
    ): Rig {
        val clock = testClock()
        val ble = ManualScanner()
        val classic = ManualClassicScanner()
        val repo: DeviceRepository = DefaultDeviceRepository(ble, classic, clock, backgroundScope)
        val address = "C0:FF:EE:00:00:01"
        if (classicOnly) {
            repo.startClassicDiscovery()
            advance(1)
            backgroundScope.launch {
                classic.found(ClassicSighting(address, "Car", -60, null, BondState.NONE, Instant.EPOCH))
            }
        } else {
            repo.startScan()
            advance(1)
            backgroundScope.launch { ble.ads.emit(rawAd(address, name = "Buds")) }
        }
        advance(600)
        val permissions = FakePermissionChecker(connect = permission)
        val vm = DeviceDetailViewModel(address, repo, clock, source, permissions, lookup)
        backgroundScope.launch { vm.uiState.collect {} }
        advance(0)
        return Rig(vm, lookup, permissions, address)
    }

    @Test
    fun `tapping the button shows the confirmation and queries nobody`() =
        runTest {
            val r = rig()
            assertTrue(r.state.canLookUpProfiles)
            r.vm.onLookUpProfilesClicked()
            advance(0)
            assertEquals(ProfileLookupState.Confirming, r.state.profiles)
            assertTrue("nothing has contacted the device", r.lookup.queried.isEmpty())
        }

    @Test
    fun `cancelling queries nobody and the next tap shows the confirmation again`() =
        runTest {
            val r = rig()
            r.vm.onLookUpProfilesClicked()
            r.vm.dismissProfileLookup()
            advance(0)
            assertEquals(ProfileLookupState.Idle, r.state.profiles)
            r.vm.onLookUpProfilesClicked()
            advance(0)
            assertEquals(ProfileLookupState.Confirming, r.state.profiles)
            assertTrue(r.lookup.queried.isEmpty())
        }

    @Test
    fun `confirming queries exactly this device once and shows the profiles`() =
        runTest {
            val r = rig()
            r.vm.onLookUpProfilesClicked()
            r.vm.confirmProfileLookup()
            advance(0)
            assertEquals(listOf(r.address), r.lookup.queried)
            assertEquals(ProfileLookupState.Done(listOf(a2dp, hfp)), r.state.profiles)
        }

    @Test
    fun `confirm without a pending dialog does nothing`() =
        runTest {
            val r = rig()
            r.vm.confirmProfileLookup() // no tap, no dialog
            advance(100)
            assertEquals(ProfileLookupState.Idle, r.state.profiles)
            assertTrue("a stray confirm cannot contact a device", r.lookup.queried.isEmpty())
        }

    @Test
    fun `no other path queries a device`() =
        runTest {
            val r = rig(permission = PermissionState.Denied(canAskAgain = true))
            r.permissions.connect = PermissionState.Granted
            r.vm.onSystemInfoPermissionResult(granted = true, canAskAgain = true) // granting the permission
            r.vm.refreshSystemInfoPermission() // resume
            advance(100)
            r.vm.dismissProfileLookup()
            r.vm.cancelProfileLookup()
            advance(100)
            assertTrue("system information loads without querying the device", r.lookup.queried.isEmpty())
            assertTrue(r.state.systemInfo is SystemInfoState.Loaded)
        }

    @Test
    fun `every lookup needs its own confirmation`() =
        runTest {
            val r = rig()
            r.vm.onLookUpProfilesClicked()
            r.vm.confirmProfileLookup()
            advance(0)
            assertEquals(1, r.lookup.queried.size)
            r.vm.onLookUpProfilesClicked()
            advance(0)
            assertEquals("queried again before anything is sent", ProfileLookupState.Confirming, r.state.profiles)
            assertEquals(1, r.lookup.queried.size)
            r.vm.confirmProfileLookup()
            advance(0)
            assertEquals(2, r.lookup.queried.size)
        }

    @Test
    fun `a ble only device is not offered the lookup`() =
        runTest {
            val r = rig(type = DeviceType.LE)
            assertFalse(r.state.canLookUpProfiles)
            r.vm.onLookUpProfilesClicked()
            advance(0)
            assertEquals(ProfileLookupState.Idle, r.state.profiles)
            assertTrue(r.lookup.queried.isEmpty())
        }

    @Test
    fun `dual mode and classic devices are offered it`() =
        runTest {
            assertTrue(rig(type = DeviceType.DUAL).state.canLookUpProfiles)
            assertTrue(rig(type = DeviceType.CLASSIC).state.canLookUpProfiles)
            assertTrue("found by a classic inquiry", rig(type = null, classicOnly = true).state.canLookUpProfiles)
        }

    @Test
    fun `it is not offered while system information is not readable`() =
        runTest {
            val r = rig(permission = PermissionState.Denied(canAskAgain = true))
            assertFalse(r.state.canLookUpProfiles)
            r.vm.onLookUpProfilesClicked()
            advance(0)
            assertEquals(ProfileLookupState.Idle, r.state.profiles)
        }

    @Test
    fun `a device that never answers is given up on after ten seconds`() =
        runTest {
            val r = rig(lookup = ManualProfileLookup(silent = true))
            r.vm.onLookUpProfilesClicked()
            r.vm.confirmProfileLookup()
            advance(9_900)
            assertEquals(ProfileLookupState.Running, r.state.profiles)
            advance(200)
            assertEquals(ProfileLookupState.TimedOut, r.state.profiles)
            assertEquals(1, r.lookup.queried.size)
        }

    @Test
    fun `taps while a lookup runs are ignored`() =
        runTest {
            val r = rig(lookup = ManualProfileLookup(delayMs = 2_000))
            r.vm.onLookUpProfilesClicked()
            r.vm.confirmProfileLookup()
            advance(100)
            r.vm.onLookUpProfilesClicked()
            r.vm.confirmProfileLookup()
            advance(100)
            assertEquals(ProfileLookupState.Running, r.state.profiles)
            advance(2_000)
            assertEquals(1, r.lookup.queried.size)
            assertTrue(r.state.profiles is ProfileLookupState.Done)
        }

    @Test
    fun `leaving the screen cancels a running lookup and closes an open dialog`() =
        runTest {
            val r = rig(lookup = ManualProfileLookup(delayMs = 5_000))
            r.vm.onLookUpProfilesClicked()
            r.vm.confirmProfileLookup()
            advance(100)
            r.vm.cancelProfileLookup()
            advance(6_000)
            assertEquals("the late answer is not shown", ProfileLookupState.Idle, r.state.profiles)
            r.vm.onLookUpProfilesClicked()
            advance(0)
            assertEquals(ProfileLookupState.Confirming, r.state.profiles)
            r.vm.cancelProfileLookup()
            advance(0)
            assertEquals(ProfileLookupState.Idle, r.state.profiles)
        }

    @Test
    fun `results the platform can give are shown for what they are`() =
        runTest {
            val none = rig(lookup = ManualProfileLookup(ProfileLookupResult.Profiles(emptyList())))
            none.vm.onLookUpProfilesClicked()
            none.vm.confirmProfileLookup()
            advance(0)
            assertEquals(ProfileLookupState.Done(emptyList()), none.state.profiles)
            val refused = rig(lookup = ManualProfileLookup(ProfileLookupResult.NotStarted))
            refused.vm.onLookUpProfilesClicked()
            refused.vm.confirmProfileLookup()
            advance(0)
            assertEquals(ProfileLookupState.NotStarted, refused.state.profiles)
        }

    @Test
    fun `losing the permission mid way goes back to asking for it`() =
        runTest {
            val r = rig(lookup = ManualProfileLookup(ProfileLookupResult.PermissionMissing))
            r.vm.onLookUpProfilesClicked()
            r.vm.confirmProfileLookup()
            advance(0)
            assertEquals(SystemInfoState.NeedsPermission(permanentlyDenied = false), r.state.systemInfo)
            assertEquals(ProfileLookupState.Idle, r.state.profiles)
        }

    @Test
    fun `after an answer the system information is read again so the cached services and pairing are current`() =
        runTest {
            val source = FakeSystemInfoSource(SystemInfoResult.Available(info(DeviceType.DUAL)))
            val r = rig(source = source)
            assertEquals(1, source.lookups.size)
            source.result =
                SystemInfoResult.Available(
                    info(DeviceType.DUAL).copy(cachedUuids = listOf(a2dp), bondState = BondState.BONDING),
                )
            r.vm.onLookUpProfilesClicked()
            r.vm.confirmProfileLookup()
            advance(0)
            advance(0) // the quiet refresh is launched by the lookup's own coroutine
            assertEquals("a second read of the cache, not a second question to the device", 2, source.lookups.size)
            assertEquals(1, r.lookup.queried.size)
            val loaded = r.state.systemInfo as SystemInfoState.Loaded
            assertEquals(listOf(a2dp), loaded.info.cachedUuids)
            assertEquals(BondState.BONDING, loaded.info.bondState)
            assertTrue("the answer stays on screen", r.state.profiles is ProfileLookupState.Done)
        }
}
