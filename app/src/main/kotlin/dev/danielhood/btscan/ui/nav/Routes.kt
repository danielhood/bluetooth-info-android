package dev.danielhood.btscan.ui.nav

import kotlinx.serialization.Serializable

/** Type-safe navigation destinations. */
@Serializable
data object ListDestination

@Serializable
data class DetailDestination(
    val address: String,
)
