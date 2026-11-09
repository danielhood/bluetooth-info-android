package dev.danielhood.btscan

import android.app.Application

class BtScanApp : Application() {
    val container: AppContainer by lazy { AppContainer() }
}
