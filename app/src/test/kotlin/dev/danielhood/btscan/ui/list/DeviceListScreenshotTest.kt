package dev.danielhood.btscan.ui.list

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import dev.danielhood.btscan.testing.SampleUi
import dev.danielhood.btscan.ui.AppSurface
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Reference images of every list state, in docs/screens/ (path relative to the app module).
 * Record with `scripts/gw.sh recordRoborazziDebug`, then look at the images before committing.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h640dp-xhdpi")
class DeviceListScreenshotTest {
    @get:Rule
    val rule = createComposeRule()

    private fun shoot(name: String, state: DeviceListUiState, dark: Boolean = false, locationText: Boolean = false) {
        rule.setContent {
            AppSurface(darkTheme = dark) {
                DeviceListContent(state, DeviceListActions(), locationText, debugLabel = null)
            }
        }
        rule.onRoot().captureRoboImage("../docs/screens/list-$name.png")
    }

    @Test
    fun idleNeverScanned() = shoot("idle-initial", SampleUi.state())

    @Test
    fun searching() = shoot("searching", SampleUi.state(ListStatus.Scanning))

    @Test
    fun scanningWithDevices() = shoot("scanning-devices", SampleUi.state(ListStatus.Scanning, SampleUi.devices))

    @Test
    fun scanningWithDevicesDark() =
        shoot("scanning-devices-dark", SampleUi.state(ListStatus.Scanning, SampleUi.devices), dark = true)

    @Test
    fun stoppedWithDevices() = shoot("stopped-devices", SampleUi.state(ListStatus.Idle, SampleUi.devices))

    @Test
    fun hideUnnamed() =
        shoot(
            "hide-unnamed",
            SampleUi.state(ListStatus.Scanning, SampleUi.devices.filter { it.name != null }, hideUnnamed = true),
        )

    @Test
    fun noDevicesFound() = shoot("no-devices", SampleUi.state(ListStatus.Idle, hasScanned = true))

    @Test
    fun permissionNeeded() = shoot("permission-needed", SampleUi.state(ListStatus.PermissionNeeded(false)))

    @Test
    fun permissionNeededLocation() =
        shoot("permission-needed-location", SampleUi.state(ListStatus.PermissionNeeded(false)), locationText = true)

    @Test
    fun permissionPermanentlyDenied() =
        shoot("permission-denied-forever", SampleUi.state(ListStatus.PermissionNeeded(true)))

    @Test
    fun bluetoothOff() = shoot("bluetooth-off", SampleUi.state(ListStatus.BluetoothOff))

    @Test
    fun locationOff() = shoot("location-off", SampleUi.state(ListStatus.LocationOff))

    @Test
    fun unsupported() = shoot("unsupported", SampleUi.state(ListStatus.Unsupported))

    @Test
    fun failed() = shoot("failed", SampleUi.failed)
}
