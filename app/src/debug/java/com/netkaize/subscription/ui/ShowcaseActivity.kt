package com.netkaize.subscription.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.netkaize.subscription.data.AccountUser
import com.netkaize.subscription.data.BillingCycle
import com.netkaize.subscription.data.CurrencyInfo
import com.netkaize.subscription.data.CurrencyRates
import com.netkaize.subscription.data.ServiceTemplate
import com.netkaize.subscription.data.Session
import com.netkaize.subscription.data.Subscription
import java.time.Instant
import java.time.LocalDate

/**
 * Debug-only activity used to capture truthful product screenshots at several Android sizes.
 * It never ships in the release APK and never connects to, writes, or represents a real account.
 */
class ShowcaseActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val destination = when (intent.getStringExtra("screen")) {
            "subscriptions" -> MainDestination.SUBSCRIPTIONS
            "add" -> MainDestination.ADD
            "analysis" -> MainDestination.ANALYSIS
            else -> MainDestination.HOME
        }
        val state = showcaseState(destination)
        setContent {
            DingYueTheme {
                AdaptiveNavigationFrame(
                    destination = destination,
                    onNavigate = {},
                ) { padding, layout ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding)
                            .background(AppCanvas),
                        contentAlignment = Alignment.TopCenter,
                    ) {
                        when (destination) {
                            MainDestination.HOME -> HomeScreen(
                                state = state,
                                layout = layout,
                                onEdit = {},
                                onDelete = {},
                                onScheduleCancel = { _, _ -> },
                                onConfirmRenewal = {},
                            )
                            MainDestination.SUBSCRIPTIONS -> SubscriptionsScreen(
                                state = state,
                                layout = layout,
                                onEdit = {},
                                onDelete = {},
                            )
                            MainDestination.ADD -> AddScreen(
                                state = state,
                                layout = layout,
                                onCustom = {},
                                onTemplate = {},
                                onExisting = {},
                            )
                            MainDestination.ANALYSIS -> AnalysisScreen(state, layout)
                            MainDestination.PROFILE -> Unit
                        }
                    }
                }
            }
        }
    }
}

private fun showcaseState(destination: MainDestination): AppUiState {
    val today = LocalDate.now()
    val user = AccountUser(
        id = "showcase-user",
        email = "demo@netkaize.com",
        displayName = "凯",
        createdAt = Instant.now().toString(),
        isAdmin = false,
        currencyCode = "CNY",
    )
    val subscriptions = listOf(
        Subscription(
            id = "demo-chatgpt",
            name = "ChatGPT Team",
            category = "AI",
            note = "团队协作与日常研究",
            priceCny = 180.0,
            cycle = BillingCycle.MONTHLY,
            startDate = today.minusMonths(8),
            nextDate = today.plusDays(5),
            renewalAnchorDate = today.plusDays(5),
            usageCount = 22,
            officialUrl = "https://chatgpt.com/",
            manageUrl = "https://chatgpt.com/",
            lastReviewedAt = today.minusDays(18),
            icon = "AI",
            iconKey = "sparkles",
            color = "#10A37F",
        ),
        Subscription(
            id = "demo-feishu",
            name = "飞书专业版",
            category = "协作",
            note = "项目协作",
            priceCny = 199.0,
            cycle = BillingCycle.MONTHLY,
            startDate = today.minusMonths(5),
            nextDate = today.plusDays(12),
            renewalAnchorDate = today.plusDays(12),
            usageCount = 14,
            lastReviewedAt = today.minusDays(40),
            icon = "飞",
            iconKey = "cloud",
            color = "#3370FF",
        ),
        Subscription(
            id = "demo-tencent-cloud",
            name = "腾讯云轻量服务器",
            category = "云服务",
            note = "生产环境",
            priceCny = 1288.0,
            cycle = BillingCycle.YEARLY,
            startDate = today.minusYears(2),
            nextDate = today.plusDays(42),
            renewalAnchorDate = today.plusDays(42),
            usageCount = 365,
            lastReviewedAt = today.minusDays(120),
            icon = "云",
            iconKey = "cloud",
            color = "#006EFF",
        ),
        Subscription(
            id = "demo-music",
            name = "Apple Music",
            category = "娱乐",
            note = "家庭共享",
            priceCny = 17.0,
            cycle = BillingCycle.MONTHLY,
            startDate = today.minusYears(1),
            nextDate = today.plusDays(22),
            renewalAnchorDate = today.plusDays(22),
            usageCount = 25,
            icon = "音",
            iconKey = "music",
            color = "#FA243C",
        ),
        Subscription(
            id = "demo-once",
            name = "Affinity Designer",
            category = "设计",
            note = "一次性买断",
            priceCny = 488.0,
            cycle = BillingCycle.ONCE,
            startDate = today.minusDays(190),
            usageCount = 30,
            icon = "设",
            iconKey = "sparkles",
            color = "#7C3AED",
        ),
    )
    val templates = listOf(
        ServiceTemplate("tpl-chatgpt", "ChatGPT Team", "AI", 180.0, BillingCycle.MONTHLY, "AI", "#10A37F", "", "https://chatgpt.com/", "https://chatgpt.com/", "AI 团队订阅", true),
        ServiceTemplate("tpl-feishu", "飞书专业版", "协作", 199.0, BillingCycle.MONTHLY, "飞", "#3370FF", "", "https://www.feishu.cn/", "", "团队协作", true),
        ServiceTemplate("tpl-notion", "Notion Plus", "协作", 72.0, BillingCycle.MONTHLY, "N", "#1D1D1F", "", "https://www.notion.so/", "", "知识管理", true),
        ServiceTemplate("tpl-music", "Apple Music", "娱乐", 17.0, BillingCycle.MONTHLY, "音", "#FA243C", "", "https://music.apple.com/", "", "音乐服务", true),
        ServiceTemplate("tpl-cloud", "腾讯云轻量服务器", "云服务", 0.0, BillingCycle.YEARLY, "云", "#006EFF", "", "https://cloud.tencent.com/", "", "云服务器", true),
        ServiceTemplate("tpl-figma", "Figma Professional", "设计", 0.0, BillingCycle.MONTHLY, "F", "#A259FF", "", "https://www.figma.com/", "", "设计协作", true),
    )
    return AppUiState(
        session = Session("debug-showcase", user),
        subscriptions = subscriptions,
        templates = templates,
        currencyRates = CurrencyRates(
            rates = mapOf("CNY" to 1.0),
            currencies = listOf(CurrencyInfo("CNY", "人民币", "¥")),
            source = "debug-showcase",
            updatedAt = Instant.now().toString(),
            cached = false,
        ),
        cloudUpdatedAt = Instant.now().toString(),
        lastSyncedAt = Instant.now().toString(),
        destination = destination,
    )
}
