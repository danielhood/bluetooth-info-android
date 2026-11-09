package dev.danielhood.btscan

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.rule.GrantPermissionRule
import dev.danielhood.btscan.ui.common.TestTags
import org.junit.After
import org.junit.Rule
import org.junit.Test

/** On-device checks of the list screen with the fake scanner (acceptance 1; the runtime permission is granted). */
class MainActivityTest {
    @get:Rule(order = 0)
    val permission: GrantPermissionRule =
        GrantPermissionRule.grant(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Manifest.permission.BLUETOOTH_SCAN
            } else {
                Manifest.permission.ACCESS_FINE_LOCATION
            },
        )

    @get:Rule(order = 1)
    val compose = createEmptyComposeRule()

    private var scenario: ActivityScenario<MainActivity>? = null

    @After
    fun tearDown() {
        scenario?.close()
    }

    private fun launch(scenarioName: String) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val intent =
            Intent(context, MainActivity::class.java)
                .putExtra("scanner", "fake")
                .putExtra("scenario", scenarioName)
        scenario = ActivityScenario.launch(intent)
    }

    private fun rowCount() = compose.onAllNodesWithTag(TestTags.DEVICE_LIST).fetchSemanticsNodes().size

    @Test
    fun launchShowsTheListScreenReadyToScan() {
        launch("default")
        compose.onNodeWithTag(TestTags.DEVICE_LIST_SCREEN).assertIsDisplayed()
        compose.onNodeWithTag(TestTags.SCAN_BUTTON).assertTextContains("Start scan")
        compose.onNodeWithTag(TestTags.EMPTY_MESSAGE).assertTextEquals("Tap Start scan to look for nearby devices")
    }

    @Test
    fun startingTheDefaultScenarioListsDevices() {
        launch("default")
        compose.onNodeWithTag(TestTags.SCAN_BUTTON).performClick()
        compose.waitUntil(timeoutMillis = 10_000) { rowCount() == 1 }
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodesWithTag(TestTags.DEVICE_COUNT).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag(TestTags.deviceRow("C0:FF:EE:00:00:01")).assertIsDisplayed()
        compose.onNodeWithTag(TestTags.SCAN_BUTTON).assertTextContains("Stop scan")
    }
}
