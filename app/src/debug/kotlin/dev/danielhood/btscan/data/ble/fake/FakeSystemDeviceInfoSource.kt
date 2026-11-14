package dev.danielhood.btscan.data.ble.fake

import dev.danielhood.btscan.domain.model.PermissionState
import dev.danielhood.btscan.domain.port.PermissionChecker
import dev.danielhood.btscan.domain.port.SystemDeviceInfoSource
import dev.danielhood.btscan.domain.system.SystemDeviceInfo
import dev.danielhood.btscan.domain.system.SystemInfoResult

/**
 * Fake "what Android knows" lookup. It honours the real connect permission, so the progressive-permission flow
 * (revoke, ask, grant) behaves exactly as with the real source; only the values are canned.
 */
class FakeSystemDeviceInfoSource(
    private val permissions: PermissionChecker,
    private val known: Map<String, SystemDeviceInfo>,
) : SystemDeviceInfoSource {
    override suspend fun lookup(address: String): SystemInfoResult =
        when {
            permissions.connectState() !is PermissionState.Granted -> SystemInfoResult.PermissionMissing
            else -> known[address]?.let { SystemInfoResult.Available(it) } ?: SystemInfoResult.Unavailable
        }
}
