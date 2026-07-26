package com.netkaize.subscription.domain

import com.netkaize.subscription.data.BillingCycle
import com.netkaize.subscription.data.Subscription
import com.netkaize.subscription.data.SubscriptionStatus
import java.time.LocalDate
import java.time.Year
import java.time.YearMonth
import java.time.temporal.ChronoUnit

object BillingCalculator {
    data class ChargeOccurrence(val subscription: Subscription, val date: LocalDate)

    fun effectiveStatus(subscription: Subscription, on: LocalDate = LocalDate.now()): SubscriptionStatus {
        val end = endDate(subscription)
        return if (end != null && !on.isBefore(end)) SubscriptionStatus.CANCELED else subscription.status
    }

    fun nextOccurrence(subscription: Subscription, from: LocalDate = LocalDate.now()): LocalDate? {
        if (effectiveStatus(subscription, from) != SubscriptionStatus.ACTIVE) return null
        if (!subscription.startDate.isBefore(from) && chargeAllowed(subscription, subscription.startDate)) {
            return subscription.startDate
        }
        if (subscription.cycle == BillingCycle.ONCE) return null
        val end = endDate(subscription)
        var occurrence = renewalOccurrence(subscription, 0)
        var index = 0
        while (index < 5_000) {
            if (end != null && !occurrence.isBefore(end)) return null
            if (!occurrence.isBefore(from) && chargeAllowed(subscription, occurrence)) return occurrence
            index += 1
            occurrence = renewalOccurrence(subscription, index)
        }
        return null
    }

    fun lifetimeSpent(subscriptions: List<Subscription>, on: LocalDate = LocalDate.now()): Double =
        subscriptions.sumOf { chargeDatesUntil(it, on).size * it.priceCny }

    fun annualSpend(subscriptions: List<Subscription>, year: Int = LocalDate.now().year): Double {
        val start = LocalDate.of(year, 1, 1)
        val end = LocalDate.of(year, 12, 31)
        return subscriptions.sumOf { chargeDatesInRange(it, start, end).size * it.priceCny }
    }

    fun monthSpend(subscriptions: List<Subscription>, month: YearMonth): Double = subscriptions.sumOf {
        chargeDatesInRange(it, month.atDay(1), month.atEndOfMonth()).size * it.priceCny
    }

    fun nextMonthBudget(subscriptions: List<Subscription>, now: LocalDate = LocalDate.now()): Double =
        monthSpend(subscriptions, YearMonth.from(now).plusMonths(1))

    fun subscriptionDays(subscription: Subscription, on: LocalDate = LocalDate.now()): Long {
        return billableDaysInRange(subscription, subscription.startDate, on)
    }

    fun billableDaysInRange(subscription: Subscription, from: LocalDate, through: LocalDate): Long {
        val effectiveStart = maxOf(from, subscription.startDate)
        val effectiveEnd = lastBillableDate(subscription)?.coerceAtMost(through) ?: through
        if (effectiveEnd.isBefore(effectiveStart)) return 0
        var cursor = effectiveStart
        var count = 0L
        while (!cursor.isAfter(effectiveEnd)) {
            if (!isPausedOn(subscription, cursor)) count += 1
            cursor = cursor.plusDays(1)
        }
        return count
    }

    fun monthlySeries(subscriptions: List<Subscription>, year: Int): List<Double> = (1..12).map { month ->
        monthSpend(subscriptions, YearMonth.of(year, month))
    }

    fun monthlyBudgetSeries(subscriptions: List<Subscription>, year: Int): List<Double> = (1..12).map { month ->
        budgetMonthSpend(subscriptions, YearMonth.of(year, month))
    }

    fun budgetMonthSpend(subscriptions: List<Subscription>, month: YearMonth): Double = subscriptions.sumOf { subscription ->
        if (subscription.cycle == BillingCycle.ONCE) {
            monthSpend(listOf(subscription), month)
        } else {
            val days = billableDaysInRange(subscription, month.atDay(1), month.atEndOfMonth())
            val divisor = when (subscription.cycle) {
                BillingCycle.MONTHLY -> month.lengthOfMonth()
                BillingCycle.YEARLY -> Year.of(month.year).length()
                BillingCycle.ONCE -> 1
            }
            days * subscription.priceCny / divisor
        }
    }

    fun chargeSchedule(
        subscriptions: List<Subscription>,
        from: LocalDate,
        through: LocalDate,
    ): List<ChargeOccurrence> = subscriptions
        .flatMap { subscription ->
            chargeDatesInRange(subscription, from, through).map { ChargeOccurrence(subscription, it) }
        }
        .sortedWith(compareBy<ChargeOccurrence> { it.date }.thenBy { it.subscription.name })

