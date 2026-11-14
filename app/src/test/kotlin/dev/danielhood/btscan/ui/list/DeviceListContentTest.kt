package dev.danielhood.btscan.ui.list

import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.danielhood.btscan.domain.model.ScanFailure
import dev.danielhood.btscan.testing.SampleUi
import dev.danielhood.btscan.testing.assertAllInteractiveNodesLabelled
import dev.danielhood.btscan.testing.assertTouchTargetAtLeast48dp
import dev.danielhood.btscan.ui.AppSurface
import dev.danielhood.btscan.ui.common.ClassicTags
import dev.danielhood.btscan.ui.common.TestTags
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Behaviour of the stateless list screen on the JVM (F-L1..F-L12, environment states). */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h800dp-xhdpi")
class DeviceListContentTest {
    @get:Rule
    val rule = createComposeRule()

    private val calls = mutableListOf<String>()

    private val actions =
        DeviceListActions(
            onToggleScan = { calls += "toggle" },
            onClear = { calls += "clear" },
            onClassicScan = { calls += "classic" },
            onConfirmClassicNotice = { calls += "classic-confirm" },
            onDismissClassicNotice = { calls += "classic-dismiss" },
            onSourceFilterChange = { calls += "filter=$it" },
            onHideUnnamedChange = { calls += "hide=$it" },
            onDeviceClick = { calls += "open=$it" },
            onGrantPermission = { calls += "grant" },
            onOpenAppSettings = { calls += "appSettings" },
            onOpenBluetoothSettings = { calls += "btSettings" },
            onOpenLocationSettings = { calls += "locSettings" },
        )

    private fun show(state: DeviceListUiState, locationText: Boolean = false, debugLabel: String? = null) {
        rule.setContent {
            AppSurface(darkTheme = false) { DeviceListContent(state, actions, locationText, debugLabel) }
        }
    }

    @Test
    fun `rows show name, address, rssi, and unnamed devices are labelled`() {
        show(SampleUi.state(ListStatus.Scanning, SampleUi.devices))
        rule.onNodeWithTag(TestTags.deviceRow("C0:FF:EE:00:00:01")).apply {
            assertIsDisplayed()
            assertTextContains("Thermo Sensor", substring = true)
            assertTextContains("C0:FF:EE:00:00:01", substring = true)
            assertTextContains("-48 dBm", substring = true)
            assertTextContains("Connectable", substring = true)
        }
        rule
            .onNodeWithTag(
                TestTags.deviceRow("C0:FF:EE:00:00:03"),
            ).assertTextContains("Unnamed device", substring = true)
    }

    @Test
    fun `tapping a row reports its address`() {
        show(SampleUi.state(ListStatus.Scanning, SampleUi.devices))
        rule.onNodeWithTag(TestTags.deviceRow("C0:FF:EE:00:00:02")).performClick()
        assertEquals(listOf("open=C0:FF:EE:00:00:02"), calls)
    }

    @Test
    fun `counter pluralises`() {
        show(SampleUi.state(ListStatus.Idle, SampleUi.devices.take(1)))
        rule.onNodeWithTag(TestTags.DEVICE_COUNT).assertTextEquals("1 device")
    }

    @Test
    fun `counter shows the number of devices`() {
        show(SampleUi.state(ListStatus.Scanning, SampleUi.devices))
        rule.onNodeWithTag(TestTags.DEVICE_COUNT).assertTextEquals("6 devices")
        rule.onNodeWithTag(TestTags.SCAN_INDICATOR).assertIsDisplayed()
    }

    @Test
    fun `start button starts when idle and reads stop while scanning`() {
        show(SampleUi.state(ListStatus.Idle))
        rule
            .onNodeWithTag(TestTags.SCAN_BUTTON)
            .assertTextContains("Start scan")
            .assertIsEnabled()
            .performClick()
        assertEquals(listOf("toggle"), calls)
    }

    @Test
    fun `button reads stop scan while scanning`() {
        show(SampleUi.state(ListStatus.Scanning))
        rule.onNodeWithTag(TestTags.SCAN_BUTTON).assertTextContains("Stop scan")
    }

    @Test
    fun `clear and hide unnamed report intent`() {
        show(SampleUi.state(ListStatus.Idle, SampleUi.devices))
        rule.onNodeWithTag(TestTags.CLEAR_BUTTON).performClick()
        rule.onNodeWithTag(TestTags.HIDE_UNNAMED_SWITCH).performClick()
        assertEquals(listOf("clear", "hide=true"), calls)
    }

