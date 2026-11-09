package dev.danielhood.btscan

import android.app.Application
import dev.danielhood.btscan.data.ble.android.AndroidPermissionChecker

class BtScanApp : Application() {
    val container: AppContainer by lazy { AppContainer(permissionChecker = AndroidPermissionChecker(this)) }
}
