package dev.danielhood.btscan.data.ble.fake

import dev.danielhood.btscan.domain.model.PermissionState
import dev.danielhood.btscan.domain.port.PermissionChecker
import dev.danielhood.btscan.domain.port.ProfileLookup
import dev.danielhood.btscan.domain.system.ProfileLookupResult
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import java.util.UUID

/** What a fake device does when queried for the profiles it supports. */
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
    /** Addresses that were queried, in order (tests and smoke scripts assert nothing is queried unprompted). */
    val queried = mutableListOf<String>()

    override suspend fun fetchProfiles(address: String): ProfileLookupResult {
        if (permissions.connectState() !is PermissionState.Granted) return ProfileLookupResult.PermissionMissing
        queried += address
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