    @Test
    fun `clear is disabled when there is nothing to clear`() {
        show(SampleUi.state(ListStatus.Idle, hasScanned = false))
        rule.onNodeWithTag(TestTags.CLEAR_BUTTON).assertIsNotEnabled()
    }

    @Test
    fun `empty state wording follows the scan history`() {
        show(SampleUi.state(ListStatus.Idle, hasScanned = false))
        rule.onNodeWithTag(TestTags.EMPTY_MESSAGE).assertTextEquals("Tap Start scan to look for nearby devices")
    }

    @Test
    fun `searching state while scanning without results`() {
        show(SampleUi.state(ListStatus.Scanning))
        rule.onNodeWithTag(TestTags.EMPTY_MESSAGE).assertTextEquals("Searching…")
    }

    @Test
    fun `no devices found after a scan with zero results`() {
        show(SampleUi.state(ListStatus.Idle, hasScanned = true))
        rule.onNodeWithTag(TestTags.EMPTY_MESSAGE).assertTextEquals("No devices found")
    }

    @Test
    fun `permission needed explains and offers to grant`() {
        show(SampleUi.state(ListStatus.PermissionNeeded(permanentlyDenied = false)))
        rule.onNodeWithTag(TestTags.STATUS_MESSAGE).assertTextContains("Nearby devices", substring = true)
        rule.onNodeWithTag(TestTags.SCAN_BUTTON).assertIsNotEnabled()
        rule.onNodeWithTag(TestTags.STATUS_ACTION).assertTextContains("Grant permission").performClick()
        assertEquals(listOf("grant"), calls)
    }

    @Test
    fun `legacy android asks for location instead`() {
        show(SampleUi.state(ListStatus.PermissionNeeded(permanentlyDenied = false)), locationText = true)
        rule.onNodeWithTag(TestTags.STATUS_MESSAGE).assertTextContains("Location", substring = true)
    }

    @Test
    fun `permanently denied permission offers app settings`() {
        show(SampleUi.state(ListStatus.PermissionNeeded(permanentlyDenied = true)))
        rule.onNodeWithTag(TestTags.STATUS_ACTION).assertTextContains("Open app settings").performClick()
        assertEquals(listOf("appSettings"), calls)
    }

    @Test
    fun `bluetooth off offers to turn it on`() {
        show(SampleUi.state(ListStatus.BluetoothOff))
        rule.onNodeWithTag(TestTags.STATUS_MESSAGE).assertTextEquals("Bluetooth is turned off")
        rule.onNodeWithTag(TestTags.STATUS_ACTION).performClick()
        assertEquals(listOf("btSettings"), calls)
    }

    @Test
    fun `location off offers location settings`() {
        show(SampleUi.state(ListStatus.LocationOff))
        rule.onNodeWithTag(TestTags.STATUS_ACTION).performClick()
        assertEquals(listOf("locSettings"), calls)
    }

    @Test
    fun `unsupported shows a message and no action and no start`() {
        show(SampleUi.state(ListStatus.Unsupported))
        rule
            .onNodeWithTag(
                TestTags.STATUS_MESSAGE,
            ).assertTextContains("does not support Bluetooth LE", substring = true)
        rule.onNodeWithTag(TestTags.STATUS_ACTION).assertDoesNotExist()
        rule.onNodeWithTag(TestTags.SCAN_BUTTON).assertIsNotEnabled()
    }

    @Test
    fun `failure shows a banner with retry while keeping the devices`() {
        show(SampleUi.failed)
        rule.onNodeWithTag(TestTags.FAILURE_BANNER).assertIsDisplayed()
        rule.onNodeWithTag(TestTags.deviceRow("C0:FF:EE:00:00:01")).assertIsDisplayed()
        rule.onNodeWithTag(TestTags.RETRY_BUTTON).performClick()
        assertEquals(listOf("toggle"), calls)
    }

    @Test
    fun `too frequent failure has its own wording`() {
        show(SampleUi.state(ListStatus.Failed(ScanFailure.Kind.SCANNING_TOO_FREQUENTLY)))
        rule.onNodeWithText("Wait a few seconds", substring = true).assertIsDisplayed()
    }

