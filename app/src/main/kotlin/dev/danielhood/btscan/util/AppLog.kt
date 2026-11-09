package dev.danielhood.btscan.util

import android.util.Log

/** Thin wrapper over [Log] so every app log line carries the `BtScan/` prefix that scripts/logs.sh filters on. */
object AppLog {
    const val APP = "BtScan/App"
    const val UI = "BtScan/UI"

    fun i(tag: String, message: String) {
        Log.i(tag, message)
    }

    fun d(tag: String, message: String) {
        Log.d(tag, message)
    }
}
