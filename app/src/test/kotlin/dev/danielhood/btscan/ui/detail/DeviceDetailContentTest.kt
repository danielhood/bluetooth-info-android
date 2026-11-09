package dev.danielhood.btscan.ui.detail

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.danielhood.btscan.testing.SampleDetail
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
                DeviceDetailContent(state, onBack = { calls += "back" }, onCopyRaw = { calls += "copy=$it" })
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
}
