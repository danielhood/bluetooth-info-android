package dev.danielhood.btscan.data.ble.fake

import dev.danielhood.btscan.domain.model.PermissionState
import dev.danielhood.btscan.domain.port.PermissionChecker
import dev.danielhood.btscan.domain.port.ProfileLookup
import dev.danielhood.btscan.domain.system.ProfileLookupResult
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import java.util.UUID

/** What a fake device does when asked which profiles it supports. */
sealed interface FakeProfileBehaviour {
    /** Answers with [uuids] after [delayMs]. */
    data class Answers(
        val uuids: List<UUID>,
        val delayMs: Long = 800,
    ) : FakeProfileBehaviour

    /** Never answers, like a device that went away: the caller's 10 s limit ends it. */
    data object Silent : FakeProfileBehaviour

    /** The platform refuses to start the query. */
    data object Refuses : FakeProfileBehaviour
}

/**
 * Fake service lookup. It honours the real connect permission, like the real source; the answers are canned per
 * address. Addresses a scenario does not list refuse to start, like an unknown device.
 */
class FakeProfileLookup(
    private val permissions: PermissionChecker,
    private val behaviours: Map<String, FakeProfileBehaviour>,
) : ProfileLookup {
    /** Addresses that were asked, in order (a test or smoke script can assert that nothing was asked unprompted). */
    val asked = mutableListOf<String>()

    override suspend fun fetchProfiles(address: String): ProfileLookupResult {
        if (permissions.connectState() !is PermissionState.Granted) return ProfileLookupResult.PermissionMissing
        asked += address
        return when (val behaviour = behaviours[address]) {
            is FakeProfileBehaviour.Answers -> {
                delay(behaviour.delayMs)
                ProfileLookupResult.Profiles(behaviour.uuids)
            }

            FakeProfileBehaviour.Silent -> {
                awaitCancellation()
            }

            FakeProfileBehaviour.Refuses, null -> {
                ProfileLookupResult.NotStarted
            }
        }
    }
}
