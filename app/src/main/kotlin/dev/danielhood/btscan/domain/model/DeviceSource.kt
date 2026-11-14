package dev.danielhood.btscan.domain.model

/** Which radio technology found a device: BLE advertising, a classic inquiry, or both (same address seen by each). */
enum class DeviceSource { BLE, CLASSIC, BOTH }
