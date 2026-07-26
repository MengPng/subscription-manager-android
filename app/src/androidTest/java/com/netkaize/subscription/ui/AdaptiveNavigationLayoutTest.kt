package com.netkaize.subscription.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test

class AdaptiveNavigationLayoutTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun compact320UsesBottomNavigationAndAccessibleTargets() {
        renderAt(widthDp = 320, heightDp = 568)

        composeRule.onNodeWithTag("bottom_navigation").assertIsDisplayed()
        composeRule.onAllNodesWithTag("navigation_rail").assertCountEquals(0)
        composeRule.onNodeWithTag("nav_add")
            .assertHeightIsAtLeast(MinimumTouchTargetDp.dp)
            .assertWidthIsAtLeast(MinimumTouchTargetDp.dp)
    }

    @Test
    fun mediumLandscapeUsesRailWithoutBottomNavigation() {
        renderAt(widthDp = 700, heightDp = 360)

        composeRule.onNodeWithTag("navigation_rail").assertIsDisplayed()
        composeRule.onAllNodesWithTag("bottom_navigation").assertCountEquals(0)
    }

    @Test
    fun expandedWidthKeepsRailNavigation() {
        renderAt(widthDp = 900, heightDp = 700)

        composeRule.onNodeWithTag("navigation_rail").assertIsDisplayed()
        composeRule.onAllNodesWithTag("bottom_navigation").assertCountEquals(0)
    }

    private fun renderAt(widthDp: Int, heightDp: Int) {
        composeRule.setContent {
            DingYueTheme {
                Box(Modifier.requiredSize(widthDp.dp, heightDp.dp)) {
                    AdaptiveNavigationFrame(
                        destination = MainDestination.HOME,
                        onNavigate = {},
                        modifier = Modifier.fillMaxSize(),
                    ) { _, layout ->
                        Text(layout.widthClass.name)
                    }
                }
            }
        }
    }
}
