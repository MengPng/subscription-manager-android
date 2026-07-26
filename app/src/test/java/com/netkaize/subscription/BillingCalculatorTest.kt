package com.netkaize.subscription

import com.netkaize.subscription.data.BillingCycle
import com.netkaize.subscription.data.PausePeriod
import com.netkaize.subscription.data.Subscription
import com.netkaize.subscription.data.SubscriptionStatus
import com.netkaize.subscription.domain.BillingCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class BillingCalculatorTest {
    @Test
    fun monthlyUsesNaturalCalendarAndSupportsCustomRenewalAnchor() {
        val subscription = Subscription(
            name = "服务",
            priceCny = 100.0,
            cycle = BillingCycle.MONTHLY,
            startDate = LocalDate.of(2026, 1, 31),
            nextDate = LocalDate.of(2026, 2, 28),
            renewalAnchorDate = LocalDate.of(2026, 1, 31),
        )
        assertEquals(LocalDate.of(2026, 2, 28), BillingCalculator.nextOccurrence(subscription, LocalDate.of(2026, 2, 1)))
        assertEquals(100.0, BillingCalculator.monthSpend(listOf(subscription), YearMonth.of(2026, 2)), 0.001)
    }

    @Test
    fun pausedPeriodsAreExcludedFromChargesAndBillableDays() {
        val subscription = Subscription(
            name = "服务",
            priceCny = 30.0,
            cycle = BillingCycle.MONTHLY,
            startDate = LocalDate.of(2026, 1, 1),
            nextDate = LocalDate.of(2026, 2, 1),
            renewalAnchorDate = LocalDate.of(2026, 1, 1),
            pauses = listOf(PausePeriod(LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28))),
        )
        assertEquals(0.0, BillingCalculator.monthSpend(listOf(subscription), YearMonth.of(2026, 2)), 0.001)
        assertEquals(35L, BillingCalculator.subscriptionDays(subscription, LocalDate.of(2026, 3, 4)))
    }

    @Test
    fun scheduledCancellationStopsFutureRenewals() {
        val subscription = Subscription(
            name = "服务",
            cycle = BillingCycle.YEARLY,
            startDate = LocalDate.of(2025, 1, 1),
            nextDate = LocalDate.of(2026, 1, 1),
            renewalAnchorDate = LocalDate.of(2025, 1, 1),
            scheduledCancelDate = LocalDate.of(2026, 1, 1),
        )
        assertNull(BillingCalculator.nextOccurrence(subscription, LocalDate.of(2025, 7, 1)))
    }

    @Test
    fun customNextDateBecomesTheRenewalAnchor() {
        val subscription = Subscription(
            name = "自定义账期",
            priceCny = 88.0,
            cycle = BillingCycle.MONTHLY,
            startDate = LocalDate.of(2026, 1, 10),
            nextDate = LocalDate.of(2026, 2, 20),
            renewalAnchorDate = LocalDate.of(2026, 2, 20),
        )
        assertEquals(LocalDate.of(2026, 2, 20), BillingCalculator.nextOccurrence(subscription, LocalDate.of(2026, 2, 1)))
        assertEquals(LocalDate.of(2026, 3, 20), BillingCalculator.nextOccurrence(subscription, LocalDate.of(2026, 2, 21)))
    }

    @Test
    fun yearlyUsesTwelveNaturalMonthsAcrossLeapDay() {
        val subscription = Subscription(
            name = "年付",
            cycle = BillingCycle.YEARLY,
            startDate = LocalDate.of(2024, 2, 29),
            nextDate = LocalDate.of(2025, 2, 28),
            renewalAnchorDate = LocalDate.of(2024, 2, 29),
        )
        assertEquals(LocalDate.of(2028, 2, 29), BillingCalculator.nextOccurrence(subscription, LocalDate.of(2027, 3, 1)))
    }

    @Test
    fun onceIsChargedExactlyOnceAndCancellationDateIsExclusive() {
        val once = Subscription(
            name = "买断",
            priceCny = 299.0,
            cycle = BillingCycle.ONCE,
            startDate = LocalDate.of(2026, 3, 5),
            nextDate = null,
            renewalAnchorDate = null,
        )
        assertEquals(299.0, BillingCalculator.lifetimeSpent(listOf(once), LocalDate.of(2030, 1, 1)), 0.001)

        val monthly = Subscription(
            name = "到期取消",
            priceCny = 100.0,
            cycle = BillingCycle.MONTHLY,
            startDate = LocalDate.of(2026, 1, 1),
            nextDate = LocalDate.of(2026, 2, 1),
            renewalAnchorDate = LocalDate.of(2026, 1, 1),
            scheduledCancelDate = LocalDate.of(2026, 3, 1),
        )
        assertEquals(200.0, BillingCalculator.lifetimeSpent(listOf(monthly), LocalDate.of(2026, 12, 31)), 0.001)
    }

    @Test
    fun nextMonthBudgetUsesActualChargeDatesWhileAnalysisBudgetProrates() {
        val monthly = Subscription(
            name = "月付",
            priceCny = 310.0,
            cycle = BillingCycle.MONTHLY,
            startDate = LocalDate.of(2026, 1, 16),
            nextDate = LocalDate.of(2026, 2, 16),
            renewalAnchorDate = LocalDate.of(2026, 1, 16),
        )
        assertEquals(310.0, BillingCalculator.nextMonthBudget(listOf(monthly), LocalDate.of(2026, 1, 20)), 0.001)
        val januaryBudget = BillingCalculator.budgetMonthSpend(listOf(monthly), YearMonth.of(2026, 1))
        assertTrue(januaryBudget in 159.9..160.1)
    }

    @Test
    fun futureFirstChargeIncludingOneTimePurchaseAppearsAsNextOccurrence() {
        val future = LocalDate.of(2026, 9, 1)
        val once = Subscription(
            name = "未来买断",
            cycle = BillingCycle.ONCE,
            startDate = future,
            nextDate = null,
        )
        val monthly = Subscription(
            name = "未来月付",
            cycle = BillingCycle.MONTHLY,
            startDate = future,
            nextDate = future.plusMonths(1),
        )

        assertEquals(future, BillingCalculator.nextOccurrence(once, LocalDate.of(2026, 8, 1)))
        assertEquals(future, BillingCalculator.nextOccurrence(monthly, LocalDate.of(2026, 8, 1)))
    }

    @Test
    fun pausedStatusWithoutLegacyIntervalStopsAtNextRenewal() {
        val paused = Subscription(
            name = "旧版暂停记录",
            priceCny = 100.0,
            cycle = BillingCycle.MONTHLY,
            startDate = LocalDate.of(2026, 1, 1),
            nextDate = LocalDate.of(2026, 2, 1),
            renewalAnchorDate = LocalDate.of(2026, 1, 1),
            status = SubscriptionStatus.PAUSED,
            pauses = emptyList(),
        )

        assertEquals(100.0, BillingCalculator.lifetimeSpent(listOf(paused), LocalDate.of(2026, 6, 1)), 0.001)
        assertNull(BillingCalculator.nextOccurrence(paused, LocalDate.of(2026, 1, 20)))
    }

    @Test
    fun manualCancellationOnChargeDateDoesNotEraseThatDaysCharge() {
        val once = Subscription(
            name = "当日取消的买断服务",
            priceCny = 299.0,
            cycle = BillingCycle.ONCE,
            startDate = LocalDate.of(2026, 3, 5),
            status = SubscriptionStatus.CANCELED,
            canceledAt = LocalDate.of(2026, 3, 5),
        )

        assertEquals(299.0, BillingCalculator.lifetimeSpent(listOf(once), LocalDate.of(2026, 3, 5)), 0.001)
        assertEquals(1L, BillingCalculator.subscriptionDays(once, LocalDate.of(2026, 3, 5)))
    }
}
