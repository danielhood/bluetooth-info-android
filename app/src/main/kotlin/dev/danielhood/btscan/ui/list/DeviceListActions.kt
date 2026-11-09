package dev.danielhood.btscan.ui.list

/** Callbacks from the list screen; the stateful route wires them to the ViewModel and the system. */
data class DeviceListActions(
    val onToggleScan: () -> Unit = {},
    val onClear: () -> Unit = {},
    val onHideUnnamedChange: (Boolean) -> Unit = {},
    val onDeviceClick: (String) -> Unit = {},
    val onGrantPermission: () -> Unit = {},
    val onOpenAppSettings: () -> Unit = {},
    val onOpenBluetoothSettings: () -> Unit = {},
    val onOpenLocationSettings: () -> Unit = {},
)
