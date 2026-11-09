package dev.danielhood.btscan.ui.list

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.danielhood.btscan.R

private const val MAX_BARS = 4
private const val BAR_STEP_DP = 4
private const val BAR_BASE_DP = 4

/** Four-step signal strength indicator; static (no animation). */
@Composable
fun SignalBars(level: Int, modifier: Modifier = Modifier) {
    val description = stringResource(R.string.signal_description, level)
    Row(
        modifier = modifier.semantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        for (bar in 1..MAX_BARS) {
            val color =
                if (bar <=
                    level
                ) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.outlineVariant
                }
            Box(Modifier.width(4.dp).height((BAR_BASE_DP + bar * BAR_STEP_DP).dp).background(color))
        }
    }
}
