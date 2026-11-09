package dev.danielhood.btscan.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import dev.danielhood.btscan.ui.AppRoot
import dev.danielhood.btscan.ui.common.TestTags
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Reference images live in docs/screens/ (relative to the app module). Record with recordRoborazziDebug. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h640dp-xhdpi")
class DeviceListScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun placeholderShowsScreenAndButton() {
        composeRule.setContent { AppRoot(debugLabel = null, darkTheme = false) }
        composeRule.onNodeWithTag(TestTags.DEVICE_LIST_SCREEN).assertIsDisplayed()
        composeRule.onNodeWithTag(TestTags.SCAN_BUTTON).assertIsDisplayed()
        composeRule.onRoot().captureRoboImage("../docs/screens/device-list-placeholder.png")
    }

    @Test
    fun placeholderShowsDebugLabelWhenOverrideActive() {
        composeRule.setContent {
            AppRoot(debugLabel = "scanner=fake scenario=default", darkTheme = true)
        }
        composeRule.onNodeWithTag(TestTags.DEBUG_LABEL).assertIsDisplayed()
        composeRule.onRoot().captureRoboImage("../docs/screens/device-list-placeholder-debug-dark.png")
    }
}
