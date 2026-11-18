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
    const val CLASSIC_SCAN_BUTTON = "classic_scan_button"
    const val CLASSIC_SCAN_INDICATOR = "classic_scan_indicator"
    const val CLASSIC_NOTICE_DIALOG = "classic_notice_dialog"
    const val CLASSIC_NOTICE_CONFIRM = "classic_notice_confirm"
    const val CLASSIC_NOTICE_CANCEL = "classic_notice_cancel"
    const val LIST_NOTICE = "list_notice"
    const val DETAIL_FOUND_BY = "detail_found_by"

    const val DETAIL_SCREEN = "detail_screen"
    const val BACK_BUTTON = "back_button"
    const val DETAIL_NAME = "detail_name"
    const val DETAIL_ADDRESS = "detail_address"
    const val DETAIL_ADDRESS_TYPE = "detail_address_type"
    const val DETAIL_RSSI = "detail_rssi"
    const val DETAIL_CONNECTABLE = "detail_connectable"
    const val DETAIL_TX_POWER = "detail_tx_power"
    const val DETAIL_FIRST_SEEN = "detail_first_seen"
    const val DETAIL_LAST_SEEN = "detail_last_seen"
    const val DETAIL_AD_COUNT = "detail_ad_count"
    const val DETAIL_FLAGS = "detail_flags"
    const val DETAIL_RAW = "detail_raw"
    const val COPY_RAW_BUTTON = "copy_raw_button"
    const val SYSTEM_INFO_SECTION = "system_info_section"
    const val SYSTEM_INFO_MESSAGE = "system_info_message"
    const val SYSTEM_INFO_ALLOW_BUTTON = "system_info_allow_button"
    const val SYSTEM_INFO_SETTINGS_BUTTON = "system_info_settings_button"
    const val SYSTEM_INFO_LOADING = "system_info_loading"
    const val SYSTEM_INFO_UNAVAILABLE = "system_info_unavailable"
    const val SYSTEM_NAME = "system_name"
    const val SYSTEM_ALIAS = "system_alias"
    const val SYSTEM_BOND_STATE = "system_bond_state"
    const val SYSTEM_TYPE = "system_type"
    const val SYSTEM_CLASS = "system_class"
    const val OUT_OF_RANGE_BANNER = "out_of_range_banner"
    const val NOT_FOUND_MESSAGE = "not_found_message"
    const val NOT_FOUND_BACK = "not_found_back_button"

    fun deviceRow(address: String) = "device_row_$address"

    /** Kind chip in a list row, for example `kind_chip_C0:FF:EE:00:00:01`. */
    fun kindChip(address: String) = "kind_chip_$address"

    /** One line of the detail Radio section, for example `detail_radio_primary_phy`. */
    fun detailRadio(field: String) = "detail_radio_$field"

    /** One cached service UUID in the System information section. */
    fun systemUuid(index: Int) = "system_uuid_$index"

    fun detailService(index: Int) = "detail_service_$index"

    fun detailManufacturer(index: Int) = "detail_manufacturer_$index"

    fun detailServiceData(index: Int) = "detail_service_data_$index"

    /** Decoded-data card for a decoder, for example `detail_decoded_ibeacon`. */
    fun detailDecoded(decoder: String) = "detail_decoded_$decoder"

    /** One decoded value, for example `decoded_ibeacon_beacon_major`. */
    fun decodedField(decoder: String, key: String) = "decoded_${decoder}_$key"
}

/** Tags for the classic scan controls on the list screen. */
object ClassicTags {
    /** Source filter chip: `all`, `ble` or `classic`. */
    fun filter(name: String) = "source_filter_$name"

    /** Transport badge on a list row. */
    fun badge(address: String) = "source_badge_$address"
}

/** Tags for the profile lookup on the detail screen (Tier 2c). */
object ProfileTags {
    const val BUTTON = "profiles_button"
    const val RUNNING = "profiles_running"
    const val HEADER = "profiles_header"
    const val EMPTY = "profiles_empty"
    const val TIMEOUT = "profiles_timeout"
    const val NOT_STARTED = "profiles_not_started"
    const val DIALOG = "profiles_confirm_dialog"
    const val CONFIRM = "profiles_confirm"
    const val CANCEL = "profiles_cancel"

    fun uuid(index: Int) = "profile_uuid_$index"

    fun name(index: Int) = "profile_name_$index"
}
