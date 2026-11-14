package dev.danielhood.btscan.ui.detail

import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.danielhood.btscan.testing.SampleDetail
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

/** Behaviour of the stateless detail screen (F-D1..F-D12). */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h800dp-xhdpi")
class DeviceDetailContentTest {
    @get:Rule
    val rule = createComposeRule()

    private val calls = mutableListOf<String>()

    private fun show(state: DeviceDetailUiState) {
        rule.setContent {
            AppSurface(darkTheme = false) {
                DeviceDetailContent(
                    state,
                    onBack = { calls += "back" },
                    onCopyRaw = { calls += "copy=$it" },
                    onAllowSystemInfo = { calls += "allow" },
                    onOpenAppSettings = { calls += "settings" },
                )
            }
        }
    }

    @Test
    fun `shows the name, address, signal, connectable, tx power and timing`() {
        show(SampleDetail.loaded())
        rule.onNodeWithTag(TestTags.DETAIL_NAME).assertTextEquals("Thermo Sensor")
        rule.onNodeWithTag(TestTags.DETAIL_ADDRESS).assertTextEquals("C0:FF:EE:00:00:01")
        rule.onNodeWithTag(TestTags.DETAIL_RSSI).assertTextEquals("-48 dBm")
        rule.onNodeWithTag(TestTags.DETAIL_CONNECTABLE).assertTextEquals("Yes")
        rule.onNodeWithTag(TestTags.DETAIL_TX_POWER).assertTextEquals("-4 dBm")
        rule.onNodeWithTag(TestTags.DETAIL_FIRST_SEEN).assertTextEquals("1 min ago")
        rule.onNodeWithTag(TestTags.DETAIL_LAST_SEEN).assertTextEquals("1 s ago")
        rule.onNodeWithTag(TestTags.DETAIL_AD_COUNT).assertTextEquals("42")
    }

    @Test
    fun `lists services with friendly names and uuids`() {
        show(SampleDetail.loaded())
        rule.onNodeWithTag(TestTags.detailService(0)).performScrollTo().apply {
            assertTextContains("Environmental Sensing", substring = true)
            assertTextContains("0000181a-0000-1000-8000-00805f9b34fb", substring = true)
        }
        rule.onNodeWithTag(TestTags.detailService(1)).assertTextContains("Battery", substring = true)
    }

    @Test
    fun `shows manufacturer company id, name and payload`() {
        show(SampleDetail.loaded())
        rule.onNodeWithTag(TestTags.detailManufacturer(0)).performScrollTo().apply {
            assertTextContains("Nordic Semiconductor ASA", substring = true)
            assertTextContains("0x0059", substring = true)
            assertTextContains("01 02 03", substring = true)
        }
    }

    @Test
    fun `shows service data, decoded flags and raw bytes`() {
        show(SampleDetail.loaded())
        rule
            .onNodeWithTag(
                TestTags.detailServiceData(0),
            ).performScrollTo()
            .assertTextContains("Battery", substring = true)
        rule.onNodeWithTag(TestTags.DETAIL_FLAGS).performScrollTo().apply {
            assertTextContains("0x06", substring = true)
            assertTextContains("LE General Discoverable Mode", substring = true)
            assertTextContains("BR/EDR not supported", substring = true)
        }
        rule.onNodeWithTag(TestTags.DETAIL_RAW).performScrollTo().assertTextContains("02 01 06", substring = true)
    }

    @Test
    fun `copy reports the raw hex`() {
        show(SampleDetail.loaded())
        rule.onNodeWithTag(TestTags.COPY_RAW_BUTTON).performScrollTo().performClick()
        assertEquals(listOf("copy=${SampleDetail.thermo.rawHex}"), calls)
    }

    @Test
    fun `sparse device shows not advertised and empty sections`() {
        show(SampleDetail.loaded(SampleDetail.sparse))
        rule.onNodeWithTag(TestTags.DETAIL_NAME).assertTextEquals("Unnamed device")
        rule.onNodeWithTag(TestTags.DETAIL_CONNECTABLE).assertTextEquals("No")
        rule.onNodeWithTag(TestTags.DETAIL_TX_POWER).assertTextEquals("Not advertised")
    }

