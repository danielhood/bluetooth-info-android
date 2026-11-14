package dev.danielhood.btscan.domain.model

/** How a device's address was formed, as the platform reports it for a scan result (Android 15 and later). */
enum class AddressType { PUBLIC, RANDOM, ANONYMOUS, UNKNOWN }
