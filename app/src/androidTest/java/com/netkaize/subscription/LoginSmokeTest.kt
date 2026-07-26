package com.netkaize.subscription

import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Production smoke for the native Compose flow.
 *
 * Credentials are supplied by instrumentation arguments so they never enter the repository or
 * test report. The test fails closed when release credentials are missing.
 */
class LoginSmokeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun productionAccountCanLogInAndReadTheLedger() {
        val arguments = InstrumentationRegistry.getArguments()
        val email = arguments.getString("smokeEmail").orEmpty()
        val password = arguments.getString("smokePassword").orEmpty()
        assertTrue(
            "Smoke credentials are required for the production login test",
            email.isNotBlank() && password.isNotBlank(),
        )

        composeRule.waitUntil(timeoutMillis = 20_000) {
            composeRule.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().size >= 2
        }
        val fields = composeRule.onAllNodes(hasSetTextAction())
        fields[0].performTextInput(email)
        fields[1].performTextInput(password)
        composeRule.onNodeWithText("登录并同步").performClick()

        composeRule.waitUntil(timeoutMillis = 30_000) {
            runCatching { composeRule.onNodeWithTag("nav_home").fetchSemanticsNode() }.isSuccess
        }
        composeRule.onNodeWithText("订阅支出概览").assertExists()
        composeRule.onNodeWithTag("nav_subscriptions").performClick()
        composeRule.onNodeWithText("已订阅服务").assertExists()
        composeRule.onNodeWithTag("nav_add").performClick()
        composeRule.onNodeWithText("添加订阅").assertExists()
        composeRule.onNodeWithTag("nav_analysis").performClick()
        composeRule.onNodeWithText("支出分析").assertExists()
        composeRule.onNodeWithTag("nav_profile").performClick()
        composeRule.onNodeWithText("个人中心").assertExists()
        composeRule.onNodeWithText(email).assertExists()
    }
}
