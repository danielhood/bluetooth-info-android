package dev.danielhood.btscan

import android.app.Application
import dev.danielhood.btscan.data.SystemClock
import dev.danielhood.btscan.data.ble.android.AndroidAdapterStateMonitor
import dev.danielhood.btscan.data.ble.android.AndroidBleScanner
import dev.danielhood.btscan.data.ble.android.AndroidClassicScanner
import dev.danielhood.btscan.data.ble.android.AndroidPermissionChecker
import dev.danielhood.btscan.data.ble.android.AndroidSystemDeviceInfoSource
import kotlinx.coroutines.Dispatchers

/** Lets the activity find the container without depending on the concrete Application (tests substitute one). */
interface ContainerHolder {
    val container: AppContainer
}

class BtScanApp :
    Application(),
    ContainerHolder {
    override val container: AppContainer by lazy {
        val permissions = AndroidPermissionChecker(this)
        AppContainer(
            permissionChecker = permissions,
            real =
                RealPorts(
                    scanner = AndroidBleScanner(this, SystemClock),
                    adapterMonitor = AndroidAdapterStateMonitor(this),
                    systemInfo = AndroidSystemDeviceInfoSource(this, permissions, Dispatchers.IO),
                    classicScanner = AndroidClassicScanner(this, SystemClock),
                ),
            clock = SystemClock,
        )
    }
}
