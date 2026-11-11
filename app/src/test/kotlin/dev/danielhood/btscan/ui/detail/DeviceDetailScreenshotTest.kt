package dev.danielhood.btscan.ui.detail

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import dev.danielhood.btscan.testing.SampleDetail
import dev.danielhood.btscan.ui.AppSurface
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Reference images of the detail screen states in docs/screens/. Record with `recordRoborazziDebug`. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h900dp-xhdpi")
class DeviceDetailScreenshotTest {
    @get:Rule
    val rule = createComposeRule()

    private fun shoot(name: String, state: DeviceDetailUiState, dark: Boolean = false) {
        rule.setContent {
            AppSurface(darkTheme = dark) { DeviceDetailContent(state, onBack = {}, onCopyRaw = {}) }
        }
        rule.onRoot().captureRoboImage("../docs/screens/detail-$name.png")
    }

    @Test
    fun loaded() = shoot("loaded", SampleDetail.loaded())

    @Test
    fun loadedDark() = shoot("loaded-dark", SampleDetail.loaded(), dark = true)

    @Test
    fun sparseFields() = shoot("sparse-fields", SampleDetail.loaded(SampleDetail.sparse))

    @Test
    fun beacon() = shoot("beacon", SampleDetail.loaded(SampleDetail.beacon))

    @Test
    fun beaconDark() = shoot("beacon-dark", SampleDetail.loaded(SampleDetail.beacon), dark = true)

    @Test
    fun outOfRange() = shoot("out-of-range", SampleDetail.loaded(outOfRange = true))

    @Test
    fun notFound() = shoot("not-found", DeviceDetailUiState.NotFound)
}