    @Test
    fun `out of range banner keeps the last known data visible`() {
        show(SampleDetail.loaded(outOfRange = true))
        rule
            .onNodeWithTag(
                TestTags.OUT_OF_RANGE_BANNER,
            ).assertIsDisplayed()
            .assertTextContains("41 s ago", substring = true)
        rule.onNodeWithTag(TestTags.DETAIL_ADDRESS).assertTextEquals("C0:FF:EE:00:00:01")
    }

    @Test
    fun `not found shows a message and a back action`() {
        show(DeviceDetailUiState.NotFound)
        rule.onNodeWithTag(TestTags.NOT_FOUND_MESSAGE).assertTextEquals("Device not found")
        rule.onNodeWithTag(TestTags.NOT_FOUND_BACK).performClick()
        assertEquals(listOf("back"), calls)
    }

    @Test
    fun `top bar back button goes back`() {
        show(SampleDetail.loaded())
        rule.onNodeWithTag(TestTags.BACK_BUTTON).performClick()
        assertEquals(listOf("back"), calls)
    }

    @Test
    fun `every interactive element is labelled on the detail and not found screens`() {
        show(SampleDetail.loaded())
        rule.assertAllInteractiveNodesLabelled()
    }

    @Test
    fun `back button has a content description and a 48dp target`() {
        show(SampleDetail.loaded())
        rule.assertTouchTargetAtLeast48dp(TestTags.BACK_BUTTON)
        rule.onNodeWithContentDescription("Back", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun `shows decoded sections with values units and confidence`() {
        show(SampleDetail.loaded(SampleDetail.beacon))
        rule
            .onNodeWithTag(
                TestTags.detailDecoded("ibeacon"),
            ).performScrollTo()
            .assertTextContains("iBeacon", substring = true)
        rule
            .onNodeWithTag(
                TestTags.decodedField("ibeacon", "beacon_uuid"),
            ).assertTextEquals("E2C56DB5-DFFB-48D2-B060-D0F5A71096E0")
        rule.onNodeWithTag(TestTags.decodedField("ibeacon", "beacon_major")).assertTextEquals("1")
        rule.onNodeWithTag(TestTags.decodedField("ibeacon", "measured_power_1m_dbm")).assertTextEquals("-59 dBm")
        rule
            .onNodeWithTag(
                TestTags.decodedField("eddystone_tlm", "temperature_c"),
            ).performScrollTo()
            .assertTextEquals("24.5 \u00B0C")
        rule.onNodeWithTag(TestTags.decodedField("eddystone_tlm", "battery_mv")).assertTextEquals("3000 mV")
        rule
            .onNodeWithTag(TestTags.decodedField("eddystone_eid", "confidence"))
            .performScrollTo()
            .assertTextContains("Encrypted or rotating", substring = true)
        rule
            .onNodeWithTag(TestTags.decodedField("ibeacon", "confidence"))
            .assertTextContains("open specification", substring = true)
    }

    @Test
    fun `a device with nothing decoded has no decoded section`() {
        show(SampleDetail.loaded())
        rule.onNodeWithTag(TestTags.detailDecoded("ibeacon")).assertDoesNotExist()
    }

    @Test
    fun `sensor readings show with units and small values keep their precision`() {
        show(SampleDetail.loaded(SampleDetail.sensor))
        rule
            .onNodeWithTag(
                TestTags.decodedField("ruuvi_v5", "temperature_c"),
            ).performScrollTo()
            .assertTextEquals("24.3 \u00B0C")
        rule.onNodeWithTag(TestTags.decodedField("ruuvi_v5", "humidity_percent")).assertTextEquals("53.49 %")
        rule.onNodeWithTag(TestTags.decodedField("ruuvi_v5", "pressure_hpa")).assertTextEquals("1000.44 hPa")
        rule.onNodeWithTag(TestTags.decodedField("ruuvi_v5", "accel_x_g")).performScrollTo().assertTextEquals("0.004 G")
        rule
            .onNodeWithTag(
                TestTags.decodedField("ruuvi_v5", "battery_mv"),
            ).performScrollTo()
            .assertTextEquals("2977 mV")
        rule
            .onNodeWithTag(
                TestTags.decodedField("ruuvi_v5", "device_mac"),
            ).performScrollTo()
            .assertTextEquals("CB B8 33 4C 88 4F")
    }

    @Test
    fun `hint cards say unofficial, name only a type and never claim a model or owner`() {
        show(SampleDetail.loaded(SampleDetail.vendor))
        rule
            .onNodeWithTag(TestTags.detailDecoded("tracker_hint"))
            .performScrollTo()
            .assertTextContains("Looks like a location tracker (unverified)")
        rule
            .onNodeWithTag(TestTags.decodedField("tracker_hint", "confidence"))
            .assertTextContains("Unofficial hint", substring = true)
        rule
            .onNodeWithTag(
                TestTags.decodedField("tracker_hint", "tracker_family"),
            ).assertTextEquals("Apple Find My network message")
        rule
            .onNodeWithTag(
                TestTags.decodedField("apple_continuity", "apple_message"),
            ).performScrollTo()
            .assertTextEquals("Find My network")
    }

    @Test
    fun `radio section shows reported values and labelled estimates`() {
        show(SampleDetail.loaded(SampleDetail.radio))
        rule.onNodeWithTag(TestTags.detailRadio("primary_phy")).performScrollTo().assertTextEquals("LE 1M")
        rule.onNodeWithTag(TestTags.detailRadio("secondary_phy")).assertTextEquals("LE 2M")
        rule.onNodeWithTag(TestTags.detailRadio("advertising_type")).assertTextEquals("Extended")
        rule.onNodeWithTag(TestTags.detailRadio("sid")).assertTextEquals("3")
        rule.onNodeWithTag(TestTags.detailRadio("tx_power")).assertTextEquals("8 dBm")
        rule.onNodeWithTag(TestTags.detailRadio("periodic_interval")).assertTextEquals("100 ms")
        rule.onNodeWithTag(TestTags.detailRadio("data_status")).assertTextEquals("Complete")
        rule.onNodeWithTag(TestTags.detailRadio("interval")).assertTextEquals("about 500 ms between packets")
        rule.onNodeWithTag(TestTags.detailRadio("distance")).performScrollTo().assertTextEquals("About 1 m")
        rule.onNodeWithText("Packet interval (estimate)").assertExists()
        rule.onNodeWithText("Distance (rough estimate)").assertExists()
    }

    @Test
    fun `unavailable radio values are omitted and a device without radio data has no radio section`() {
        val partial =
            SampleDetail.radio.copy(
                radio =
                    SampleDetail.radio.radio!!.copy(
                        secondaryPhy = null,
                        advertisingSid = null,
                        txPowerDbm = null,
                        periodicIntervalUnits = null,
                        dataStatus = null,
                    ),
                intervalEstimateMs = null,
                distanceBand = null,
            )
        show(SampleDetail.loaded(partial))
        rule.onNodeWithTag(TestTags.detailRadio("primary_phy")).performScrollTo().assertExists()
        rule.onNodeWithTag(TestTags.detailRadio("secondary_phy")).assertDoesNotExist()
        rule.onNodeWithTag(TestTags.detailRadio("sid")).assertDoesNotExist()
        rule.onNodeWithTag(TestTags.detailRadio("tx_power")).assertDoesNotExist()
        rule.onNodeWithTag(TestTags.detailRadio("interval")).assertDoesNotExist()
        rule.onNodeWithTag(TestTags.detailRadio("distance")).assertDoesNotExist()
    }

    @Test
    fun `a device with no radio data shows no radio section`() {
        show(SampleDetail.loaded())
        rule.onNodeWithTag(TestTags.detailRadio("primary_phy")).assertDoesNotExist()
        rule.onNodeWithText("Radio").assertDoesNotExist()
    }

    // ---- System information section (Tier 2a) ----

    @Test
    fun `without the permission the section explains and offers to allow it`() {
        show(SampleDetail.loaded())
        rule.onNodeWithTag(TestTags.SYSTEM_INFO_SECTION).assertTextEquals("System information")
        rule
            .onNodeWithTag(
                TestTags.SYSTEM_INFO_MESSAGE,
            ).assertTextContains("Scanning works without it", substring = true)
        rule
            .onNodeWithTag(
                TestTags.SYSTEM_INFO_MESSAGE,
            ).assertTextContains("never pairs with, connects to or changes", substring = true)
        rule.onNodeWithTag(TestTags.SYSTEM_INFO_ALLOW_BUTTON).performClick()
        assertEquals(listOf("allow"), calls)
        rule.onNodeWithTag(TestTags.SYSTEM_INFO_SETTINGS_BUTTON).assertDoesNotExist()
    }

    @Test
    fun `a permanent denial offers the app settings instead of the prompt`() {
        show(SampleDetail.loaded(systemInfo = SystemInfoState.NeedsPermission(permanentlyDenied = true)))
        rule.onNodeWithTag(TestTags.SYSTEM_INFO_ALLOW_BUTTON).assertDoesNotExist()
        rule.onNodeWithTag(TestTags.SYSTEM_INFO_SETTINGS_BUTTON).performClick()
        assertEquals(listOf("settings"), calls)
    }

    @Test
    fun `loading and unavailable states have their own text`() {
        show(SampleDetail.loaded(systemInfo = SystemInfoState.Loading))
        rule.onNodeWithTag(TestTags.SYSTEM_INFO_LOADING).assertExists()
    }

    @Test
    fun `the system has nothing to say`() {
        show(SampleDetail.loaded(systemInfo = SystemInfoState.Unavailable))
        rule
            .onNodeWithTag(
                TestTags.SYSTEM_INFO_UNAVAILABLE,
            ).assertTextEquals("Android has no information about this device.")
    }

    @Test
    fun `loaded values are shown with friendly names and a read-only footer`() {
        show(SampleDetail.loaded(systemInfo = SampleDetail.systemInfoHeadset))
        rule.onNodeWithTag(TestTags.SYSTEM_NAME).assertTextEquals("Buds Pro")
        rule.onNodeWithTag(TestTags.SYSTEM_ALIAS).assertTextEquals("My buds")
        rule.onNodeWithTag(TestTags.SYSTEM_BOND_STATE).assertTextEquals("Paired")
        rule.onNodeWithTag(TestTags.SYSTEM_TYPE).assertTextEquals("Dual-mode (Classic and Low Energy)")
        rule.onNodeWithTag(TestTags.SYSTEM_CLASS).assertTextEquals("Audio or video: Headphones")
        rule.onNodeWithTag(TestTags.SYSTEM_ADDRESS_TYPE).assertTextEquals("Public")
        rule
            .onNodeWithTag(
                TestTags.systemUuid(0),
            ).performScrollTo()
            .assertTextContains("A2DP Audio Sink", substring = true)
        rule.onNodeWithTag(TestTags.systemUuid(1)).assertTextContains("Hands-Free", substring = true)
        rule.onNodeWithTag(TestTags.systemUuid(2)).assertTextContains("Battery", substring = true)
        rule.onNodeWithText("Read-only.", substring = true).assertExists()
        rule.onNodeWithText("Display only: BT Scan never pairs or unpairs devices.").assertExists()
    }

    @Test
    fun `missing values read not available and nothing is invented`() {
        show(SampleDetail.loaded(systemInfo = SampleDetail.systemInfoSparse))
        rule.onNodeWithTag(TestTags.SYSTEM_NAME).assertTextEquals("Not available")
        rule.onNodeWithTag(TestTags.SYSTEM_ALIAS).assertTextEquals("Not available")
        rule.onNodeWithTag(TestTags.SYSTEM_TYPE).assertTextEquals("Not available")
        rule.onNodeWithTag(TestTags.SYSTEM_CLASS).assertTextEquals("Not available")
        rule.onNodeWithTag(TestTags.SYSTEM_BOND_STATE).assertTextEquals("Not paired")
        rule.onNodeWithTag(TestTags.SYSTEM_ADDRESS_TYPE).assertDoesNotExist()
        rule.onNodeWithText("None cached").assertExists()
    }
}