    @Test
    fun `debug label is shown when provided`() {
        show(SampleUi.state(), debugLabel = "scanner=fake scenario=default")
        rule.onNodeWithTag(TestTags.DEBUG_LABEL).assertTextEquals("scanner=fake scenario=default")
    }

    @Test
    fun `touch targets are at least 48dp (accessibility)`() {
        show(SampleUi.state(ListStatus.Scanning, SampleUi.devices))
        listOf(
            TestTags.SCAN_BUTTON,
            TestTags.CLEAR_BUTTON,
            TestTags.HIDE_UNNAMED_SWITCH,
            TestTags.deviceRow("C0:FF:EE:00:00:01"),
        ).forEach { tag ->
            rule.assertTouchTargetAtLeast48dp(tag)
        }
    }

    @Test
    fun `every interactive element is labelled in the list states`() {
        show(SampleUi.state(ListStatus.Scanning, SampleUi.devices))
        rule.assertAllInteractiveNodesLabelled()
    }

    @Test
    fun `every interactive element is labelled in the failure and permission states`() {
        show(SampleUi.failed)
        rule.assertAllInteractiveNodesLabelled()
    }

    @Test
    fun `signal bars carry a description`() {
        show(SampleUi.state(ListStatus.Scanning, SampleUi.devices.take(1)))
        rule.onNodeWithContentDescription("Signal 4 of 4", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun `rows show a kind chip only when a decoder matched`() {
        show(SampleUi.state(ListStatus.Scanning, SampleUi.devicesWithKinds))
        rule
            .onNodeWithTag(
                TestTags.kindChip("C0:FF:EE:00:00:01"),
                useUnmergedTree = true,
            ).assertTextEquals("Ruuvi sensor")
        rule.onNodeWithTag(TestTags.kindChip("C0:FF:EE:00:00:02"), useUnmergedTree = true).assertTextEquals("iBeacon")
        rule.onNodeWithTag(TestTags.kindChip("C0:FF:EE:00:00:03"), useUnmergedTree = true).assertTextEquals("Eddystone")
        rule
            .onNodeWithTag(
                TestTags.kindChip("C0:FF:EE:00:00:04"),
                useUnmergedTree = true,
            ).assertTextEquals("Tracker? (unverified)")
        rule.onNodeWithTag(TestTags.kindChip("C0:FF:EE:00:00:05"), useUnmergedTree = true).assertDoesNotExist()
    }

    private fun row(n: Int, rssi: Int) =
        DeviceRowUi(
            "C0:FF:EE:00:01:%02X".format(n),
            "Device $n",
            rssi,
            signalLevel(rssi),
            true,
        )

    private fun showUpdating(initial: List<DeviceRowUi>): androidx.compose.runtime.MutableState<DeviceListUiState> {
        val state = androidx.compose.runtime.mutableStateOf(SampleUi.state(ListStatus.Scanning, initial))
        rule.setContent { AppSurface(darkTheme = false) { DeviceListContent(state.value, actions, false, null) } }
        return state
    }

    @Test
    fun `a new strongest device is shown at the top when the list was at the top`() {
        // Regression found by the real-radio spike: a keyed lazy list keeps the old first row in view when an item is
        // inserted above it, so the strongest (often the nearest, most interesting) device was hidden off screen.
        val initial = (1..8).map { row(it, -60 - it) }
        val state = showUpdating(initial)
        val strongest = row(99, -30)
        rule.runOnIdle { state.value = SampleUi.state(ListStatus.Scanning, listOf(strongest) + initial) }
        rule.onNodeWithTag(TestTags.deviceRow(strongest.address)).assertIsDisplayed()
    }

    @Test
    fun `a new strongest device does not move a list the user scrolled down`() {
        val initial = (1..30).map { row(it, -40 - it) }
        val state = showUpdating(initial)
        rule.onNodeWithTag(TestTags.DEVICE_LIST).performScrollToIndex(20)
        rule.waitForIdle()
        val watching = initial[20].address
        rule.onNodeWithTag(TestTags.deviceRow(watching)).assertIsDisplayed()
        rule.runOnIdle { state.value = SampleUi.state(ListStatus.Scanning, listOf(row(99, -30)) + initial) }
        rule.onNodeWithTag(TestTags.deviceRow(watching)).assertIsDisplayed()
    }

    // ---- Classic scan (Tier 2b): the controls, the gate dialog, badges and the filter ----

    @Test
    fun `the classic button is there and reports the tap`() {
        show(SampleUi.state())
        rule
            .onNodeWithTag(
                TestTags.CLASSIC_SCAN_BUTTON,
            ).assertTextEquals("Scan classic devices (about 12 s)")
            .performClick()
        assertEquals(listOf("classic"), calls)
    }

    @Test
    fun `while an inquiry runs the button stops it and the indicator shows`() {
        show(SampleUi.state().copy(classicScanning = true))
        rule.onNodeWithTag(TestTags.CLASSIC_SCAN_BUTTON).assertTextEquals("Stop classic scan")
        rule.onNodeWithTag(TestTags.CLASSIC_SCAN_INDICATOR, useUnmergedTree = true).assertExists()
    }

    @Test
    fun `the classic button is disabled while scanning is blocked`() {
        show(SampleUi.state(ListStatus.PermissionNeeded(permanentlyDenied = false)))
        rule.onNodeWithTag(TestTags.CLASSIC_SCAN_BUTTON).assertIsNotEnabled()
    }

    @Test
    fun `the gate dialog explains the audio effect and only the Scan button confirms`() {
        show(SampleUi.state().copy(classicNoticePending = true))
        rule.onNodeWithTag(TestTags.CLASSIC_NOTICE_DIALOG).assertExists()
        rule
            .onNodeWithText(
                "Classic scanning looks for older Bluetooth devices for about 12 seconds. " +
                    "It can briefly disturb audio playing on your headphones.",
            ).assertExists()
        rule.onNodeWithTag(TestTags.CLASSIC_NOTICE_CANCEL).performClick()
        assertEquals(listOf("classic-dismiss"), calls)
        rule.onNodeWithTag(TestTags.CLASSIC_NOTICE_CONFIRM).performClick()
        assertEquals(listOf("classic-dismiss", "classic-confirm"), calls)
    }

    @Test
    fun `no dialog is shown unless the notice is pending`() {
        show(SampleUi.state())
        rule.onNodeWithTag(TestTags.CLASSIC_NOTICE_DIALOG).assertDoesNotExist()
    }

    @Test
    fun `rows show their transport and what a classic inquiry reported`() {
        show(SampleUi.state(devices = SampleUi.devicesMixed))
        rule
            .onNodeWithTag(
                ClassicTags.badge("C0:FF:EE:00:00:01"),
                useUnmergedTree = true,
            ).assertTextEquals("BLE + Classic")
        rule
            .onNodeWithTag(
                ClassicTags.badge("C0:FF:EE:00:00:0A"),
                useUnmergedTree = true,
            ).assertTextEquals("Classic")
        rule.onNodeWithTag(ClassicTags.badge("C0:FF:EE:00:00:02"), useUnmergedTree = true).assertTextEquals("BLE")
        rule.onNodeWithTag(TestTags.deviceRow("C0:FF:EE:00:00:0A")).assertTextContains("Paired", substring = true)
        rule
            .onNodeWithTag(
                TestTags.deviceRow("C0:FF:EE:00:00:0A"),
            ).assertTextContains("Audio or video: Car audio", substring = true)
        rule.onNodeWithTag(TestTags.deviceRow("C0:FF:EE:00:00:0B")).assertTextContains("Not paired", substring = true)
    }

    @Test
    fun `a plain ble list has no transport badges`() {
        show(SampleUi.state(ListStatus.Scanning, SampleUi.devices))
        rule.onNodeWithTag(ClassicTags.badge("C0:FF:EE:00:00:01"), useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun `the source filter chips report the choice`() {
        show(SampleUi.state(devices = SampleUi.devicesMixed))
        rule.onNodeWithTag(ClassicTags.filter("classic")).performClick()
        rule.onNodeWithTag(ClassicTags.filter("ble")).performClick()
        rule.onNodeWithTag(ClassicTags.filter("all")).performClick()
        assertEquals(listOf("filter=CLASSIC", "filter=BLE", "filter=ALL"), calls)
    }

    @Test
    fun `the note explains when one scan was stopped for the other`() {
        show(SampleUi.state().copy(classicScanning = true, notice = ListNotice.BLE_STOPPED_FOR_CLASSIC))
        rule
            .onNodeWithTag(
                TestTags.LIST_NOTICE,
            ).assertTextEquals("The BLE scan was stopped for the classic scan: the two cannot run together.")
    }
}
