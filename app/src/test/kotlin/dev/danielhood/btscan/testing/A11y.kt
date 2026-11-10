package dev.danielhood.btscan.testing

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteractionsProvider
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onNodeWithTag
import org.junit.Assert.assertTrue

/** Every node a user can click or toggle must expose a text or content description (TalkBack needs a name). */
fun SemanticsNodeInteractionsProvider.assertAllInteractiveNodesLabelled() {
    val nodes =
        onAllNodes(
            hasClickAction() or SemanticsMatcher.keyIsDefined(SemanticsProperties.ToggleableState),
        ).fetchSemanticsNodes()
    assertTrue("expected some interactive nodes", nodes.isNotEmpty())
    nodes.forEach { node ->
        val text =
            node.config
                .getOrNull(SemanticsProperties.Text)
                ?.joinToString { it.text }
                .orEmpty()
        val description =
            node.config
                .getOrNull(SemanticsProperties.ContentDescription)
                ?.joinToString()
                .orEmpty()
        assertTrue("interactive node ${node.id} has no label", text.isNotBlank() || description.isNotBlank())
    }
}

private const val MIN_TOUCH_DP = 48f

/** The touch bounds (what a finger can hit, including Material's minimum-size padding) must be 48dp or more. */
fun ComposeContentTestRule.assertTouchTargetAtLeast48dp(tag: String) {
    val bounds = onNodeWithTag(tag).fetchSemanticsNode().touchBoundsInRoot
    val widthDp = bounds.width / density.density
    val heightDp = bounds.height / density.density
    assertTrue("$tag touch target is ${widthDp}x${heightDp}dp", widthDp >= MIN_TOUCH_DP && heightDp >= MIN_TOUCH_DP)
}
