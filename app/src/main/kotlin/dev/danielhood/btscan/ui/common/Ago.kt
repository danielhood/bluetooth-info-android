package dev.danielhood.btscan.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import dev.danielhood.btscan.R

private const val SECONDS_PER_MINUTE = 60L
private const val SECONDS_PER_HOUR = 3600L

/** Relative age such as "12 s ago", "3 min ago", "2 h ago". */
@Composable
fun agoText(seconds: Long): String =
    when {
        seconds < SECONDS_PER_MINUTE -> stringResource(R.string.seconds_ago, seconds)
        seconds < SECONDS_PER_HOUR -> stringResource(R.string.minutes_ago, seconds / SECONDS_PER_MINUTE)
        else -> stringResource(R.string.hours_ago, seconds / SECONDS_PER_HOUR)
    }
