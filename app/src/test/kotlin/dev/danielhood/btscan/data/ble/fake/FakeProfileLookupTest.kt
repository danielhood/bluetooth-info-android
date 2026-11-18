package dev.danielhood.btscan.data.ble.fake

import dev.danielhood.btscan.domain.model.PermissionState
import dev.danielhood.btscan.domain.system.ProfileLookupResult
import dev.danielhood.btscan.testing.FakePermissionChecker
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FakeProfileLookupTest {
    private val addresses = Scenarios.require("system-info").devices.map { it.address }

    private fun lookup(permissions: FakePermissionChecker = FakePermissionChecker()) =
        FakeProfileLookup(permissions, Scenarios.require("system-info").profiles)

    @Test
    fun `the headset answers with its profiles and the log records the question`() =
        runTest {
            val fake = lookup()
            val result = fake.fetchProfiles(addresses[0])
            assertEquals(4, (result as ProfileLookupResult.Profiles).uuids.size)
            assertEquals(listOf(addresses[0]), fake.queried)
        }

    @Test
    fun `an unknown address refuses to start and a silent device never answers`() =
        runTest {
            val fake = lookup()
            assertEquals(ProfileLookupResult.NotStarted, fake.fetchProfiles(addresses[1]))
            var answered = false
            val job =
                backgroundScope.launch {
                    fake.fetchProfiles(addresses[2])
                    answered = true
                }
            advanceTimeBy(60_000)
            runCurrent()
            assertTrue("a silent device never answers", !answered)
            job.cancel()
        }

    @Test
    fun `the fake honours the real permission and queries nobody without it`() =
        runTest {
            val permissions = FakePermissionChecker(connect = PermissionState.Denied(canAskAgain = true))
            val fake = lookup(permissions)
            assertEquals(ProfileLookupResult.PermissionMissing, fake.fetchProfiles(addresses[0]))
            assertTrue(fake.queried.isEmpty())
        }
}