    fun annualizedPrice(subscription: Subscription): Double = when (subscription.cycle) {
        BillingCycle.MONTHLY -> subscription.priceCny * 12
        BillingCycle.YEARLY, BillingCycle.ONCE -> subscription.priceCny
    }

    fun activeSubscriptions(subscriptions: List<Subscription>, on: LocalDate = LocalDate.now()): List<Subscription> =
        subscriptions.filter { effectiveStatus(it, on) == SubscriptionStatus.ACTIVE }

    fun dueWithin(subscriptions: List<Subscription>, days: Long, from: LocalDate = LocalDate.now()): List<Pair<Subscription, LocalDate>> =
        activeSubscriptions(subscriptions, from)
            .mapNotNull { subscription -> nextOccurrence(subscription, from)?.let { subscription to it } }
            .filter { (_, date) -> !date.isAfter(from.plusDays(days)) }
            .sortedBy { it.second }

    private fun chargeDatesInRange(subscription: Subscription, from: LocalDate, through: LocalDate): List<LocalDate> =
        chargeDatesUntil(subscription, through).filter { !it.isBefore(from) }

    private fun chargeDatesUntil(subscription: Subscription, through: LocalDate): List<LocalDate> {
        if (subscription.startDate.isAfter(through)) return emptyList()
        if (subscription.cycle == BillingCycle.ONCE) {
            return listOf(subscription.startDate).filter { chargeAllowed(subscription, it) }
        }
        val dates = mutableListOf(subscription.startDate)
        var index = 0
        var occurrence = renewalOccurrence(subscription, index)
        while (!occurrence.isAfter(through) && index < 5_000) {
            if (occurrence.isAfter(subscription.startDate)) dates += occurrence
            index += 1
            occurrence = renewalOccurrence(subscription, index)
        }
        return dates.filter { chargeAllowed(subscription, it) }
    }

    private fun renewalOccurrence(subscription: Subscription, index: Int): LocalDate {
        val defaultNext = addPeriod(subscription.startDate, subscription.cycle, 1)
        val anchor = subscription.renewalAnchorDate
            ?: if (subscription.nextDate == defaultNext) subscription.startDate else subscription.nextDate
            ?: subscription.startDate
        val firstStep = if (anchor == subscription.startDate) 1 else 0
        return addPeriod(anchor, subscription.cycle, firstStep + index)
    }

    private fun addPeriod(date: LocalDate, cycle: BillingCycle, count: Int): LocalDate = when (cycle) {
        BillingCycle.MONTHLY -> date.plusMonths(count.toLong())
        BillingCycle.YEARLY -> date.plusYears(count.toLong())
        BillingCycle.ONCE -> date.plusDays(count.toLong())
    }

    private fun endDate(subscription: Subscription): LocalDate? = listOfNotNull(
        subscription.scheduledCancelDate,
        subscription.canceledAt.takeIf { subscription.status == SubscriptionStatus.CANCELED },
    ).minOrNull()

    private fun lastBillableDate(subscription: Subscription): LocalDate? = listOfNotNull(
        subscription.scheduledCancelDate?.minusDays(1),
        subscription.canceledAt.takeIf { subscription.status == SubscriptionStatus.CANCELED },
    ).minOrNull()

    private fun chargeAllowed(subscription: Subscription, date: LocalDate): Boolean {
        val beforeScheduledCancellation = subscription.scheduledCancelDate?.let(date::isBefore) ?: true
        // A manual cancellation records when access ended, not a refund. A charge on that same
        // day remains part of the estimate; scheduled cancellation is exclusive at its boundary.
        val notAfterManualCancellation = subscription.canceledAt
            ?.takeIf { subscription.status == SubscriptionStatus.CANCELED }
            ?.let { !date.isAfter(it) }
            ?: true
        return beforeScheduledCancellation && notAfterManualCancellation && !isPausedOn(subscription, date)
    }

    private fun isPausedOn(subscription: Subscription, date: LocalDate): Boolean {
        if (subscription.pauses.any { period ->
                !date.isBefore(period.startDate) && (period.endDate == null || !date.isAfter(period.endDate))
            }) return true
        if (subscription.status != SubscriptionStatus.PAUSED || subscription.pauses.any { it.endDate == null }) return false
        // Defensive fallback for old/malformed rows that carried a paused status without an open
        // interval. The next renewal is the least destructive deterministic boundary: the paid
        // current period remains, while later estimates stop.
        val inferredStart = subscription.lastReviewedAt ?: subscription.nextDate ?: subscription.startDate
        return !date.isBefore(inferredStart)
    }

    private fun LocalDate.coerceAtMost(other: LocalDate): LocalDate = if (isAfter(other)) other else this
}
