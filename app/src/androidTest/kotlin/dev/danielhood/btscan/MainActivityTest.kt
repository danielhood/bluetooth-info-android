package dev.danielhood.btscan

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import dev.danielhood.btscan.ui.common.TestTags
import org.junit.Rule
import org.junit.Test

class MainActivityTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun launchShowsDeviceListPlaceholder() {
        composeRule.onNodeWithTag(TestTags.DEVICE_LIST_SCREEN).assertIsDisplayed()
        composeRule.onNodeWithTag(TestTags.SCAN_BUTTON).assertIsDisplayed()
    }
}
