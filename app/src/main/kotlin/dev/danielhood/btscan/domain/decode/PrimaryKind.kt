package dev.danielhood.btscan.domain.decode

/** Most informative first: the list shows one chip per device, taken from the first of these that matched. */
internal val KIND_PRIORITY =
    listOf(
        DecoderId.RUUVI_V5,
        DecoderId.BTHOME,
        DecoderId.IBEACON,
        DecoderId.ALTBEACON,
        DecoderId.EDDYSTONE_UID,
        DecoderId.EDDYSTONE_URL,
        DecoderId.EDDYSTONE_TLM,
        DecoderId.EDDYSTONE_EID,
        DecoderId.FAST_PAIR,
        DecoderId.TRACKER_HINT,
        DecoderId.APPLE_CONTINUITY,
    )

/** The decoder whose name labels this device in the list, or null when nothing matched. */
fun List<Decoded>.primaryKind(): DecoderId? = KIND_PRIORITY.firstOrNull { id -> any { it.decoder == id } }
