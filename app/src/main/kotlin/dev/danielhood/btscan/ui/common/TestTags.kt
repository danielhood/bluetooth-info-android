package dev.danielhood.btscan.ui.common

/** All Modifier.testTag values. Tags double as `resource-id` in uiautomator dumps; keep them stable. */
object TestTags {
    const val DEVICE_LIST_SCREEN = "device_list_screen"
    const val SCAN_BUTTON = "scan_button"
    const val CLEAR_BUTTON = "clear_button"
    const val HIDE_UNNAMED_SWITCH = "hide_unnamed_switch"
    const val DEVICE_COUNT = "device_count"
    const val SCAN_INDICATOR = "scan_indicator"
    const val DEVICE_LIST = "device_list"
    const val EMPTY_MESSAGE = "empty_message"
    const val STATUS_MESSAGE = "status_message"
    const val STATUS_ACTION = "status_action_button"
    const val FAILURE_BANNER = "failure_banner"
    const val RETRY_BUTTON = "retry_button"
    const val DEBUG_LABEL = "debug_label"

    fun deviceRow(address: String) = "device_row_$address"
}
