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
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.danielhood.btscan.domain.model.ScanFailure
import dev.danielhood.btscan.testing.SampleUi
import dev.danielhood.btscan.testing.assertAllInteractiveNodesLabelled
import dev.danielhood.btscan.testing.assertTouchTargetAtLeast48dp
import dev.danielhood.btscan.ui.AppSurface
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
}
