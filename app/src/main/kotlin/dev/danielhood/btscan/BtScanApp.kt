package dev.danielhood.btscan

import android.app.Application
import dev.danielhood.btscan.data.SystemClock
import dev.danielhood.btscan.data.ble.android.AndroidAdapterStateMonitor
import dev.danielhood.btscan.data.ble.android.AndroidBleScanner
import dev.danielhood.btscan.data.ble.android.AndroidPermissionChecker

/** Lets the activity find the container without depending on the concrete Application (tests substitute one). */
interface ContainerHolder {
    val container: AppContainer
}

class BtScanApp :
    Application(),
    ContainerHolder {
    override val container: AppContainer by lazy {
        AppContainer(
            permissionChecker = AndroidPermissionChecker(this),
            realScanner = AndroidBleScanner(this, SystemClock),
            realAdapterMonitor = AndroidAdapterStateMonitor(this),
            clock = SystemClock,
        )
    }
}
