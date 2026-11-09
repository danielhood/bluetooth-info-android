package dev.danielhood.btscan.ui.common

import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics

/**
 * Test tag on a container whose child texts should read as one node (for TalkBack, tests and `ui.sh dump`,
 * where the container's `text` becomes the concatenation of its children).
 */
fun Modifier.mergedTestTag(tag: String): Modifier = testTag(tag).semantics(mergeDescendants = true) {}
