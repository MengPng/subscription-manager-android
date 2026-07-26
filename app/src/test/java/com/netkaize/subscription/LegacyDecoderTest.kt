package com.netkaize.subscription

import com.netkaize.subscription.data.JsonCodec
import com.netkaize.subscription.data.Subscription
import com.netkaize.subscription.data.decodeLegacySubscriptions
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LegacyDecoderTest {
    @Test
    fun duplicateRepairSkipsIdsAlreadyPresentLaterInTheSnapshot() {
        val values = JSONArray()
            .put(JsonCodec.subscriptionToJson(Subscription(id = "x", name = "第一项")))
            .put(JsonCodec.subscriptionToJson(Subscription(id = "x-legacy-2", name = "已有后缀")))
            .put(JsonCodec.subscriptionToJson(Subscription(id = "x", name = "重复项")))

        val decoded = decodeLegacySubscriptions(values, "test")

        assertEquals(3, decoded.subscriptions.map { it.id }.toSet().size)
        assertEquals("x-legacy-3", decoded.subscriptions.last().id)
        assertTrue(decoded.errors.any { it.contains("重复 ID") })
    }

    @Test
    fun nonObjectRowIsReportedAsUnresolvedInsteadOfSilentlyDiscarded() {
        val decoded = decodeLegacySubscriptions(JSONArray().put("broken-row"), "test")

        assertTrue(decoded.subscriptions.isEmpty())
        assertEquals(1, decoded.unresolvedErrors.size)
    }

    @Test
    fun tolerantLegacyRepairIsStableAcrossRepeatedCaptures() {
        val item = JSONObject()
            .put("name", "缺少 ID 的旧服务")
            .put("price", 10)
            .put("cycle", "monthly")
            .put("createdAt", "2024-05-06T12:00:00.000Z")
            .put("status", "active")
        val first = decodeLegacySubscriptions(JSONArray().put(item), "test")
        val second = decodeLegacySubscriptions(JSONArray().put(JSONObject(item.toString())), "test")

        assertEquals(first.subscriptions, second.subscriptions)
        assertEquals(
            JsonCodec.subscriptionsSha256(first.subscriptions),
            JsonCodec.subscriptionsSha256(second.subscriptions),
        )
        assertEquals(java.time.LocalDate.of(2024, 5, 6), first.subscriptions.single().startDate)
    }

    @Test
    fun missingAllReliableDatesIsIsolatedInsteadOfUsingCurrentDate() {
        val item = JSONObject()
            .put("id", "missing-date")
            .put("name", "日期缺失")
            .put("price", 10)
            .put("cycle", "monthly")
            .put("status", "active")

        val decoded = decodeLegacySubscriptions(JSONArray().put(item), "test")

        assertTrue(decoded.subscriptions.isEmpty())
        assertEquals(1, decoded.unresolvedErrors.size)
    }
}
