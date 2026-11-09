package dev.danielhood.btscan

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dev.danielhood.btscan.ui.AppRoot
import dev.danielhood.btscan.util.AppLog

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as BtScanApp).container
        val debugConfig = container.debugOverrides.configFrom(intent)
        container.debugConfig = debugConfig
        AppLog.i(AppLog.APP, "launch debugConfig=${debugConfig.label ?: "none"}")
        enableEdgeToEdge()
        setContent { AppRoot(container = container, debugLabel = debugConfig.label) }
    }
}
