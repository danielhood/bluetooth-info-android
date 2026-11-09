package dev.danielhood.btscan.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.danielhood.btscan.AppContainer
import dev.danielhood.btscan.DebugConfig
import dev.danielhood.btscan.testing.FakePermissionChecker
import dev.danielhood.btscan.ui.common.TestTags
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The whole app on the JVM with the fake scanner on real time: list to detail and back (acceptance 2, 3, 4).
 * Real time is used because the fake scanner and repository run on their own dispatcher.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h900dp-xhdpi")
class AppNavigationTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var container: AppContainer

    private val thermo = "C0:FF:EE:00:00:01"

    @Before
    fun setUp() {
        container = AppContainer(permissionChecker = FakePermissionChecker(), scope = scope)
        container.debugConfig = DebugConfig(scanner = "fake", scenario = "default")
        rule.setContent { AppRoot(container = container, debugLabel = null, darkTheme = false) }
    }

    @After
    fun tearDown() = scope.cancel()

    private fun startScanAndOpenThermo() {
        rule.onNodeWithTag(TestTags.SCAN_BUTTON).performClick()
        rule.waitUntil(timeoutMillis = 10_000) {
            rule.onAllNodesWithTag(TestTags.deviceRow(thermo)).fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithTag(TestTags.deviceRow(thermo)).performClick()
    }

    @Test
    fun `tapping a row opens the detail screen for that device (acceptance 2)`() {
        startScanAndOpenThermo()
        rule.waitUntil(timeoutMillis = 5_000) {
            rule.onAllNodesWithTag(TestTags.DETAIL_SCREEN).fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithTag(TestTags.DETAIL_NAME).assertTextEquals("Thermo Sensor")
        rule.onNodeWithTag(TestTags.DETAIL_ADDRESS).assertTextEquals(thermo)
        rule.onNodeWithTag(TestTags.DETAIL_TX_POWER).assertTextEquals("-4 dBm")
        rule
            .onNodeWithTag(
                TestTags.detailService(0),
            ).performScrollTo()
            .assertTextContains("Environmental Sensing", substring = true)
    }

    @Test
    fun `detail rssi is live (acceptance 3)`() {
        startScanAndOpenThermo()
        rule.waitUntil(timeoutMillis = 5_000) {
            rule.onAllNodesWithTag(TestTags.DETAIL_RSSI).fetchSemanticsNodes().isNotEmpty()
        }

        fun rssiText() =
            rule
                .onNodeWithTag(TestTags.DETAIL_RSSI)
                .fetchSemanticsNode()
                .config
                .getOrNull(SemanticsProperties.Text)
                ?.joinToString { it.text }
        val first = rssiText()
        rule.waitUntil(timeoutMillis = 8_000) { rssiText() != first }
    }

    @Test
    fun `back from detail returns to the list with scanning and devices intact (acceptance 4)`() {
        startScanAndOpenThermo()
        rule.waitUntil(timeoutMillis = 5_000) {
            rule.onAllNodesWithTag(TestTags.DETAIL_SCREEN).fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithTag(TestTags.BACK_BUTTON).performClick()
        rule.waitUntil(timeoutMillis = 5_000) {
            rule.onAllNodesWithTag(TestTags.DEVICE_LIST).fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithTag(TestTags.SCAN_BUTTON).assertTextContains("Stop scan")
        rule.onNodeWithTag(TestTags.deviceRow(thermo)).assertIsDisplayed()
    }

    @Test
    fun `system back from detail also returns to the list`() {
        startScanAndOpenThermo()
        rule.waitUntil(timeoutMillis = 5_000) {
            rule.onAllNodesWithTag(TestTags.DETAIL_SCREEN).fetchSemanticsNodes().isNotEmpty()
        }
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        rule.waitUntil(timeoutMillis = 5_000) {
            rule.onAllNodesWithTag(TestTags.DEVICE_LIST).fetchSemanticsNodes().isNotEmpty()
        }
    }
}
