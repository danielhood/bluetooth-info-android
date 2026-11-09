package dev.danielhood.btscan

import android.app.Application
import dev.danielhood.btscan.data.SystemClock
import dev.danielhood.btscan.data.ble.android.AndroidAdapterStateMonitor
import dev.danielhood.btscan.data.ble.android.AndroidBleScanner
import dev.danielhood.btscan.data.ble.android.AndroidPermissionChecker

class BtScanApp : Application() {
    val container: AppContainer by lazy {
        AppContainer(
            permissionChecker = AndroidPermissionChecker(this),
            realScanner = AndroidBleScanner(this, SystemClock),
            realAdapterMonitor = AndroidAdapterStateMonitor(this),
            clock = SystemClock,
        )
    }
}
