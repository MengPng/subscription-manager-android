package com.netkaize.subscription

import com.netkaize.subscription.data.Subscription
import com.netkaize.subscription.data.mergeLegacySubscriptions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MigrationPolicyTest {
    @Test
    fun differentNativeAndLegacyRowsAreBothRetained() {
        val native = Subscription(id = "native", name = "本机订阅")
        val legacy = Subscription(id = "legacy", name = "旧版订阅")

        val merged = mergeLegacySubscriptions(listOf(native), listOf(legacy))

        assertEquals(2, merged.size)
        assertTrue(merged.any { it.name == "本机订阅" })
        assertTrue(merged.any { it.name == "旧版订阅" })
    }

    @Test
    fun identicalDetailsWithDifferentIdsRemainTwoDistinctSubscriptions() {
        val native = Subscription(id = "seat-a", name = "同一服务")
        val legacy = Subscription(id = "seat-b", name = "同一服务")

        val merged = mergeLegacySubscriptions(listOf(native), listOf(legacy))

        assertEquals(listOf("seat-a", "seat-b"), merged.map { it.id })
    }

    @Test
    fun collidingIdsAreRenamedDeterministicallyAndRetryDoesNotDuplicate() {
        val native = Subscription(id = "same", name = "本机订阅")
        val legacy = Subscription(id = "same", name = "旧版订阅")

        val first = mergeLegacySubscriptions(listOf(native), listOf(legacy))
        val retried = mergeLegacySubscriptions(first, listOf(legacy))

        assertEquals(first, retried)
        assertEquals(2, retried.size)
        assertTrue(retried.single { it.name == "旧版订阅" }.id.startsWith("same-legacy-"))
    }

    @Test
    fun authoritativeMergedCandidateAlreadyContainsLegacyOnCrashRetry() {
        val native = Subscription(id = "same", name = "本机订阅")
        val legacy = Subscription(id = "same", name = "旧版订阅")
        val authoritative = mergeLegacySubscriptions(listOf(native), listOf(legacy))

        assertEquals(authoritative, mergeLegacySubscriptions(authoritative, listOf(legacy)))
    }
}
