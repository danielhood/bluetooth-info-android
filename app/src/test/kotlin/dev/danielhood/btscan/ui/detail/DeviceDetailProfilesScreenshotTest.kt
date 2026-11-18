package dev.danielhood.btscan.ui.detail

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import dev.danielhood.btscan.domain.parser.AdvertisementParser
import dev.danielhood.btscan.testing.SampleDetail
import dev.danielhood.btscan.ui.AppSurface
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Reference images of the profile lookup states; taller than the other detail images so the section is in view. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h1500dp-xhdpi")
class DeviceDetailProfilesScreenshotTest {
    @get:Rule
    val rule = createComposeRule()

    private fun shoot(name: String, profiles: ProfileLookupState) {
        rule.setContent {
            AppSurface(darkTheme = false) {
                DeviceDetailContent(
                    SampleDetail.loaded(
                        SampleDetail.sparse.copy(name = "Buds Pro"),
                        systemInfo = SampleDetail.systemInfoSparse,
                        profiles = profiles,
                        canLookUpProfiles = true,
                    ),
                    onBack = {},
                    onCopyRaw = {},
                )
            }
        }
        rule.onRoot().captureRoboImage("../docs/screens/detail-profiles-$name.png")
    }

    @Test
    fun idle() = shoot("idle", ProfileLookupState.Idle)

    @Test
    fun running() = shoot("running", ProfileLookupState.Running)

    @Test
    fun confirming() = shoot("confirm", ProfileLookupState.Confirming)

    @Test
    fun answered() =
        shoot(
            "answered",
            ProfileLookupState.Done(
                listOf(0x110B, 0x111E, 0x110E, 0x1124).map { AdvertisementParser.uuidFromShort(it.toLong()) },
            ),
        )

    @Test
    fun timedOut() = shoot("timeout", ProfileLookupState.TimedOut)
}
