package com.netkaize.subscription

import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Verifies that the signed app can launch its native login flow on the minimum supported API. */
@RunWith(AndroidJUnit4::class)
class CompatibilitySmokeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun nativeLoginScreenStartsOnTheMinimumSupportedApi() {
        composeRule.waitUntil(timeoutMillis = 30_000) {
            composeRule.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().size >= 2
        }
        composeRule.onNodeWithText("登录并同步").assertExists()
        composeRule.onNodeWithText("创建账户").assertExists()
        composeRule.onNodeWithText("忘记密码？").assertExists()
    }
}
