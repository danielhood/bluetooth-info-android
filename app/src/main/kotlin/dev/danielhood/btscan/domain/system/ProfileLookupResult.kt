package dev.danielhood.btscan.domain.system

import java.util.UUID

/** Outcome of querying a device which profiles it supports (a classic service lookup). */
sealed interface ProfileLookupResult {
    /** The device answered. [uuids] may be empty: it answered but lists nothing. */
    data class Profiles(
        val uuids: List<UUID>,
    ) : ProfileLookupResult

    /** The permission needed to contact the device is not (or no longer) granted. */
    data object PermissionMissing : ProfileLookupResult

    /** The platform would not start the query (unknown address, adapter off, device busy). */
    data object NotStarted : ProfileLookupResult
}
